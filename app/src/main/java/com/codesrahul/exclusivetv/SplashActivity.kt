package com.codesrahul.exclusivetv

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@SuppressLint("CustomSplashScreen")
class SplashActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide System UI immediately for full immersion
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.logo)
        val appName = findViewById<View>(R.id.app_name)
        val progressBar = findViewById<View>(R.id.progressBar)
        val loadingText = findViewById<View>(R.id.loading_text)

        // Animation Sequence
        val fadeInLogo = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply { duration = 800 }
        val scaleXLogo = ObjectAnimator.ofFloat(logo, "scaleX", 0.8f, 1.0f).apply { duration = 800 }
        val scaleYLogo = ObjectAnimator.ofFloat(logo, "scaleY", 0.8f, 1.0f).apply { duration = 800 }

        val fadeInText = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply { 
            duration = 600 
            startDelay = 400
        }
        
        val fadeInLoader = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply { 
            duration = 500
            startDelay = 1000 
        }

        val fadeInLoadingText = ObjectAnimator.ofFloat(loadingText, "alpha", 0f, 1f).apply { 
            duration = 500
            startDelay = 1200 
        }

        AnimatorSet().apply {
            play(fadeInLogo).with(scaleXLogo).with(scaleYLogo).with(fadeInText)
            play(fadeInLoader).with(fadeInLoadingText)
            interpolator = DecelerateInterpolator()
            start()
        }



        // Navigate to Main Activity after delay
        handler.postDelayed(runnable, 2500) // 2.5 seconds total splash time
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}
