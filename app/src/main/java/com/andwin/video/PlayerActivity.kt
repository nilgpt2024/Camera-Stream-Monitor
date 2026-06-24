package com.andwin.video

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.andwin.video.databinding.ActivityPlayerBinding
import com.andwin.video.player.StreamPlayer
import com.andwin.video.utils.LocaleHelper
import java.io.File

class PlayerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        super.applyOverrideConfiguration(LocaleHelper.applyOverrideConfiguration(overrideConfiguration))
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var streamPlayer: StreamPlayer
    private var autoPlay: Boolean = false

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
        binding.toolbar.setNavigationOnClickListener {
            streamPlayer.release()
            finish()
        }

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

    private fun playLocalVideo(path: String) {
        try {
            val fileUri = Uri.fromFile(File(path))
            streamPlayer.playStream(fileUri.toString())
            binding.statusOverlay.visibility = View.GONE
            Toast.makeText(this, String.format(getString(R.string.playing_file), File(path).name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, String.format(getString(R.string.playback_failed), e.message), Toast.LENGTH_LONG).show()
            binding.tvStatus.text = getString(R.string.playback_failed_simple)
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun playStream(url: String) {
        try {
            binding.statusOverlay.visibility = View.GONE
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
