package com.andwin.video.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class StreamPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var isPlaying = false

    fun initialize(playerView: PlayerView) {
        this.playerView = playerView
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            this.playerView?.player = player
        }
    }

    fun playStream(url: String) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            isPlaying = true
        }
    }

    fun playLocalFile(uri: Uri) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            isPlaying = true
        }
    }

    fun pause() {
        exoPlayer?.playWhenReady = false
        isPlaying = false
    }

    fun resume() {
        exoPlayer?.playWhenReady = true
        isPlaying = true
    }

    fun stop() {
        exoPlayer?.stop()
        isPlaying = false
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    fun getDuration(): Long = exoPlayer?.duration ?: 0L

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        playerView?.player = null
        playerView = null
        isPlaying = false
    }
}
