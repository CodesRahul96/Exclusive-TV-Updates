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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.codesrahul.exclusivetv.models.TVModel
import com.codesrahul.exclusivetv.RootCheckUtil
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings


class MainActivity : FragmentActivity(), UpdateManager.UpdateListener {

    var webFragment = WebFragment()
    private var errorFragment = ErrorFragment()
    private var loadingFragment = LoadingFragment()
    private var infoFragment = InfoFragment()
    private var channelFragment = ChannelFragment()
    private var timeFragment = TimeFragment()
    private var menuFragment = MenuFragment()
    private var settingFragment = SettingFragment()
    private var importProgressFragment = ImportProgressFragment()
    private var trackSelectionFragment = TrackSelectionFragment()
    private var epgGridFragment = EpgGridFragment()
    private var offlineFragment = OfflineFragment()
    private var maintenanceFragment = MaintenanceFragment()

    private val spListener = object : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(key: String) {
            if (isMaintenanceMode) return
            if (key == SP.KEY_EPG) {
                if (SP.epgEnabled) {
                    runOnUiThread {
                        com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, SP.config ?: "", silent = true)
                    }
                }
            } else if (key == SP.KEY_EPG_ENABLED) {
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
    }

    private var isMaintenanceMode = false

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
            runOnUiThread {
                hideOfflineScreen()
            }
            if (isVpnActive()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
                    finishAffinity() // Close all activities
                }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread {
                showOfflineScreen()
            }
            if (!isVpnActive()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Internet connection lost.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        
        // Initialize Firebase Remote Config (Triggers parallel network request for Main API)
        initRemoteConfig()
        // Initial network check
        if (!isNetworkAvailable()) {
            handler.postDelayed({
                showOfflineScreen()
            }, 500) // Small delay to let fragments settle
        }

        startRootMonitoring()
        
        // Sync network clock
        CoroutineScope(Dispatchers.IO).launch { Utils.init() }

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
                .add(R.id.main_browse_fragment, epgGridFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .add(R.id.main_browse_fragment, menuFragment)
                .add(R.id.main_browse_fragment, settingFragment)
                .add(R.id.main_browse_fragment, importProgressFragment)
                .add(R.id.main_browse_fragment, trackSelectionFragment)
                .add(R.id.main_browse_fragment, offlineFragment)
                .add(R.id.main_browse_fragment, maintenanceFragment)
                .hide(menuFragment)
                .hide(settingFragment)
                .hide(importProgressFragment)
                .hide(trackSelectionFragment)
                .hide(offlineFragment)
                .hide(maintenanceFragment)
                .hide(epgGridFragment)
                .hide(errorFragment)
                .show(loadingFragment)
                .hide(timeFragment)
                .hide(webFragment)
                .commit()
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
                 if (fragment is MaintenanceFragment) {
                     maintenanceFragment = fragment
                 }
             }
        }

        gestureListener = GestureListener()
        gestureDetector = GestureDetector(this, gestureListener)

        showTime()

        updateManager = UpdateManager(this, com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE)
        updateManager.checkAndUpdate()
        
        // Initialize watermark
        setupWatermark()
        
        startPeriodicRefresh()

        // EPG Update Listener
        SP.setOnSharedPreferenceChangeListener(spListener)

        setupObservers()
    }

    private fun setupObservers() {
        
        // 1. Observe Group Changes (Initial Load or Update)
        TVList.groupModel.change.observe(this) { _ ->
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
                    // Find the new index of the EXACT CURRENT URL to preserve playback
                    val newIndex = com.codesrahul.exclusivetv.models.TVList.listModel.indexOfFirst { model ->
                        model.tv.uris.any { it == currentPlayingUrl }
                    }
                    if (newIndex != -1 && newIndex != pos) {
                        com.codesrahul.exclusivetv.models.TVList.setPosition(newIndex)
                    }
                } else {
                    // No video playing, maybe first load finished or error state
                    TVList.getTVModel(pos)?.let { playChannel(it) }
                }
                menuFragment.update()
                
                // Only setup collection observers once for the whole list
                setupCollectionObservers()
            }
        }

        // 2. Observe Position Changes (Navigation)
        TVList.position.observe(this) { pos ->
            if (pos == null) return@observe
            val model = TVList.getTVModel(pos)
            if (model != null) {
                // IMPORTANT: Only trigger playChannel if it's NOT already playing this URL
                val currentUrl = webFragment.getCurrentUrl() ?: ""
                val targetUrl = model.tv.uris.firstOrNull() ?: ""
                
                if (targetUrl != currentUrl || currentUrl.isEmpty()) {
                    playChannel(model)
                } else {
                }
            }
        }

        // 3. Import Progress (Top Card) - DISABLED v9.4 (User Request: Remove Duplicate Card)
        /*
        TVList.importProgress.observe(this) { progress ->
            if (progress > 0) {
                 if (importProgressFragment.isHidden) {
                     supportFragmentManager.beginTransaction()
                         .show(importProgressFragment)
                         .commitAllowingStateLoss()
                 }
                
                 importProgressFragment.setProgress(progress)
                 
                 if (progress == 100) {
                      // Hide after delay
                      handler.postDelayed({ 
                          if (!importProgressFragment.isHidden) {
                              supportFragmentManager.beginTransaction()
                                  .hide(importProgressFragment)
                                  .commitAllowingStateLoss()
                              importProgressFragment.setProgress(0) // Reset
                          }
                      }, 2000)
                 }
            } else {
                 if (!importProgressFragment.isHidden) {
                     supportFragmentManager.beginTransaction()
                         .hide(importProgressFragment)
                         .commitAllowingStateLoss()
                 }
                 importProgressFragment.setProgress(0)
            }
        }
        
        TVList.importStatus.observe(this) { status ->
             importProgressFragment.setStatus(status)
        }
        */
    }

    private fun startPeriodicRefresh() {
        refreshHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!SecurityUtil.isAppOutdated && !isMaintenanceMode) {
                    val config = SP.config
                    if (!config.isNullOrEmpty() && config.startsWith("http")) {
                        TVList.update(this@MainActivity, config, silent = true)
                    }
                    lastRefreshTime = Utils.getDateTimestamp() * 1000L
                }
                refreshHandler.postDelayed(this, refreshInterval)
            }
        }, refreshInterval)
    }
    
    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = webFragment.isPlaying()
            // Check if PIP is enabled in settings
            if (isPlaying && !isMaintenanceMode && SP.pipMode) {
                // Check if we are in a state where PiP makes sense (e.g. not in settings)
                if (settingFragment.isHidden && menuFragment.isHidden) {
                    try {
                        val params = android.app.PictureInPictureParams.Builder()
                            //.setAspectRatio(Rational(16, 9)) // Optional: Set aspect ratio if known
                            .build()
                        enterPictureInPictureMode(params)
                    } catch (e: Exception) {
                        // PiP might not be supported or failed
                    }
                }
            }
        }
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        
        if (isInPictureInPictureMode) {
            // Hide UI elements
            watermarkContainer.visibility = View.GONE
            
            // Hide fragments if they are interfering
            if (infoFragment.isShowing()) infoFragment.dismiss()
            if (channelFragment.isShowing()) channelFragment.dismiss()
            
            // Hide other overlays
            loadingFragment.view?.visibility = View.GONE
            
        } else {
            // Restore UI elements
            updateWatermarkVisibility()
            
            // Logic Fix: Only restore loading fragment visibility if it is NOT logically hidden
            // (i.e., if the player is actually in a buffering/loading state)
            if (!loadingFragment.isHidden) {
                 loadingFragment.view?.visibility = View.VISIBLE
            }
            
            // No need to refresh playback as ExoPlayer/WebView handles full-screen transition
            // webFragment.refreshPlayback() // REASON: Prevents redundant loading spinner
        }
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
                if (!isMaintenanceMode) {
                    updateManager.checkAndUpdate()
                }
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

    private fun initRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600 // 0 for debug, 1 hour for prod
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // IMMEDIATE LOAD: Trigger update with cached config immediately
        val cachedConfig = SP.config
        if (!cachedConfig.isNullOrEmpty()) {
             TVList.update(this, silent = true)
        }
        
        // Set defaults
        val defaults = mapOf(
            "main_api_url" to TVList.DEFAULT_CONFIG_URL,
            SecretManager.getMaintenanceModeKey() to false
        )
        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                // Update dynamic URLs in SP
                val host = remoteConfig.getString("api_host")
                if (host.isNotBlank()) SP.apiHost = host
                
                val downloadHost = remoteConfig.getString("api_download_host")
                if (downloadHost.isNotBlank()) SP.apiDownloadHost = downloadHost
                
                val hostFallback = remoteConfig.getString("api_host_fallback")
                if (hostFallback.isNotBlank()) SP.apiHostFallback = hostFallback
                
                val downloadHostFallback = remoteConfig.getString("api_download_host_fallback")
                if (downloadHostFallback.isNotBlank()) SP.apiDownloadHostFallback = downloadHostFallback

                val apiUrl = remoteConfig.getString("main_api_url")
                
                isMaintenanceMode = remoteConfig.getBoolean(SecretManager.getMaintenanceModeKey())
                SecurityUtil.isMaintenanceMode = isMaintenanceMode
                if (isMaintenanceMode) {
                    onAppMaintenance()
                    return@addOnCompleteListener
                }

                if (apiUrl.isNotBlank()) {
                    val currentConfig = SP.config
                    if (apiUrl != currentConfig) {
                        Log.d(TAG, "Config URL changed: $apiUrl")
                        SP.config = apiUrl
                        SP.addPlaylistUrl(apiUrl)
                        
                        // ONLY trigger update if URL changed (The initRemoteConfig call handles the startup sync)
                        TVList.update(this, silent = true)
                    }
                }
            }
    }


    override fun onResume() {
        super.onResume()
        if (!SecurityUtil.isAppOutdated && !isMaintenanceMode) {
            // Check for refresh on resume
            val now = Utils.getDateTimestamp() * 1000L
            if (now - lastRefreshTime > resumeRefreshThreshold) {
                val config = SP.config
                if (!config.isNullOrEmpty() && config.startsWith("http")) {
                    handler.post {
                        TVList.update(this, silent = true)
                    }
                    lastRefreshTime = now
                }
            }
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        
        if (!isMaintenanceMode && server == null) {
            val port = PortUtil.findFreePort()
            if (port != -1) {
                server = SimpleServer(this, port)
            }
        }
    }

    fun ready(tag: String) {
    }

    fun setServer(server: String) {
        settingFragment.setServer(server)
        settingFragment.setVersionName(appVersionName)
    }

    fun playChannel(tvModel: TVModel) {
        if (isMaintenanceMode) return
        
        // Hide error and show loader
        hideErrorFragment()
        showFragment(loadingFragment)
        
        // Remove previous observers to avoid leaks/multi-triggers
        tvModel.errInfo.removeObservers(this)
        tvModel.ready.removeObservers(this)
        
        val currentPos = TVList.position.value ?: -1
        
        // Observe Error Info
        tvModel.errInfo.observe(this) { info: String? ->
            // Robust Verification: Ensure we only handle signals for the CURRENTLY selected channel instance
            if (info != null && tvModel == TVList.getTVModel()) {
                if (info == "" || info == "success" || info == "web ok") {
                    hideFragment(loadingFragment)
                    hideErrorFragment()
                    showFragment(webFragment)
                } else if (info.contains("Retrying", ignoreCase = true) || info.contains("Trying", ignoreCase = true)) {
                    // Don't hide webFragment during retries, just show the error overlay on top
                    hideFragment(loadingFragment)
                    showFragment(webFragment) 
                    showErrorFragment(info)
                } else {
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
             if (tvModel == TVList.getTVModel()) {
                hideFragment(loadingFragment)
            }
        }, "loader_timeout", SystemClock.uptimeMillis() + 6000)
    }

    private var observersSet = false
    private fun setupCollectionObservers() {
        if (observersSet) return // Avoid redundant setup
        
        observersSet = true
        
        // Take a snapshot to avoid ConcurrentModificationException during iteration
        val snapshot = com.codesrahul.exclusivetv.models.TVList.listModel.toList()
        
        snapshot.forEach { tvModel ->
            tvModel.like.removeObservers(this)
            tvModel.like.observe(this) { liked ->
                if (liked != null) {
                    val collectionModel = com.codesrahul.exclusivetv.models.TVList.groupModel.getTVListModel(0)
                    if (liked) {
                        collectionModel?.replaceTVModel(tvModel)
                    } else {
                        collectionModel?.removeTVModel(tvModel.tv.id)
                    }
                    com.codesrahul.exclusivetv.SP.setLike(tvModel.tv.id, liked)
                    
                    // Refresh menu if it's showing favorites or if we need to update hearts
                    if (!menuFragment.isHidden) {
                        handler.post { 
                            if (!menuFragment.isHidden) { // Re-check visibility
                                menuFragment.update() 
                            }
                        }
                    }
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

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var isLongPressActive = false
        private val audioRunnable = Runnable {
            if (isLongPressActive) {
                showAudioSelector()
                isLongPressActive = false 
            }
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val screenWidth = windowManager.defaultDisplay.width
            val screenHeight = windowManager.defaultDisplay.height
            val x = e.x
            val y = e.y
            
            // Close settings or audio track selector if they're open
            if (!settingFragment.isHidden) {
                hideSettingFragment()
                return true
            }
            
            if (!trackSelectionFragment.isHidden) {
                hideTrackSelectionFragment()
                return true
            }
            
            if (infoFragment.isShowing()) {
                infoFragment.dismiss()
                return true
            }
            
            if (!menuFragment.isHidden) {
                // If menu is open, let standard touch handling work (or close if outside)
                // For now, simple toggle behavior
                 hideMenuFragment()
                 return true
            }
            
            // 1. Left Side Tap (< 20% width) -> Channels & Categories
            if (x < screenWidth * 0.2) {
                showFragment(menuFragment)
                return true
            }
            
            // 2. Bottom Tap (> 80% height) -> Info Card
            if (y > screenHeight * 0.8) {
                val tvModel = TVList.getTVModel()
                if (tvModel != null) {
                    infoFragment.show(tvModel)
                }
                return true
            }

            // 3. Default: Main Menu (or do nothing if preferred, but user said "Tap Left... Opens Channels")
            // User request implies strict zones. 
            // "Tap outside channel management menu" implies closing it.
            
            // If we are here, no special zone was tapped. 
            // We can show the menu as a fallback or do nothing.
            // Let's stick to the specific requests.
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val screenWidth = windowManager.defaultDisplay.width
            val x = e.x
            
            // Double Tap Right Side (> 60% width) -> Settings
            if (x > screenWidth * 0.6) {
                showSetting()
                return true
            }
            return true
        }

        fun startTimedLongPress() {
            // Check if right side for Audio Menu
            // We don't have coordinates here easily without passing them.
            // But onTouchEvent sets isRightHalf.
            // Let's rely on that or simplify.
            // The onTouchEvent logic checked isRightHalf.
            
            isLongPressActive = true
            
            // Schedule 3-second callback for audio selector (Right Side Long Press)
            longPressHandler.postDelayed(audioRunnable, 3000)
            
            // Removing 5s settings callback as it clashes/is redundant with double tap
            // longPressHandler.postDelayed(settingsRunnable, 5000) 
        }

        fun cancelTimedLongPress() {
            isLongPressActive = false
            longPressHandler.removeCallbacks(audioRunnable)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val screenWidth = windowManager.defaultDisplay.width
            val screenHeight = windowManager.defaultDisplay.height
            val x = e1.x
            val y = e1.y
            
            // Top Middle Zone: Width 30%-70%, Height < 50%
            val isTopMiddle = x > screenWidth * 0.3 && x < screenWidth * 0.7 && y < screenHeight * 0.5
            
            if (isTopMiddle) {
                if (velocityY > 0) { // Swipe Down -> Previous Channel (Logic invert/standard check)
                     // Usually Swipe Up (velocityY < 0) is Next, Down is Prev
                     if (menuFragment.isHidden && settingFragment.isHidden) {
                        prev()
                        return true
                    }
                }
                if (velocityY < 0) { // Swipe Up -> Next Channel
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        next()
                        return true
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        private var currentToast: Toast? = null

        private fun adjustVolume(deltaY: Float) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val deltaVolume = (deltaY / 1000f) * maxVolume.toFloat() / windowManager.defaultDisplay.height

            var newVolume = currentVolume + deltaVolume
            if (newVolume < 0) {
                newVolume = 0F
            } else if (newVolume > maxVolume) {
                newVolume = maxVolume.toFloat()
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)

            // Cancel previous toast to prevent queue buildup
            currentToast?.cancel()
            currentToast = Toast.makeText(this@MainActivity, "Volume: ${newVolume.toInt()} / $maxVolume", Toast.LENGTH_SHORT)
            currentToast?.show()
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
            menuFragment.updateList(currentGroup)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            return false
        }
    }

    fun showOfflineScreen() {
        if (offlineFragment.isHidden) {
            showFragment(offlineFragment)
        }
    }

    fun hideOfflineScreen() {
        if (!offlineFragment.isHidden) {
            hideFragment(offlineFragment)
            webFragment.refreshPlayback()
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!fragment.isHidden) {
            return
        }

        if (supportFragmentManager.isStateSaved) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitAllowingStateLoss()

        when (fragment) {
            menuFragment -> {
                 menuActive()
                 hideFragment(timeFragment)
                 if (infoFragment.isShowing()) infoFragment.dismiss()
            }
            settingFragment -> {
                 settingActive()
                 hideFragment(timeFragment)
                 if (infoFragment.isShowing()) infoFragment.dismiss()
            }
            trackSelectionFragment -> trackSelectionActive()
            offlineFragment -> {
                 // Offline screen specific sound or logic if needed
            }
        }
    }

    private fun hideFragment(fragment: Fragment) {
        if (fragment.isHidden || supportFragmentManager.isStateSaved) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitAllowingStateLoss()
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!menuFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(menuFragment).commitAllowingStateLoss()
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
                supportFragmentManager.beginTransaction().hide(trackSelectionFragment).commitAllowingStateLoss()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isMaintenanceMode) return true // Block all keys
        
        // Reset ALL auto-hide timers on ANY key event before dispatching it to views
        if (!menuFragment.isHidden) menuActive()
        if (!settingFragment.isHidden) settingActive()
        if (!trackSelectionFragment.isHidden) trackSelectionActive()
        
        // Intercept Select/Enter for Info Card or Channel Switch when in Playback Mode (No menus visible)
        if (event.action == KeyEvent.ACTION_DOWN && 
           (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
             
             if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
                 if (channelFragment.isNumberEntering()) {
                     channelFragment.playNow()
                     return true
                 }
                 
                 if (infoFragment.isShowing()) {
                     infoFragment.dismiss()
                 } else {
                     val tvModel = TVList.getTVModel()
                     if (tvModel != null) {
                         infoFragment.show(tvModel)
                     }
                 }
                 return true // Consumed
             }
        }
        
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_G -> {
                    toggleEpgGrid()
                    return true
                }
            }
        }
        
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // Handle right arrow for audio track selector (3s hold) when all fragments are hidden
            if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        // Start 3-second timer on first press
                        isRightArrowPressed = true
                        rightArrowHandler.postDelayed(rightArrowHoldRunnable, 3000)
                    }
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    // Cancel timer on release
                    if (isRightArrowPressed) {
                        isRightArrowPressed = false
                        rightArrowHandler.removeCallbacks(rightArrowHoldRunnable)
                        
                        // Short Press: Toggle Info Card
                        if (infoFragment.isShowing()) {
                            infoFragment.dismiss()
                        } else {
                            val tvModel = TVList.getTVModel()
                            if (tvModel != null) {
                                infoFragment.show(tvModel)
                            }
                        }
                    }
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun toggleEpgGrid() {
        if (epgGridFragment.isHidden) {
            showEpgGrid()
        } else {
            hideEpgGrid()
        }
    }

    fun showEpgGrid() {
        if (supportFragmentManager.isStateSaved) return
        
        supportFragmentManager.beginTransaction()
            .hide(menuFragment)
            .hide(settingFragment)
            .hide(webFragment)
            .show(epgGridFragment)
            .commitAllowingStateLoss()
            
        showTime()
    }

    private fun hideEpgGrid() {
        if (supportFragmentManager.isStateSaved) return
        
        supportFragmentManager.beginTransaction()
            .hide(epgGridFragment)
            .show(webFragment)
            .commitAllowingStateLoss()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (isMaintenanceMode) return true // Block all touch
        
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

        if (!epgGridFragment.isHidden) {
            hideEpgGrid()
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
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .hide(menuFragment)
            .commitAllowingStateLoss()
        showTime()
    }

    private fun hideSettingFragment() {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .hide(settingFragment)
            .commitAllowingStateLoss()
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
        
        if (supportFragmentManager.isStateSaved) return
        
        supportFragmentManager.beginTransaction()
            .show(trackSelectionFragment)
            .commitAllowingStateLoss()
        trackSelectionActive()
    }

    private fun hideTrackSelectionFragment() {
        if (trackSelectionFragment.isHidden || supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .hide(trackSelectionFragment)
            .commitAllowingStateLoss()
    }

    private fun showErrorFragment(msg: String) {
        errorFragment.show(msg)
        if (!errorFragment.isHidden) {
            return
        }

        if (supportFragmentManager.isStateSaved) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(errorFragment)
            .commitAllowingStateLoss()
    }

    private fun hideErrorFragment() {
        errorFragment.show("hide")
        if (errorFragment.isHidden) {
            return
        }

        if (supportFragmentManager.isStateSaved) return

        supportFragmentManager.beginTransaction()
            .hide(errorFragment)
            .commitAllowingStateLoss()
    }

    fun onKey(keyCode: Int, event: KeyEvent?): Boolean {
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
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
                    if (channelFragment.isNumberEntering()) {
                        channelFragment.playNow()
                        return true
                    }
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
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
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
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
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
                if (!settingFragment.isHidden || !trackSelectionFragment.isHidden || !epgGridFragment.isHidden) {
                    return false
                }
                
                // 3. Handle right arrow for audio track selector (3s hold) when all fragments are hidden
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden) {
                    // Logic moved to dispatchKeyEvent()
                }
                
                return false
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden) {
                    showFragment(menuFragment)
                    return true
                }
                // Let fragments handle their own left navigation
                return false
            }


        }

        
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    webFragment.safeTogglePlayback()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    webFragment.safeSeekForward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    webFragment.safeSeekBackward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    next() // Utilize existing next() channel logic
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    prev() // Utilize existing prev() channel logic
                    return true
                }
            }
        }

        return false
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (android.content.Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrEmpty()) {
                handleVoiceSearch(query)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    // Permission granted, resume download/install
                    Toast.makeText(this, "Permission granted. Resuming update...", Toast.LENGTH_SHORT).show()
                    updateManager.resumeDownload()
                } else {
                    Toast.makeText(this, "Permission denied. Cannot install update.", Toast.LENGTH_SHORT).show()
                }
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
        handler.removeCallbacksAndMessages(null)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        rootHandler.removeCallbacksAndMessages(null)
        refreshHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        rightArrowHandler.removeCallbacksAndMessages(null)
        SP.removeOnSharedPreferenceChangeListener(spListener)
        server?.stop()
        updateManager.destroy()
        super.onDestroy()
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
                    Toast.makeText(this@MainActivity, "Rooted device detected. App cannot run.", Toast.LENGTH_LONG).show()
                    finishAffinity()
                }
                wasRooted = isRooted
                rootHandler.postDelayed(this, checkInterval)
            }
        })
    }

    override fun onForceUpdate() {
        SecurityUtil.isAppOutdated = true
        com.codesrahul.exclusivetv.models.TVList.clear()
        
        // Stop playback
        webFragment.stop()
        
        // Hide all fragments to prevent usage
        hideFragment(webFragment)
        hideFragment(menuFragment)
        hideFragment(settingFragment)
        hideFragment(trackSelectionFragment)
        
        Toast.makeText(this, "Update Required - Please update to continue", Toast.LENGTH_LONG).show()
    }

    private fun onAppMaintenance() {
        isMaintenanceMode = true
    SecurityUtil.isMaintenanceMode = true
        com.codesrahul.exclusivetv.models.TVList.clear()
        
        // Stop playback and server
        webFragment.stop()
        server?.stop()
        server = null
        
        // Show maintenance screen
        showFragment(maintenanceFragment)
        
        Toast.makeText(this, "System under maintenance", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
