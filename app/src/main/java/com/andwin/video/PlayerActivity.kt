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
import com.andwin.video.utils.LocaleHelper
import java.io.File

class PlayerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }

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
                Toast.makeText(this, getString(R.string.enter_video_url_toast), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.enter_video_url_toast), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPause.setOnClickListener {
            streamPlayer.pause()
        }

        binding.btnStop.setOnClickListener {
            streamPlayer.stop()
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.player_stopped)
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
                showExample(getString(R.string.example_rtmp))
            }
            R.id.chipRtsp -> {
                binding.etUrl.setText("rtsp://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample(getString(R.string.example_rtsp))
            }
            R.id.chipHls -> {
                binding.etUrl.setText("http://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample(getString(R.string.example_hls))
            }
            R.id.chipDash -> {
                binding.etUrl.setText("http://")
                binding.etUrl.setSelection(binding.etUrl.text?.length ?: 0)
                showExample(getString(R.string.example_dash))
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
            Toast.makeText(this, String.format(getString(R.string.file_picker_error), e.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val fileName = getFileName(uri)
                binding.etUrl.setText(uri.toString())
                Toast.makeText(this, String.format(getString(R.string.file_selected), fileName), Toast.LENGTH_SHORT).show()
                
                if (autoPlay || true) {
                    playStream(uri.toString())
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = getString(R.string.unknown_file)
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
        Toast.makeText(this, getString(R.string.protocol_prefilled), Toast.LENGTH_LONG).show()
    }

    private fun playLocalVideo(path: String) {
        try {
            val fileUri = Uri.fromFile(File(path))
            streamPlayer.playStream(fileUri.toString())
            binding.tvStatus.visibility = View.GONE
            Toast.makeText(this, String.format(getString(R.string.playing_file), File(path).name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.playback_failed), e.message), Toast.LENGTH_LONG).show()
            binding.tvStatus.text = getString(R.string.playback_failed_simple)
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
                else -> getString(R.string.network_stream)
            }
            Toast.makeText(this, String.format(getString(R.string.connecting_protocol), protocol), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.playback_failed), e.message), Toast.LENGTH_LONG).show()
            binding.tvStatus.text = String.format(getString(R.string.connection_failed), e.message)
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        streamPlayer.release()
    }
}
