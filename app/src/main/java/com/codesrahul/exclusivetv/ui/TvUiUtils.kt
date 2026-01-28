package com.codesrahul.exclusivetv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RawRes

class TvUiUtils(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var soundFocusId: Int = 0
    private var soundClickId: Int = 0
    private var loaded = false

    // ------------------------------------------------------------
    // INIT SOUNDS
    // ------------------------------------------------------------
    fun initSounds(@RawRes focusRes: Int, @RawRes clickRes: Int) {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(attr)
            .setMaxStreams(3)
            .build()

        soundPool?.setOnLoadCompleteListener { _, _, _ ->
            loaded = true
        }

        soundFocusId = soundPool?.load(context, focusRes, 1) ?: 0
        soundClickId = soundPool?.load(context, clickRes, 1) ?: 0
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }

    // ------------------------------------------------------------
    // PUBLIC SOUND CALLS
    // ------------------------------------------------------------
    fun playFocusSound() {
        if (loaded) {
            soundPool?.play(soundFocusId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playClickSound() {
        if (loaded) {
            soundPool?.play(soundClickId, 1f, 1f, 1, 0, 1f)
        }
    }

    // ------------------------------------------------------------
    // APPLY TV-STYLE FOCUS BEHAVIOR
    // ------------------------------------------------------------
    fun applyTvFocus(view: View, scale: Float = 1.08f, elevationDp: Float = 14f) {

        view.setOnFocusChangeListener { v, hasFocus ->

            // Prevent animation stacking on fast DPAD moves
            v.animate().cancel()

            val targetScale = if (hasFocus) scale else 1f
            val targetElevation = if (hasFocus) dpToPx(v.context, elevationDp) else 0f

            v.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(130)
                .setInterpolator(DecelerateInterpolator())
                .start()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                v.elevation = targetElevation
            }

            if (hasFocus) playFocusSound()
        }

        // Preserve user's original click listener
        val originalClick = view.hasOnClickListeners()
        view.setOnClickListener { v ->
            playClickSound()
            if (originalClick) {
                v.callOnClick() // preserves existing listener
            }
        }
    }

    // ------------------------------------------------------------
    // ACCESSIBILITY / UI FIXES
    // ------------------------------------------------------------
    fun tintTextViewDrawable(textView: android.widget.TextView, color: Int) {
        val drawables = textView.compoundDrawables
        for (drawable in drawables) {
            drawable?.let {
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                    androidx.core.graphics.drawable.DrawableCompat.wrap(it).mutate(),
                    color
                )
            }
        }
    }

    // ------------------------------------------------------------
    // UTIL
    // ------------------------------------------------------------
    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
