package com.codesrahul.exclusivetv.tis

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputManager
import android.media.tv.TvInputService
import android.net.Uri
import android.view.Surface
import com.codesrahul.exclusivetv.MainActivity

class SimpleTvInputService : TvInputService() {
    override fun onCreateSession(inputId: String): Session {
        return SimpleSession(this)
    }

    inner class SimpleSession(context: Context) : Session(context) {
        override fun onSetSurface(surface: Surface?): Boolean {
            return true
        }

        override fun onSetStreamVolume(volume: Float) {}

        override fun onTune(channelUri: Uri?): Boolean {
            // When user tunes to this input, just launch the main app
            val intent = Intent(this@SimpleTvInputService, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            
            // Notify that we tuned (even though we are switching away)
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
            return true // Tuning handled (by leaving)
        }

        override fun onSetCaptionEnabled(enabled: Boolean) {}

        override fun onRelease() {}
    }
}
