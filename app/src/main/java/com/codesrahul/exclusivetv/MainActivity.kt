package com.codesrahul.exclusivetv

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVModel
import com.codesrahul.exclusivetv.RootCheckUtil


class MainActivity : FragmentActivity(), UpdateManager.UpdateListener {

    private var webFragment = WebFragment()
    private var errorFragment = ErrorFragment()
    private var loadingFragment = LoadingFragment()
    private var infoFragment = InfoFragment()
    private var channelFragment = ChannelFragment()
    private var timeFragment = TimeFragment()
    private var menuFragment = MenuFragment()
    private var settingFragment = SettingFragment()
    private var importProgressFragment = ImportProgressFragment()
    private var trackSelectionFragment = TrackSelectionFragment()

    private lateinit var updateManager: UpdateManager
    
    // Watermark views
    private lateinit var watermarkContainer: FrameLayout
    private lateinit var tvWatermark: TextView



    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideMenu = 15 * 1000L
    private val delayHideSetting = 30 * 1000L
    private val delayHideTrackSelection = 30 * 1000L
    
    // Right arrow key hold tracking for audio track
    private val rightArrowHandler = Handler(Looper.getMainLooper())
    private var isRightArrowPressed = false
    private val rightArrowHoldRunnable = Runnable {
        if (isRightArrowPressed) {
            showAudioSelector()
            isRightArrowPressed = false
        }
    }

    // Periodic Update Check
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateCheckInterval: Long = 15 * 60 * 1000 // 15 minutes

    private var doubleBackToExitPressedOnce = false

    lateinit var gestureDetector: GestureDetector
    lateinit var gestureListener: GestureListener

    private var server: SimpleServer? = null

    private val rootHandler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 5000 // Check every 5 seconds
    private var wasRooted = false

    private var lastRefreshTime = 0L
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshInterval: Long = 30 * 60 * 1000L // 30 minutes
    private val resumeRefreshThreshold: Long = 15 * 60 * 1000L // 15 minutes

    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (isVpnActive()) {
                runOnUiThread {
                    Log.d(TAG, "VPN detected via Callback. Exiting app.")
                    Toast.makeText(this@MainActivity, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
                    finishAffinity() // Close all activities
                }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            if (!isVpnActive()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "VPN disconnected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        startRootMonitoring()

//        requestWindowFeature(FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            windowInsetsController.let { controller ->
                controller.isAppearanceLightNavigationBars = true
                controller.isAppearanceLightStatusBars = true
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.setAttributes(lp)
        }

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, webFragment)
                .add(R.id.main_browse_fragment, errorFragment)
                .add(R.id.main_browse_fragment, loadingFragment)
                .add(R.id.main_browse_fragment, timeFragment)
                .add(R.id.main_browse_fragment, infoFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .add(R.id.main_browse_fragment, menuFragment)
                .add(R.id.main_browse_fragment, settingFragment)
                .add(R.id.main_browse_fragment, importProgressFragment)
                .add(R.id.main_browse_fragment, trackSelectionFragment)
                .hide(menuFragment)
                .hide(settingFragment)
                .hide(importProgressFragment)
                .hide(trackSelectionFragment)
                .hide(errorFragment)
                .show(loadingFragment)
                .hide(timeFragment)
                .hide(webFragment)
                .commitNow()
        } else {
             // restore fragments
             val fragments = supportFragmentManager.fragments
             for (fragment in fragments) {
                 if (fragment is WebFragment) {
                     webFragment = fragment
                 }
                 if (fragment is ErrorFragment) {
                     errorFragment = fragment
                 }
                 if (fragment is LoadingFragment) {
                     loadingFragment = fragment
                 }
                 if (fragment is InfoFragment) {
                     infoFragment = fragment
                 }
                 if (fragment is ChannelFragment) {
                     channelFragment = fragment
                 }
                 if (fragment is TimeFragment) {
                     timeFragment = fragment
                 }
                 if (fragment is MenuFragment) {
                     menuFragment = fragment
                 }
                 if (fragment is SettingFragment) {
                     settingFragment = fragment
                 }
                 if (fragment is ImportProgressFragment) {
                     importProgressFragment = fragment
                 }
                 if (fragment is TrackSelectionFragment) {
                     trackSelectionFragment = fragment
                 }
             }
        }

        gestureListener = GestureListener(this)
        gestureDetector = GestureDetector(this, gestureListener)

        showTime()

        updateManager = UpdateManager(this, com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE)
        updateManager.checkAndUpdate()
        
        // Setup watermark
        setupWatermark()
        
        startPeriodicRefresh()
        startPeriodicUpdateCheck()

        // EPG Update Listener
        SP.setOnSharedPreferenceChangeListener(object : OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(key: String) {
                if (key == SP.KEY_EPG) {
                    if (SP.epgEnabled) {
                        Log.i(TAG, "EPG URL changed, forcing refresh")
                        runOnUiThread {
                            com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, SP.config ?: "", silent = true)
                        }
                    }
                } else if (key == SP.KEY_EPG_ENABLED) {
                    Log.i(TAG, "EPG toggle changed: ${SP.epgEnabled}")
                    runOnUiThread {
                        if (SP.epgEnabled) {
                            com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, SP.config ?: "", silent = true)
                        } else {
                            // Silently refresh UI models to clear EPG data from view
                            com.codesrahul.exclusivetv.models.TVList.refreshModels(this@MainActivity)
                        }
                    }
                }
            }
        })
    }

    private fun startPeriodicRefresh() {
        refreshHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isUpdateRequired) {
                    Log.i(TAG, "Triggering periodic background refresh")
                    val config = SP.config
                    if (!config.isNullOrEmpty() && config.startsWith("http")) {
                        TVList.update(this@MainActivity, config, silent = true)
                    }
                    lastRefreshTime = System.currentTimeMillis()
                }
                refreshHandler.postDelayed(this, refreshInterval)
            }
        }, refreshInterval)
    }
    
    private fun setupWatermark() {
        watermarkContainer = findViewById<FrameLayout>(R.id.watermarkContainer)
        tvWatermark = findViewById<TextView>(R.id.tvWatermark)
        
        updateWatermarkVisibility()
        updateWatermarkOpacity()
        updateWatermarkPosition()
    }
    
    private fun startPeriodicUpdateCheck() {
        updateHandler.postDelayed(object : Runnable {
            override fun run() {
                Log.i(TAG, "Triggering periodic update check")
                updateManager.checkAndUpdate()
                updateHandler.postDelayed(this, updateCheckInterval)
            }
        }, updateCheckInterval)
    }
    
    fun updateWatermarkVisibility() {
        watermarkContainer.visibility = if (SP.watermarkEnabled) View.VISIBLE else View.GONE
    }
    
    private fun updateWatermarkOpacity() {
        val opacity = SP.watermarkOpacity
        tvWatermark.alpha = opacity / 100f
    }
    
    private fun updateWatermarkPosition() {
        val layoutParams = watermarkContainer.layoutParams as FrameLayout.LayoutParams
        when (SP.watermarkPosition) {
            "bottom_right" -> layoutParams.gravity = Gravity.BOTTOM or Gravity.END
            "bottom_left" -> layoutParams.gravity = Gravity.BOTTOM or Gravity.START
            "top_right" -> layoutParams.gravity = Gravity.TOP or Gravity.END
            "top_left" -> layoutParams.gravity = Gravity.TOP or Gravity.START
        }
        watermarkContainer.layoutParams = layoutParams
    }

    override fun onResume() {
        super.onResume()
        if (!isUpdateRequired) {
            // Check for refresh on resume
            val now = System.currentTimeMillis()
            if (now - lastRefreshTime > resumeRefreshThreshold) {
                Log.i(TAG, "Triggering resume refresh")
                val config = SP.config
                if (!config.isNullOrEmpty() && config.startsWith("http")) {
                    TVList.update(config, silent = true)
                    lastRefreshTime = now
                }
            }
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()

        Log.i(TAG, "Setting up observers")
        
        // 1. Observe Group Changes (Initial Load or Update)
        TVList.groupModel.change.observe(this) { _ ->
            Log.i(TAG, "groupModel changed")
            val currentGroup = TVList.groupModel.tvGroupModel.value
            if (currentGroup != null) {
                val currentPlayingUrl = webFragment.getCurrentUrl() ?: ""
                val pos = TVList.position.value ?: -1
                
                if (pos == -1) {
                    // Initial playback on load
                    val targetPos = if (SP.watchLast) com.codesrahul.exclusivetv.models.TVList.restorePosition() else if (SP.channel > 0) SP.channel - 1 else 0
                    if (com.codesrahul.exclusivetv.models.TVList.setPosition(targetPos)) {
                        // Note: setPosition triggers the position observer which handles playback
                    }
                } else if (currentPlayingUrl.isNotEmpty()) {
                    // This was a silent background refresh
                    // Find the new index of the current URL
                    val newIndex = com.codesrahul.exclusivetv.models.TVList.restorePosition() 
                    if (newIndex != -1 && newIndex != pos) {
                        Log.i(TAG, "Updating index for current channel from $pos to $newIndex")
                        com.codesrahul.exclusivetv.models.TVList.setPosition(newIndex)
                    }
                } else {
                    // No video playing, maybe first load finished or error state
                    TVList.getTVModel(pos)?.let { playChannel(it) }
                }
                menuFragment.update()
                setupCollectionObservers()
            }
        }

        // 2. Observe Position Changes (Navigation)
        TVList.position.observe(this) { pos ->
            if (pos == null) return@observe
            Log.i(TAG, "Position changed to $pos")
            val model = TVList.getTVModel(pos)
            if (model != null) {
                // IMPORTANT: Only trigger playChannel if it's NOT already playing this URL
                val currentUrl = webFragment.getCurrentUrl() ?: ""
                val targetUrl = model.tv.uris.firstOrNull() ?: ""
                
                if (targetUrl != currentUrl || currentUrl.isEmpty()) {
                    playChannel(model)
                } else {
                    Log.i(TAG, "Skipping playChannel - URL already playing")
                }
            }
        }

        // setupCollectionObservers() moved to groupModel.change observer

        // TODO group position

        TVList.importProgress.observe(this) { progress ->
            if (progress > 0) {
                 if (importProgressFragment.isHidden) {
                     supportFragmentManager.beginTransaction()
                         .show(importProgressFragment)
                         .commitNowAllowingStateLoss()
                 }
                
                 val current = if (progress == 60) 0 else 60
                 if(progress == 60) {
                      importProgressFragment.animateProgress(0, 60)
                 } else if (progress == 100) {
                      importProgressFragment.animateProgress(60, 100)
                      // Hide after delay or let user dismiss? 
                      // User said "move to 100% and close".
                      handler.postDelayed({ 
                          if (!importProgressFragment.isHidden) {
                              supportFragmentManager.beginTransaction()
                                  .hide(importProgressFragment)
                                  .commitNowAllowingStateLoss()
                              importProgressFragment.setProgress(0) // Reset
                          }
                      }, 1000)
                 }
            } else {
                 // Reset or hide
                 if (!importProgressFragment.isHidden) {
                     supportFragmentManager.beginTransaction()
                         .hide(importProgressFragment)
                         .commitNowAllowingStateLoss()
                 }
                 importProgressFragment.setProgress(0)
            }
        }

        val port = PortUtil.findFreePort()
        if (port != -1) {
            server = SimpleServer(this, port)
        }
    }

    fun ready(tag: String) {
        Log.i(TAG, "ready $tag")
    }

    fun setServer(server: String) {
        settingFragment.setServer(server)
        settingFragment.setVersionName(appVersionName)
    }

    private fun playChannel(tvModel: TVModel) {
        Log.i(TAG, "playChannel ${tvModel.tv.title}")
        
        // Hide error and show loader
        hideErrorFragment()
        showFragment(loadingFragment)
        
        // Remove previous observers to avoid leaks/multi-triggers
        tvModel.errInfo.removeObservers(this)
        tvModel.ready.removeObservers(this)
        
        val currentPos = TVList.position.value ?: -1
        
        // Observe Error Info
        tvModel.errInfo.observe(this) { info: String? ->
            if (info != null && tvModel.tv.id == currentPos) {
                if (info == "" || info == "web ok") {
                    Log.i(TAG, "${tvModel.tv.title} Playing")
                    hideFragment(loadingFragment)
                    showFragment(webFragment)
                } else {
                    Log.i(TAG, "${tvModel.tv.title} Error: $info")
                    hideFragment(loadingFragment)
                    hideFragment(webFragment)
                    showErrorFragment(info)
                }
            }
        }

        // Play in WebFragment
        webFragment.play(tvModel)
        
        // Show info overlay
        infoFragment.show(tvModel)
        if (SP.channelNum) {
            channelFragment.show(tvModel as TVModel)
        }

        // Auto-hide loader after timeout backup
        handler.removeCallbacksAndMessages("loader_timeout")
        handler.postAtTime({ 
             if (tvModel.tv.id == TVList.position.value) {
                hideFragment(loadingFragment)
            }
        }, "loader_timeout", SystemClock.uptimeMillis() + 6000)
    }

    private fun setupCollectionObservers() {
        TVList.listModel.forEach { tvModel ->
            tvModel.like.removeObservers(this)
            tvModel.like.observe(this) { liked ->
                if (liked != null) {
                    if (liked) {
                        TVList.groupModel.getTVListModel(0)?.replaceTVModel(tvModel)
                    } else {
                        TVList.groupModel.getTVListModel(0)?.removeTVModel(tvModel.tv.id)
                    }
                    SP.setLike(tvModel.tv.id, liked)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            val screenWidth = windowManager.defaultDisplay.width
            val isRightHalf = event.x > screenWidth / 2
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isRightHalf && menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                        gestureListener.startTimedLongPress()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gestureListener.cancelTimedLongPress()
                }
            }
            
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    inner class GestureListener(private val context: Context) :
        GestureDetector.SimpleOnGestureListener() {

        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var isLongPressActive = false
        private val audioRunnable = Runnable {
            if (isLongPressActive) {
                showAudioSelector()
                isLongPressActive = false // Prevent 5s callback
            }
        }
        private val settingsRunnable = Runnable {
            if (isLongPressActive) {
                showSetting()
                isLongPressActive = false
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Close settings or audio track selector if they're open
            if (!settingFragment.isHidden) {
                hideSettingFragment()
                return true
            }
            
            if (!trackSelectionFragment.isHidden) {
                hideTrackSelectionFragment()
                return true
            }
            
            // Single tap anywhere → menu
            showFragment(menuFragment)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showSetting()
            return true
        }

        fun startTimedLongPress() {
            isLongPressActive = true
            
            // Schedule 3-second callback for audio selector
            longPressHandler.postDelayed(audioRunnable, 3000)
            
            // Schedule 5-second callback for settings
            longPressHandler.postDelayed(settingsRunnable, 5000)
        }

        fun cancelTimedLongPress() {
            isLongPressActive = false
            longPressHandler.removeCallbacks(audioRunnable)
            longPressHandler.removeCallbacks(settingsRunnable)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if ((e1?.x ?: 0f) > windowManager.defaultDisplay.width / 3
                && (e1?.x ?: 0f) < windowManager.defaultDisplay.width * 2 / 3
            ) {
                if (velocityY > 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        prev()
                    }
                }
                if (velocityY < 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        next()
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        private fun adjustVolume(deltaY: Float) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val deltaVolume = deltaY / 1000 * maxVolume / windowManager.defaultDisplay.height

            var newVolume = currentVolume + deltaVolume
            if (newVolume < 0) {
                newVolume = 0F
            } else if (newVolume > maxVolume) {
                newVolume = maxVolume.toFloat()
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)

            // You can add a toast to display the current volume
            Toast.makeText(context, "Volume: $newVolume / $maxVolume", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPlayEnd() {
        val tvModel = TVList.getTVModel()
        if (tvModel != null && SP.repeatInfo) {
            infoFragment.show(tvModel)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }
    }

    fun play(position: Int) {
        val currentTvModel = TVList.getTVModel() ?: return
        val prevGroup = currentTvModel.groupIndex
        if (position > -1 && position < TVList.size()) {
            TVList.setPosition(position)
            val newTvModel = TVList.getTVModel() ?: return
            val currentGroup = newTvModel.groupIndex
            if (currentGroup != prevGroup) {
                Log.i(TAG, "group change")
                menuFragment.updateList(currentGroup)
            }
        } else {
            Toast.makeText(this, "Channel does not exist", Toast.LENGTH_LONG).show()
        }
    }

    fun prev() {
        val currentTvModel = TVList.getTVModel() ?: return
        val prevGroup = currentTvModel.groupIndex
        var position = TVList.position.value?.dec() ?: 0
        if (position == -1) {
            position = TVList.size() - 1
        }
        TVList.setPosition(position)
        val newTvModel = TVList.getTVModel() ?: return
        val currentGroup = newTvModel.groupIndex
        if (currentGroup != prevGroup) {
            Log.i(TAG, "group change")
            menuFragment.updateList(currentGroup)
        }
    }

    fun next() {
        val currentTvModel = TVList.getTVModel() ?: return
        val prevGroup = currentTvModel.groupIndex
        var position = TVList.position.value?.inc() ?: 0
        if (position == TVList.size()) {
            position = 0
        }
        TVList.setPosition(position)
        val newTvModel = TVList.getTVModel() ?: return
        val currentGroup = newTvModel.groupIndex
        if (currentGroup != prevGroup) {
            Log.i(TAG, "group change")
            menuFragment.updateList(currentGroup)
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitNow()

        when (fragment) {
            menuFragment -> menuActive()
            settingFragment -> settingActive()
            trackSelectionFragment -> trackSelectionActive()
        }
    }

    private fun hideFragment(fragment: Fragment) {
        if (fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitNowAllowingStateLoss()
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!menuFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(menuFragment).commit()
            }
        }
    }

    fun settingActive() {
        handler.removeCallbacks(hideSetting)
        handler.postDelayed(hideSetting, delayHideSetting)
    }

    private val hideSetting = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!settingFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(settingFragment).commitAllowingStateLoss()
                showTime()
            }
        }
    }

    fun trackSelectionActive() {
        handler.removeCallbacks(hideTrackSelection)
        handler.postDelayed(hideTrackSelection, delayHideTrackSelection)
    }

    private val hideTrackSelection = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!trackSelectionFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(trackSelectionFragment).commitNow()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Reset ALL auto-hide timers on ANY key event before dispatching it to views
        if (!menuFragment.isHidden) menuActive()
        if (!settingFragment.isHidden) settingActive()
        if (!trackSelectionFragment.isHidden) trackSelectionActive()
        
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Reset ALL auto-hide timers on ANY touch event
        if (!menuFragment.isHidden) menuActive()
        if (!settingFragment.isHidden) settingActive()
        if (!trackSelectionFragment.isHidden) trackSelectionActive()
        return super.dispatchTouchEvent(ev)
    }

    fun showTime() {
        if (SP.time) {
            showFragment(timeFragment)
        } else {
            hideFragment(timeFragment)
        }
    }

    private fun showChannel(channel: String) {
        if (!menuFragment.isHidden) {
            return
        }

        if (settingFragment.isVisible) {
            return
        }

        channelFragment.show(channel)
    }


    private fun channelUp() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                prev()
                return
            }
            next()
        }
    }

    private fun channelDown() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                next()
                return
            }
            prev()
        }
    }

    private fun back() {
        if (!menuFragment.isHidden) {
            hideMenuFragment()
            return
        }

        if (!settingFragment.isHidden) {
            hideSettingFragment()
            return
        }

        if (!trackSelectionFragment.isHidden) {
            hideTrackSelectionFragment()
            return
        }

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }

        doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }

    private fun showSetting() {
        if (!menuFragment.isHidden) {
            return
        }
        showFragment(settingFragment)
    }

    fun hideMenuFragment() {
        supportFragmentManager.beginTransaction()
            .hide(menuFragment)
            .commit()
        Log.i(TAG, "SP.time ${SP.time}")
    }

    private fun hideSettingFragment() {
        supportFragmentManager.beginTransaction()
            .hide(settingFragment)
            .commit()
        showTime()
    }

    private fun showAudioSelector() {
        if (!menuFragment.isHidden || !settingFragment.isHidden) return
        
        val tracks = webFragment.getAudioTracks()
        if (tracks.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }
        
        trackSelectionFragment.setTracks(tracks, object : TrackSelectionFragment.TrackSelectionListener {
            override fun onTrackSelected(index: Int) {
                val currentTv = TVList.getTVModel()
                if (currentTv != null && currentTv.tv.uris.isNotEmpty()) {
                    SP.setAudioTrack(currentTv.tv.uris[0], index)
                }
                webFragment.setAudioTrack(index)
                hideTrackSelectionFragment()
            }

            override fun onDismiss() {
                hideTrackSelectionFragment()
            }
        })
        
        supportFragmentManager.beginTransaction()
            .show(trackSelectionFragment)
            .commitNow()
        trackSelectionActive()
    }

    private fun hideTrackSelectionFragment() {
        if (trackSelectionFragment.isHidden) return
        supportFragmentManager.beginTransaction()
            .hide(trackSelectionFragment)
            .commitNow()
    }

    private fun showErrorFragment(msg: String) {
        errorFragment.show(msg)
        if (!errorFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(errorFragment)
            .commitNow()
    }

    private fun hideErrorFragment() {
        errorFragment.show("hide")
        if (errorFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(errorFragment)
            .commitNow()
    }

    fun onKey(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKey keyCode $keyCode, repeat ${event?.repeatCount}")
        when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                showChannel("0")
                return true
            }

            KeyEvent.KEYCODE_1 -> {
                showChannel("1")
                return true
            }

            KeyEvent.KEYCODE_2 -> {
                showChannel("2")
                return true
            }

            KeyEvent.KEYCODE_3 -> {
                showChannel("3")
                return true
            }

            KeyEvent.KEYCODE_4 -> {
                showChannel("4")
                return true
            }

            KeyEvent.KEYCODE_5 -> {
                showChannel("5")
                return true
            }

            KeyEvent.KEYCODE_6 -> {
                showChannel("6")
                return true
            }

            KeyEvent.KEYCODE_7 -> {
                showChannel("7")
                return true
            }

            KeyEvent.KEYCODE_8 -> {
                showChannel("8")
                return true
            }

            KeyEvent.KEYCODE_9 -> {
                showChannel("9")
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                back()
                return true
            }

             KeyEvent.KEYCODE_BOOKMARK -> {
                 showSetting()
                 return true
             }

             KeyEvent.KEYCODE_M -> {
                 SP.moveMode = !SP.moveMode
                 Toast.makeText(this, "Move mode: ${if(SP.moveMode) "on" else "off"}", Toast.LENGTH_SHORT).show()
                 return true
             }

             KeyEvent.KEYCODE_UNKNOWN -> {
                return true
            }

            KeyEvent.KEYCODE_HELP -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_SETTINGS -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    if (infoFragment.isShowing()) {
                        infoFragment.dismiss()
                    } else {
                        val tvModel = TVList.getTVModel()
                        if (tvModel != null) {
                            infoFragment.show(tvModel)
                        }
                    }
                    return true
                }
                return !trackSelectionFragment.isHidden || !menuFragment.isHidden || !settingFragment.isHidden
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    channelUp()
                    return true
                }
                // If menu is open, let the RecyclerView handle it naturally - return false
                if (!menuFragment.isHidden) {
                    return false
                }
                // For settings and track selection, let them handle navigation naturally
                return false
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    channelDown()
                    return true
                }
                // If menu is open, let the RecyclerView handle it naturally - return false
                if (!menuFragment.isHidden) {
                    return false
                }
                // For settings and track selection, let them handle navigation naturally
                return false
            }
            
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // 1. If menu is open, let menu handle it for navigation (e.g. Categories -> Channel List)
                if (!menuFragment.isHidden) {
                    if (menuFragment.onKey(keyCode)) return true
                }
                
                // 2. For settings and track selection, let them handle navigation naturally
                if (!settingFragment.isHidden || !trackSelectionFragment.isHidden) {
                    return false
                }
                
                // 3. Handle right arrow for audio track selector (3s hold) when all fragments are hidden
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        // Start 3-second timer on first press
                        isRightArrowPressed = true
                        rightArrowHandler.postDelayed(rightArrowHoldRunnable, 3000)
                        return true
                    } else if (event?.action == KeyEvent.ACTION_UP) {
                        // Cancel timer on release
                        isRightArrowPressed = false
                        rightArrowHandler.removeCallbacks(rightArrowHoldRunnable)
                        return true
                    }
                }
                
                return false
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    showFragment(menuFragment)
                    return true
                }
                // Let fragments handle their own left navigation
                return false
            }


        }
        return false
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (android.content.Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrEmpty()) {
                Log.i(TAG, "Voice Search Query: $query")
                handleVoiceSearch(query)
            }
        }
    }

    private fun handleVoiceSearch(query: String) {
        // Find channel by name (fuzzy match)
        val channel = TVList.findChannelByName(query)
        if (channel != null) {
            Toast.makeText(this, "Playing: ${channel.tv.title}", Toast.LENGTH_SHORT).show()
            webFragment.play(channel)
        } else {
            Toast.makeText(this, "Channel not found: $query", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onKey(keyCode, event)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        rootHandler.removeCallbacksAndMessages(null) // Stop monitoring to prevent memory leaks
        refreshHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        server?.stop()
    }

    // Security check helper
    private fun isVpnActive(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun startRootMonitoring() {
        rootHandler.post(object : Runnable {
            override fun run() {
                val isRooted = RootCheckUtil.isDeviceRooted()
                if (isRooted && !wasRooted) {
                    Log.d(TAG, "Root access detected. Exiting app.")
                    Toast.makeText(this@MainActivity, "Rooted device detected. App cannot run.", Toast.LENGTH_LONG).show()
                    finishAffinity()
                }
                wasRooted = isRooted
                rootHandler.postDelayed(this, checkInterval)
            }
        })
    }

    override fun onForceUpdate() {
        Log.i(TAG, "Force update detected. Blocking app usage.")
        isUpdateRequired = true
        
        // Stop playback
        webFragment.stop()
        
        // Hide all fragments to prevent usage
        hideFragment(webFragment)
        hideFragment(menuFragment)
        hideFragment(settingFragment)
        hideFragment(trackSelectionFragment)
        
        Toast.makeText(this, "Update Required - Please update to continue", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "MainActivity"
        @JvmStatic
        var isUpdateRequired = false
            private set
    }
}
