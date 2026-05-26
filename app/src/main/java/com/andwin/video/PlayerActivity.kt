package com.andwin.video

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.andwin.video.databinding.ActivityPlayerBinding
import com.andwin.video.player.StreamPlayer
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var streamPlayer: StreamPlayer
    private var autoPlay: Boolean = false
    private val FILE_PICKER_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializePlayer()
        setupListeners()
        
        val videoPath = intent.getStringExtra("video_path")
        autoPlay = intent.getBooleanExtra("auto_play", false)
        
        if (videoPath != null && File(videoPath).exists()) {
            binding.etUrl.setText(videoPath)
            if (autoPlay) {
                playLocalVideo(videoPath)
            }
        }
    }

    private fun initializePlayer() {
        streamPlayer = StreamPlayer(this)
        streamPlayer.initialize(binding.playerView)
    }

    private fun setupListeners() {
        binding.btnGo.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                if (File(url).exists()) {
                    playLocalVideo(url)
                } else {
                    playStream(url)
                }
            } else {
                Toast.makeText(this, "请输入视频地址", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPlay.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                if (File(url).exists()) {
                    playLocalVideo(url)
                } else {
                    playStream(url)
                }
            } else {
                Toast.makeText(this, "请输入视频地址", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPause.setOnClickListener {
            streamPlayer.pause()
        }

        binding.btnStop.setOnClickListener {
            streamPlayer.stop()
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "已停止\n\n支持: RTMP / RTSP / HLS / DASH / 本地文件"
        }

        binding.btnBackToList.setOnClickListener {
            streamPlayer.release()
            finish()
        }
    }

    fun onChipClick(view: View) {
        when (view.id) {
            R.id.chipRtmp -> {
                binding.etUrl.setText("rtmp://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample("RTMP 推流地址格式:\nrtmp://服务器地址/应用名/流名\n\n示例: rtmp://live.example.com/live/stream1")
            }
            R.id.chipRtsp -> {
                binding.etUrl.setText("rtsp://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample("RTSP 摄像头/监控地址格式:\nrtsp://IP地址:端口/路径\n\n示例: rtsp://192.168.1.100:554/live/ch1\n示例: rtsp://admin:password@192.168.1.100:554/h264/ch1/main/av_stream")
            }
            R.id.chipHls -> {
                binding.etUrl.setText("http://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample("HLS 直播流地址格式:\nhttp://服务器地址/路径.m3u8\n\n示例: http://live.example.com/live/stream.m3u8\n示例: https://devstreaming-cdn.apple.com/videos/big_buck_bunny/master.m3u8")
            }
            R.id.chipDash -> {
                binding.etUrl.setText("http://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample("DASH 自适应码率流地址:\nhttp://服务器地址/路径.mpd\n\n示例: http://example.com/dash/stream.mpd")
            }
            R.id.chipLocal -> {
                pickLocalFile()
            }
        }
    }

    private fun pickLocalFile() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, FILE_PICKER_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val fileName = getFileName(uri)
                binding.etUrl.setText(uri.toString())
                Toast.makeText(this, "已选择: $fileName", Toast.LENGTH_SHORT).show()
                
                if (autoPlay || true) {
                    playStream(uri.toString())
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "未知文件"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun showExample(example: String) {
        binding.tvStatus.text = example
        binding.tvStatus.visibility = View.VISIBLE
        Toast.makeText(this, "已填入协议前缀，请补充完整地址", Toast.LENGTH_LONG).show()
    }

    private fun playLocalVideo(path: String) {
        try {
            val fileUri = Uri.fromFile(File(path))
            streamPlayer.playStream(fileUri.toString())
            binding.tvStatus.visibility = View.GONE
            Toast.makeText(this, "播放: ${File(path).name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_LONG).show()
            binding.tvStatus.text = "播放失败"
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun playStream(url: String) {
        try {
            binding.tvStatus.visibility = View.GONE
            streamPlayer.playStream(url)
            
            val protocol = when {
                url.startsWith("rtmp://") -> "RTMP"
                url.startsWith("rtsp://") -> "RTSP"
                url.contains(".m3u8") -> "HLS"
                url.contains(".mpd") -> "DASH"
                else -> "网络流"
            }
            Toast.makeText(this, "正在连接 $protocol 流...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_LONG).show()
            binding.tvStatus.text = "连接失败\n${e.message}"
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        streamPlayer.release()
    }
}
