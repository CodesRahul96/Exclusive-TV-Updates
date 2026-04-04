package com.codesrahul.exclusivetv.vlc

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

/**
 * High-performance VLC Engine Manager.
 * Provides the same protocol compatibility as the VLC App but integrated in ExclusiveTV.
 */
class VlcPlayerManager(private val context: Context) {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    fun initialize(videoLayout: VLCVideoLayout, userAgent: String) {
        val options = ArrayList<String>()
        
        // GLOBAL ENGINE OPTIONS
        options.add("--http-user-agent=$userAgent")
        options.add("--network-caching=5000") // 5s aggressive buffer for IPTV
        options.add("--rtsp-tcp") // Force TCP for stability
        options.add("--audio-time-stretch") // Maintain pitch on speed change
        options.add("--no-stats") // Performance optimization
        
        // HW Acceleration logic
        options.add("--codec=mediacodec_all")

        libVLC = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVLC)
        
        // Attach the VideoLayout from our XML
        mediaPlayer?.attachViews(videoLayout, null, true, false)
        
        // Default aspect ratio matches our 'Fill' logic
        mediaPlayer?.aspectRatio = null // Default
    }

    fun play(url: String) {
        if (libVLC == null) return
        
        val media = Media(libVLC, Uri.parse(url))
        
        // PER-STREAM OPTIONS
        media.addOption(":network-caching=5000")
        media.addOption(":clock-jitter=500")
        media.addOption(":clock-synchro=0")
        
        mediaPlayer?.media = media
        mediaPlayer?.play()
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        } else {
            mediaPlayer?.play()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
        libVLC?.release()
        mediaPlayer = null
        libVLC = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
    
    fun getMediaPlayer(): MediaPlayer? = mediaPlayer
}
