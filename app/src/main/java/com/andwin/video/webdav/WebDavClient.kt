package com.andwin.video.webdav

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端 - 基于 OkHttp 实现 WebDAV 协议
 *
 * 支持: PROPFIND / PUT / GET / DELETE / MKCOL
 */
class WebDavClient(private val context: Context) {

    companion object {
        private const val TAG = "WebDavClient"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val UPLOAD_TIMEOUT_SECONDS = 120L

        // SharedPreferences keys
        const val PREF_WEBDAV_SERVER_URL = "webdav_server_url"
        const val PREF_WEBDAV_USERNAME = "webdav_username"
        const val PREF_WEBDAV_PASSWORD = "webdav_password"
        const val PREF_WEBDAV_REMOTE_PATH = "webdav_remote_path"
        const val PREF_WEBDAV_AUTO_UPLOAD = "webdav_auto_upload"

        // Default remote path
        const val DEFAULT_REMOTE_PATH = "/VideoMonitor"
    }

    data class WebDavFile(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: String,
        val isDirectory: Boolean,
        val contentType: String?
    )

    data class WebDavConfig(
        val serverUrl: String,
        val username: String,
        val password: String,
        val remotePath: String = DEFAULT_REMOTE_PATH
    )

    /**
     * 从 SharedPreferences 读取 WebDAV 配置
     */
    fun getConfig(): WebDavConfig? {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val serverUrl = prefs.getString(PREF_WEBDAV_SERVER_URL, "")?.trim() ?: ""
        if (serverUrl.isEmpty()) return null

        return WebDavConfig(
            serverUrl = serverUrl,
            username = prefs.getString(PREF_WEBDAV_USERNAME, "") ?: "",
            password = prefs.getString(PREF_WEBDAV_PASSWORD, "") ?: "",
            remotePath = prefs.getString(PREF_WEBDAV_REMOTE_PATH, DEFAULT_REMOTE_PATH) ?: DEFAULT_REMOTE_PATH
        )
    }

    /**
     * 构建 HTTP Basic Auth 头
     */
    private fun getAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
    }

    /**
     * 创建 OkHttpClient（带认证）
     */
    private fun createClient(config: WebDavConfig, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithAuth = originalRequest.newBuilder()
                    .header("Authorization", getAuthHeader(config.username, config.password))
                    .build()
                chain.proceed(requestWithAuth)
            }
            .build()
    }

    /**
     * 规范化服务器 URL（确保以 / 结尾）
     */
    private fun normalizeServerUrl(url: String): String {
        var normalized = url.trim().trimEnd('/')
        // 如果没有协议前缀，默认添加 https
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return "$normalized/"
    }

    /**
     * 测试连接是否可用
     */
    suspend fun testConnection(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = createClient(config)
            val baseUrl = normalizeServerUrl(config.serverUrl)
            val remotePath = config.remotePath.trimEnd('/') + "/"

            Log.i(TAG, "测试 WebDAV 连接: $baseUrl$remotePath")

            // 先尝试 PROPFIND 根路径
            val request = Request.Builder()
                .url("$baseUrl$remotePath")
                .method("PROPFIND", RequestBody.create(null, ""))
                .header("Depth", "0")
                .header("Content-Type", "application/xml")
                .build()

            val response = client.newCall(request).execute()

            when (response.code) {
                207 -> {
                    // Multi-status，说明连接成功且路径存在
                    Log.i(TAG, "WebDAV 连接成功")
                    Result.success("连接成功")
                }
                404 -> {
                    // 路径不存在，尝试创建
                    Log.i(TAG, "远程目录不存在，尝试创建...")
                    createDirectory(client, baseUrl, config.remotePath)
                    Result.success("连接成功（已自动创建远程目录）")
                }
                401 -> Result.failure(Exception("认证失败：用户名或密码错误"))
                else -> {
                    val body = response.body?.string() ?: ""
                    Log.w(TAG, "WebDAV 连接返回 ${response.code}: $body")
                    Result.failure(Exception("连接失败 (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV 连接测试异常", e)
            Result.failure(e)
        }
    }

    /**
     * 上传文件到 WebDAV 服务器
     */
    suspend fun uploadFile(
        localFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig()
            ?: return@withContext Result.failure(Exception("未配置 WebDAV 服务器"))

        try {
            val client = createClient(config, UPLOAD_TIMEOUT_SECONDS)
            val baseUrl = normalizeServerUrl(config.serverUrl)
            val remoteDir = config.remotePath.trimEnd('/')
            val remoteFilePath = "$remoteDir/${localFile.name}"

            Log.i(TAG, "开始上传文件: ${localFile.name} -> $remoteFilePath")
            Log.i(TAG, "文件大小: ${localFile.length()} bytes")

            val mediaType = "application/octet-stream".toMediaType()
            val fileBody = RequestBody.create(mediaType, localFile)

            val request = Request.Builder()
                .url("$baseUrl${remoteFilePath}")
                .put(fileBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful || response.code in 201..204) {
                Log.i(TAG, "✅ 文件上传成功: ${localFile.name}")
                Result.success(remoteFilePath)
            } else {
                val body = response.body?.string() ?: ""
                Log.e(TAG, "❌ 文件上传失败 (HTTP ${response.code}): $body")
                Result.failure(Exception("上传失败 (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 文件上传异常", e)
            Result.failure(e)
        }
    }

    /**
     * 列出远程目录中的文件
     */
    suspend fun listFiles(): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        val config = getConfig()
            ?: return@withContext Result.failure(Exception("未配置 WebDAV 服务器"))

        try {
            val client = createClient(config)
            val baseUrl = normalizeServerUrl(config.serverUrl)
            val remotePath = config.remotePath.trimEnd('/') + "/"

            val propFindBody = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:displayname/>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:resourcetype/>
                    <d:getcontenttype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            val xmlMediaType = "application/xml; charset=utf-8".toMediaType()

            val request = Request.Builder()
                .url("$baseUrl$remotePath")
                .method("PROPFIND", RequestBody.create(xmlMediaType, propFindBody))
                .header("Depth", "1")
                .build()

            val response = client.newCall(request).execute()

            if (response.code == 207) {
                val body = response.body?.string() ?: ""
                val files = parseMultiStatus(body, remotePath)
                Log.i(TAG, "列出远程文件: ${files.size} 个")
                Result.success(files)
            } else {
                Log.w(TAG, "列出文件失败 (HTTP ${response.code})")
                Result.failure(Exception("列出文件失败 (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "列出文件异常", e)
            Result.failure(e)
        }
    }

    /**
     * 删除远程文件
     */
    suspend fun deleteRemoteFile(fileName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = getConfig()
            ?: return@withContext Result.failure(Exception("未配置 WebDAV 服务器"))

        try {
            val client = createClient(config)
            val baseUrl = normalizeServerUrl(config.serverUrl)
            val remoteDir = config.remotePath.trimEnd('/')
            val remoteFilePath = "$remoteDir/$fileName"

            val request = Request.Builder()
                .url("$baseUrl$remoteFilePath")
                .delete()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful || response.code == 204) {
                Log.i(TAG, "远程文件已删除: $fileName")
                Result.success(true)
            } else {
                Result.failure(Exception("删除失败 (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除远程文件异常", e)
            Result.failure(e)
        }
    }

    /**
     * 批量上传所有本地录制文件
     */
    suspend fun uploadAllRecordings(
        localFiles: List<File>,
        onProgress: ((String, Int, Int) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val config = getConfig()
            ?: return@withContext Result.failure(Exception("未配置 WebDAV 服务器"))

        try {
            // 确保远程目录存在
            val client = createClient(config)
            val baseUrl = normalizeServerUrl(config.serverUrl)
            createDirectory(client, baseUrl, config.remotePath)

            val uploadedPaths = mutableListOf<String>()
            var completedCount = 0

            for (file in localFiles) {
                onProgress?.invoke(file.name, completedCount, localFiles.size)

                val result = uploadFile(file)
                if (result.isSuccess) {
                    uploadedPaths.add(result.getOrDefault(""))
                } else {
                    Log.w(TAG, "跳过上传失败的文件: ${file.name}")
                }
                completedCount++
                onProgress?.invoke(file.name, completedCount, localFiles.size)
            }

            Log.i(TAG, "批量上传完成: ${uploadedPaths.size}/${localFiles.size} 个文件")
            Result.success(uploadedPaths)
        } catch (e: Exception) {
            Log.e(TAG, "批量上传异常", e)
            Result.failure(e)
        }
    }

    /**
     * 解析 WebDAV Multi-Status XML 响应
     */
    private fun parseMultiStatus(xml: String, basePath: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var currentName = ""
            var currentSize: Long = 0
            var currentModified = ""
            var isDirectory = false
            var currentContentType: String? = null
            var href = ""
            var inResponse = false
            var inProp = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name.lowercase()
                        when {
                            tagName == "d:response" || tagName == "response" -> {
                                inResponse = true
                                currentName = ""
                                currentSize = 0
                                currentModified = ""
                                isDirectory = false
                                currentContentType = null
                                href = ""
                            }
                            tagName == "d:href" || tagName == "href" -> {
                                if (inResponse) {
                                    href = nextText(parser).trimEnd('/')
                                }
                            }
                            tagName == "d:prop" || tagName == "prop" -> {
                                inProp = true
                            }
                            tagName == "d:displayname" || tagName == "displayname" -> {
                                if (inProp) currentName = nextText(parser)
                            }
                            tagName == "d:getcontentlength" || tagName == "getcontentlength" -> {
                                if (inProp) currentSize = nextText(parser).toLongOrNull() ?: 0
                            }
                            tagName == "d:getlastmodified" || tagName == "getlastmodified" -> {
                                if (inProp) currentModified = nextText(parser)
                            }
                            tagName == "d:resourcetype" || tagName == "resourcetype" -> {
                                // 检查是否有子元素 <d:collection/>
                            }
                            tagName == "d:collection" || tagName == "collection" -> {
                                if (inProp) isDirectory = true
                            }
                            tagName == "d:getcontenttype" || tagName == "getcontenttype" -> {
                                if (inProp) currentContentType = nextText(parser)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name.lowercase()
                        if (tagName == "d:response" || tagName == "response") {
                            inResponse = false
                            inProp = false
                            // 跳过根目录自身
                            if (href.isNotEmpty() && href != basePath) {
                                val fileName = href.substringAfterLast('/').ifEmpty {
                                    href.substringAfterLast('/').trimEnd('/')
                                }.let {
                                    if (it.isBlank()) href.substringAfterLast('/') else it
                                }
                                if (fileName.isNotBlank()) {
                                    files.add(WebDavFile(
                                        name = fileName.ifBlank { currentName.ifBlank { href.substringAfterLast('/') } },
                                        path = href,
                                        size = currentSize,
                                        lastModified = currentModified,
                                        isDirectory = isDirectory,
                                        contentType = currentContentType
                                    ))
                                }
                            }
                        }
                        if (tagName == "d:prop" || tagName == "prop") {
                            inProp = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 WebDAV 响应异常", e)
        }
        return files
    }

    private fun nextText(parser: XmlPullParser): String {
        var text = ""
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.TEXT -> text += parser.text
                XmlPullParser.END_TAG -> break
            }
            eventType = parser.next()
        }
        return text.trim()
    }

    /**
     * 创建远程目录（MKCOL）
     */
    private suspend fun createDirectory(client: OkHttpClient, baseUrl: String, path: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val parts = path.trim('/').split("/").filter { it.isNotEmpty() }
                var currentPath = ""

                for (part in parts) {
                    currentPath += "/$part"
                    val request = Request.Builder()
                        .url("$baseUrl$currentPath/")
                        .method("MKCOL", null)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.code !in listOf(201, 301, 302, 405)) {
                        // 405 = Method Not Allowed（目录已存在），视为成功
                        if (response.code != 405) {
                            Log.w(TAG, "创建目录 $currentPath 返回: ${response.code}")
                        }
                    }
                }
                Log.i(TAG, "远程目录已就绪: $path")
                true
            } catch (e: Exception) {
                Log.e(TAG, "创建远程目录异常", e)
                false
            }
        }
    }
}
