package com.andwin.video

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andwin.video.databinding.ActivityRecordingsBinding
import com.andwin.video.recorder.VideoRecorder
import com.andwin.video.utils.LocaleHelper
import com.andwin.video.webdav.WebDavClient
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var videoRecorder: VideoRecorder
    private lateinit var webDavClient: WebDavClient
    private lateinit var adapter: RecordingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoRecorder = VideoRecorder(this)
        webDavClient = WebDavClient(this)

        setupToolbar()
        setupRecyclerView()
        loadRecordings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.recordings_title)
    }

    private fun setupRecyclerView() {
        adapter = RecordingAdapter(
            onItemClick = { file ->
                playVideo(file)
            },
            onDeleteClick = { file, position ->
                confirmDelete(file, position)
            },
            onUploadClick = { file ->
                uploadSingleFile(file)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            adapter = this@RecordingsActivity.adapter
        }
    }

    private fun loadRecordings() {
        val files = videoRecorder.getRecordingFiles()
        
        if (files.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.tvCount.text = getString(R.string.no_recordings)
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            
            val totalSize = formatFileSize(videoRecorder.getTotalSize())
            binding.tvCount.text = getString(R.string.video_count_and_size, files.size, totalSize)
            
            adapter.submitList(files)
        }
    }

    private fun playVideo(file: File) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("video_path", file.absolutePath)
            putExtra("auto_play", true)
        }
        startActivity(intent)
    }

    private fun confirmDelete(file: File, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message, file.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (videoRecorder.deleteRecording(file)) {
                    adapter.notifyItemRemoved(position)
                    loadRecordings()
                    // 重新加载完整列表
                    val newList = videoRecorder.getRecordingFiles()
                    if (newList.isEmpty()) {
                        adapter.submitList(emptyList())
                        loadRecordings()
                    } else {
                        adapter.submitList(newList)
                    }
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.delete_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recordings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_upload_all -> {
                uploadAllToWebDAV()
                true
            }
            R.id.action_list_remote -> {
                listRemoteFiles()
                true
            }
            R.id.action_clear_all -> {
                confirmClearAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClearAll() {
        val count = videoRecorder.getRecordingFiles().size
        if (count == 0) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_all_confirm_title))
            .setMessage(getString(R.string.clear_all_confirm_message, count))
            .setPositiveButton(getString(R.string.clear_all)) { dialog: android.content.DialogInterface, which: Int ->
                val deleted = videoRecorder.clearAllRecordings()
                adapter.submitList(emptyList())
                loadRecordings()
                android.widget.Toast.makeText(this, getString(R.string.deleted_files, deleted), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ==================== WebDAV 操作 ====================

    /**
     * 全部上传到 WebDAV
     */
    private fun uploadAllToWebDAV() {
        val config = webDavClient.getConfig()
        if (config == null || config.serverUrl.isBlank()) {
            android.widget.Toast.makeText(this, getString(R.string.webdav_not_configured), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val files = videoRecorder.getRecordingFiles()
        if (files.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.webdav_no_files), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.webdav_upload_all))
            .setMessage(getString(R.string.webdav_upload_all_confirm, files.size))
            .setPositiveButton(getString(R.string.start_stream)) { _, _ ->
                performUploadAll(files)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 执行批量上传
     */
    private fun performUploadAll(files: List<File>) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.webdav_upload_title))
            .setMessage(getString(R.string.webdav_connecting))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            var successCount = 0
            var currentIndex = 0

            for (file in files) {
                currentIndex++
                runOnUiThread {
                    progressDialog.setMessage(
                        getString(R.string.webdav_uploading, file.name, currentIndex, files.size)
                    )
                }

                val result = webDavClient.uploadFile(file)
                if (result.isSuccess) successCount++
            }

            progressDialog.dismiss()

            runOnUiThread {
                android.widget.Toast.makeText(
                    this@RecordingsActivity,
                    getString(R.string.webdav_upload_complete, successCount, files.size),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 上传单个文件到 WebDAV
     */
    private fun uploadSingleFile(file: File) {
        val config = webDavClient.getConfig()
        if (config == null || config.serverUrl.isBlank()) {
            android.widget.Toast.makeText(this, getString(R.string.webdav_not_configured), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.webdav_upload_title))
            .setMessage(getString(R.string.webdav_upload_single_confirm, file.name))
            .setPositiveButton(getString(R.string.start_stream)) { _, _ ->
                val progressDialog = AlertDialog.Builder(this@RecordingsActivity)
                    .setTitle(getString(R.string.webdav_upload_title))
                    .setMessage(getString(R.string.webdav_uploading, file.name, 1, 1))
                    .setCancelable(false)
                    .show()

                lifecycleScope.launch {
                    val result = webDavClient.uploadFile(file,
                        onProgress = { written, total ->
                            val percent = if (total > 0) written.toDouble() / total.toDouble() * 100 else 0.0
                            runOnUiThread {
                                progressDialog.setMessage(
                                    getString(R.string.webdav_upload_progress,
                                        formatFileSize(written),
                                        formatFileSize(total),
                                        percent
                                    )
                                )
                            }
                        }
                    )

                    progressDialog.dismiss()

                    if (result.isSuccess) {
                        android.widget.Toast.makeText(
                            this@RecordingsActivity,
                            getString(R.string.webdav_upload_success, file.name),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        android.widget.Toast.makeText(
                            this@RecordingsActivity,
                            getString(R.string.webdav_upload_failed, error),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 查看远程文件列表
     */
    private fun listRemoteFiles() {
        val config = webDavClient.getConfig()
        if (config == null || config.serverUrl.isBlank()) {
            android.widget.Toast.makeText(this, getString(R.string.webdav_not_configured), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.webdav_list_remote))
            .setMessage(getString(R.string.webdav_connecting))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val result = webDavClient.listFiles()
            progressDialog.dismiss()

            if (result.isSuccess) {
                val remoteFiles = result.getOrDefault(emptyList())
                showRemoteFileList(remoteFiles)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                android.widget.Toast.makeText(
                    this@RecordingsActivity,
                    getString(R.string.webdav_connection_failed, error),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 显示远程文件列表对话框
     */
    private fun showRemoteFileList(files: List<WebDavClient.WebDavFile>) {
        if (files.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.webdav_remote_files))
                .setMessage(getString(R.string.webdav_no_remote_files))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }

        // 过滤掉目录，只显示文件
        val fileItems = files.filter { !it.isDirectory }
        val displayNames = fileItems.map { file ->
            "${file.name}\n${formatFileSize(file.size)} · ${file.lastModified}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.webdav_remote_files)} (${fileItems.size})")
            .setItems(displayNames) { _, which ->
                val selectedFile = fileItems[which]
                confirmDeleteRemote(selectedFile)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 确认删除远程文件
     */
    private fun confirmDeleteRemote(file: WebDavClient.WebDavFile) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.webdav_delete_remote_confirm, file.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteRemoteFile(file)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 删除远程文件
     */
    private fun deleteRemoteFile(file: WebDavClient.WebDavFile) {
        lifecycleScope.launch {
            val result = webDavClient.deleteRemoteFile(file.name)

            if (result.isSuccess) {
                android.widget.Toast.makeText(
                    this@RecordingsActivity,
                    getString(R.string.webdav_deleted_remote, file.name),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    this@RecordingsActivity,
                    getString(R.string.delete_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    inner class RecordingAdapter(
        private val onItemClick: (File) -> Unit,
        private val onDeleteClick: (File, Int) -> Unit,
        private val onUploadClick: (File) -> Unit = {}
    ) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

        private var items = listOf<File>()

        fun submitList(newItems: List<File>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
            val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
            val btnPlay: ImageView = itemView.findViewById(R.id.btnPlay)
            val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
            val btnUpload: ImageView = itemView.findViewById(R.id.btnUpload)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = items[position]

            holder.tvFileName.text = file.name
            holder.tvFileInfo.text = formatTime(file.lastModified())
            holder.tvFileSize.text = formatFileSize(file.length())

            // 设置缩略图（使用视频图标）
            holder.ivThumbnail.setImageResource(R.drawable.ic_video_file)

            holder.itemView.setOnClickListener { onItemClick(file) }
            holder.btnPlay.setOnClickListener { onItemClick(file) }
            holder.btnDelete.setOnClickListener { onDeleteClick(file, position) }
            holder.btnUpload.setOnClickListener { onUploadClick(file) }
        }

        override fun getItemCount(): Int = items.size
    }
}
