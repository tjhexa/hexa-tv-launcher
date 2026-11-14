package com.hexa.hometv

import android.content.Context
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Plays the bundled background clip, muted and looping, into a [SurfaceView].
 *
 * The surface is opaque and sits behind this window's views, so the normal
 * wallpaper ImageViews and scrims still draw over it. [release] must be called
 * when switching back to a still wallpaper; it frees the decoder buffers.
 */
class VideoBackground(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val assetPath: String,
    private val onFirstFrame: () -> Unit
) : SurfaceHolder.Callback {

    private var player: MediaPlayer? = null
    private var prepared = false
    private var playWhenReady = true
    private var released = false
    private var firstFrameDelivered = false

    init {
        surfaceView.holder.addCallback(this)
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
    }

    fun play() {
        if (released) return
        playWhenReady = true
        if (prepared) {
            try {
                player?.start()
            } catch (e: Exception) {
                // surface went away between checks
            }
        }
    }

    fun pause() {
        playWhenReady = false
        if (prepared) {
            try {
                player?.pause()
            } catch (e: Exception) {
                // already stopped
            }
        }
    }

    fun release() {
        released = true
        surfaceView.holder.removeCallback(this)
        closePlayer()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (released) return
        openPlayer(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        closePlayer()
    }

    private fun openPlayer(holder: SurfaceHolder) {
        closePlayer()
        val mp = MediaPlayer()
        try {
            context.assets.openFd(assetPath).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setSurface(holder.surface)
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            mp.setOnPreparedListener { p ->
                if (released) {
                    p.release()
                    return@setOnPreparedListener
                }
                prepared = true
                p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                if (playWhenReady) p.start()
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && !firstFrameDelivered) {
                    firstFrameDelivered = true
                    onFirstFrame()
                    true
                } else {
                    false
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                closePlayer()
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun closePlayer() {
        val p = player ?: return
        player = null
        prepared = false
        try {
            p.setSurface(null)
        } catch (ignored: Exception) {
        }
        try {
            p.reset()
        } catch (ignored: Exception) {
        }
        try {
            p.release()
        } catch (ignored: Exception) {
        }
    }
}
