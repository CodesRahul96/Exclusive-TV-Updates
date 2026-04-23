package com.codesrahul.exclusivetv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
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
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ProgressBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.codesrahul.exclusivetv.models.TVModel
import com.codesrahul.exclusivetv.RootCheckUtil
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : FragmentActivity(), UpdateManager.UpdateListener {

    internal var webFragment = WebFragment()
    internal var errorFragment = ErrorFragment()
    internal var loadingFragment = LoadingFragment()
    internal var infoFragment = InfoFragment()
    internal var channelFragment = ChannelFragment()
    internal var timeFragment = TimeFragment()
    internal var menuFragment = MenuFragment()
    internal var settingFragment = SettingFragment()
    internal var importProgressFragment = ImportProgressFragment()
    internal var trackSelectionFragment = TrackSelectionFragment()
    internal var epgGridFragment = EpgGridFragment()
    internal var offlineFragment = OfflineFragment()
    internal var maintenanceFragment = MaintenanceFragment()
    internal var loginFragment = LoginFragment()
    internal var searchFragment = SearchFragment() // [NEW]
    internal var onboardingFragment = OnboardingFragment() // [NEW]

    private val spListener = object : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(key: String) {
            if (isMaintenanceMode) return
            
            // Trigger Cloud Sync for persistent settings
            val syncKeys = listOf(
                "channel_reversal", "channel_num", "time", "boot_startup", 
                "repeat_info", "buffer_mode", SP.KEY_EPG_ENABLED, 
                "show_date_in_info", "watch_last", "force_high_quality",
                "pip_mode", "audio_stabilizer", "config_channel_check",
                "watermark_enabled", "watermark_opacity", "watermark_position", "epg_shift", "sleep_timer", SP.KEY_RESIZE_MODE
            )
            if (syncKeys.contains(key)) {
                SyncManager.syncUp()
            }

            if (key == SP.KEY_EPG) {
                if (SP.epgEnabled) {
                    runOnUiThread {
                        com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, silent = true)
                    }
                }
            } else if (key == SP.KEY_EPG_ENABLED) {
                runOnUiThread {
                    if (SP.epgEnabled) {
                        com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, silent = true)
                    } else {
                        // Silently refresh UI models to clear EPG data from view
                        com.codesrahul.exclusivetv.models.TVList.refreshModels(this@MainActivity)
                    }
                }
            }
        }
    }

    private var isMaintenanceMode = false
    private var isFirstPlaybackTriggered = false // Professional flag to prevent startup races

    private lateinit var updateManager: UpdateManager
    
    // Watermark views
    private lateinit var watermarkContainer: FrameLayout
    private lateinit var tvWatermark: TextView



    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideMenu = 15 * 1000L
    private val delayHideSetting = 30 * 1000L
    private val delayHideTrackSelection = 30 * 1000L
    
    // Right arrow key hold tracking for Settings Menu
    private val rightArrowHandler = Handler(Looper.getMainLooper())
    private var isRightArrowPressed = false
    private val rightArrowHoldRunnable = Runnable {
        if (isRightArrowPressed) {
            showSetting()
            isRightArrowPressed = false
        }
    }

    // [NEW] Search trigger hold tracking
    private val searchHoldHandler = Handler(Looper.getMainLooper())
    private var isSearchPressed = false
    private val searchHoldRunnable = Runnable {
        if (isSearchPressed) {
            isSearchPressed = false
            showSearchFragment()
            if (SP.voiceSearch) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!searchFragment.isHidden) {
                        Log.d("EXCL_VOICE", "Triggering voice search via Select Hold")
                        searchFragment.triggerVoiceSearch()
                    }
                }, 400)
            }
        }
    }

    // Left arrow key hold tracking for Channel Search (TV Only - 2s)
    // Periodic Monitoring Jobs (Modern Architecture)
    private var coreHealthJob: kotlinx.coroutines.Job? = null
    private var leftArrowHoldJob: kotlinx.coroutines.Job? = null
    private var bootstrapWatchdogJob: kotlinx.coroutines.Job? = null
    private var isLeftArrowPressed = false
    private val updateCheckInterval: Long = 15 * 60 * 1000 // 15 minutes

    private var doubleBackToExitPressedOnce = false

    lateinit var gestureListener: GestureListener
    lateinit var gestureDetector: GestureDetector

    // Gesture HUD Views
    private lateinit var gestureHudRoot: View
    private lateinit var hudBrightness: View
    private lateinit var brightnessValue: TextView
    private lateinit var brightnessProgress: ProgressBar
    private lateinit var hudVolume: View
    private lateinit var volumeValue: TextView
    private lateinit var volumeProgress: ProgressBar
    private lateinit var hudSeek: View
    private lateinit var seekDelta: TextView
    private lateinit var seekTime: TextView
    private lateinit var seekProgress: ProgressBar
    private lateinit var hudSpeed: TextView
    private lateinit var hudChannelChange: View
    private lateinit var channelChangeArrow: TextView
    private lateinit var channelChangeLabel: TextView

    // Gesture State
    private var isScrolling = false
    private var scrollSeekDelta = 0L
    private var scrollBasePosition = 0L
    private var scrollBaseVolume = 0f
    private var scrollBaseBrightness = 0f

    private var server: SimpleServer? = null

    private var wasRooted = false
    private val securityCheckInterval: Long = 60000 // 60 seconds (optimized)

    private var lastRefreshTime = 0L
    private val refreshInterval: Long = 30 * 60 * 1000L // 30 minutes
    private val resumeRefreshThreshold: Long = 60 * 1000L // 1 minute
    
    // Live Background Sync Heartbeat
    private var lastDataSyncTime: Long = 0L
    private val dataSyncInterval: Long = 30 * 60 * 1000L // 30 minutes (Professional Default)

    private fun isAddedToContext(): Boolean {
        // Simple check to see if we can still show/hide fragments
        return !supportFragmentManager.isDestroyed && !supportFragmentManager.isStateSaved
    }

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
        SP.setOnSharedPreferenceChangeListener(spListener)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemUI()
        
        initBasicSetup(savedInstanceState)
        
        // Phase 2: Start Professional Bootstrap (Async but Synchronized)
        updateManager = UpdateManager(this, com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE)
        bootstrap()

        if (!isTvDevice()) {
            infoFragment.setOnChannelClickListener {
                channelFragment.showKeypad()
            }
        }
    }

    private fun initBasicSetup(savedInstanceState: Bundle?) {
        // Initial setup
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        
        // Hide System UI after view creation
        try {
            hideSystemUI()
        } catch (e: Exception) {
        }

        // Initialize Gestures
        gestureListener = GestureListener()
        gestureDetector = GestureDetector(this, gestureListener)
        
        // Initialize HUD
        gestureHudRoot = findViewById(R.id.gesture_hud_root)
        hudBrightness = findViewById(R.id.hud_brightness)
        brightnessValue = findViewById(R.id.brightness_value)
        brightnessProgress = findViewById(R.id.brightness_progress)
        hudVolume = findViewById(R.id.hud_volume)
        volumeValue = findViewById(R.id.volume_value)
        volumeProgress = findViewById(R.id.volume_progress)
        hudSeek = findViewById(R.id.hud_seek)
        seekDelta = findViewById(R.id.seek_delta)
        seekTime = findViewById(R.id.seek_time)
        seekProgress = findViewById(R.id.seek_progress)
        hudSpeed = findViewById(R.id.hud_speed)
        hudChannelChange = findViewById(R.id.hud_channel_change)
        channelChangeArrow = findViewById(R.id.channel_change_arrow)
        channelChangeLabel = findViewById(R.id.channel_change_label)

        if (savedInstanceState != null) {
            // Restore fragment references from FragmentManager after recreation
            supportFragmentManager.findFragmentByTag("web")?.let { webFragment = it as WebFragment }
            supportFragmentManager.findFragmentByTag("error")?.let { errorFragment = it as ErrorFragment }
            supportFragmentManager.findFragmentByTag("loading")?.let { loadingFragment = it as LoadingFragment }
            supportFragmentManager.findFragmentByTag("time")?.let { timeFragment = it as TimeFragment }
            supportFragmentManager.findFragmentByTag("info")?.let { infoFragment = it as InfoFragment }
            supportFragmentManager.findFragmentByTag("epg")?.let { epgGridFragment = it as EpgGridFragment }
            supportFragmentManager.findFragmentByTag("channel")?.let { channelFragment = it as ChannelFragment }
            supportFragmentManager.findFragmentByTag("menu")?.let { menuFragment = it as MenuFragment }
            supportFragmentManager.findFragmentByTag("setting")?.let { settingFragment = it as SettingFragment }
            supportFragmentManager.findFragmentByTag("import")?.let { importProgressFragment = it as ImportProgressFragment }
            supportFragmentManager.findFragmentByTag("track")?.let { trackSelectionFragment = it as TrackSelectionFragment }
            supportFragmentManager.findFragmentByTag("offline")?.let { offlineFragment = it as OfflineFragment }
            supportFragmentManager.findFragmentByTag("maintenance")?.let { maintenanceFragment = it as MaintenanceFragment }
            supportFragmentManager.findFragmentByTag("login")?.let { loginFragment = it as LoginFragment }
            supportFragmentManager.findFragmentByTag("search")?.let { searchFragment = it as SearchFragment }
            supportFragmentManager.findFragmentByTag("onboarding")?.let { onboardingFragment = it as OnboardingFragment }
        }

        if (savedInstanceState == null) {
            val transaction = supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, webFragment, "web")
                .add(R.id.main_browse_fragment, errorFragment, "error")
                .add(R.id.main_browse_fragment, loadingFragment, "loading")
                .add(R.id.main_browse_fragment, timeFragment, "time")
                .add(R.id.main_browse_fragment, infoFragment, "info")
                .add(R.id.main_browse_fragment, epgGridFragment, "epg")
                .add(R.id.main_browse_fragment, channelFragment, "channel")
                .add(R.id.main_browse_fragment, menuFragment, "menu")
                .add(R.id.main_browse_fragment, settingFragment, "setting")
                .add(R.id.main_browse_fragment, importProgressFragment, "import")
                .add(R.id.main_browse_fragment, trackSelectionFragment, "track")
                .add(R.id.main_browse_fragment, offlineFragment, "offline")
                .add(R.id.main_browse_fragment, maintenanceFragment, "maintenance")
                .add(R.id.main_browse_fragment, loginFragment, "login")
                .add(R.id.main_browse_fragment, searchFragment, "search")
                .add(R.id.onboarding_container, onboardingFragment, "onboarding")
                .hide(menuFragment).hide(settingFragment).hide(importProgressFragment)
                .hide(trackSelectionFragment).hide(offlineFragment).hide(maintenanceFragment)
                .hide(epgGridFragment).hide(errorFragment).hide(timeFragment)
                .hide(webFragment).hide(searchFragment).hide(onboardingFragment)

            // Smart Startup: Check Login Status
            if (SP.userId != null) {
                // Logged in: Show Loading (Bootstrap will handle the rest)
                transaction.hide(loginFragment).show(loadingFragment)
            } else {
                // Not Logged in: Show Login
                transaction.show(loginFragment).hide(loadingFragment)
            }
            
            transaction.commit()
        }

        setupWatermark()
        setupObservers()
        startRootMonitoring()
        CoroutineScope(Dispatchers.IO).launch { Utils.init() }
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                hideSystemUI()
            } catch (e: Exception) {
                // Ignore errors during focus change
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun bootstrap(skipSubCheck: Boolean = false) {
        showFragment(loadingFragment)
        
        // Step 0: Play Integrity Check (Phase 4 Security)
        checkIntegrity { 
            // Continue with normal bootstrap after integrity check
            startBootstrapSequence(skipSubCheck)
        }
    }

    private fun startBootstrapSequence(skipSubCheck: Boolean) {
        // [PROFESSIONAL] Start 45s safety watchdog using Coroutines
        bootstrapWatchdogJob?.cancel()
        bootstrapWatchdogJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(45000)
            if (isActive && !isFinishing && loadingFragment.isAdded && loadingFragment.isVisible) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network is slow. Attempting to load cached content...", Toast.LENGTH_LONG).show()
                    if (SP.userId != null) onBootstrapComplete() 
                    else {
                        showFragment(loginFragment)
                        hideFragment(loadingFragment)
                    }
                }
            }
        }
        
        // Step 1: Initialize Remote Config
        initRemoteConfig { 
            if (skipSubCheck) {
                 SyncManager.syncDown {
                     runOnUiThread { onBootstrapComplete() }
                 }
            } else {
                // Step 2: Check Auth & Subscription (Only after config is fetched)
                checkAuthAndSubscription { 
                    // Step 3: Trigger Final Data Load (Only after plan is known)
                    runOnUiThread {
                        onBootstrapComplete()
                    }
                }
            }
        }
    }

    private fun onBootstrapComplete() {
        bootstrapWatchdogJob?.cancel()
        if (!loadingFragment.isVisible && !loginFragment.isVisible) return // Already handled or done

        // Transition to Landscape upon successful login/bootstrap on mobile
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        hideFragment(loginFragment)
        com.codesrahul.exclusivetv.models.TVList.update(this, silent = true, force = true)
        
        // Background Auto-Update Check
        if (::updateManager.isInitialized) {
            updateManager.checkAndUpdate()
        }

        // --- SMART BACKGROUND SYNC ---
        scheduleBackgroundSync()
        
        // [PROFESSIONAL] Initial playback is now handled centrally by setupObservers() 
        // to ensure zero race conditions between data loading and fragment attachment.
        // We set position to -1 to guarantee the first legitimate update triggers playback.
        if (com.codesrahul.exclusivetv.models.TVList.listModel.isEmpty()) {
            com.codesrahul.exclusivetv.models.TVList.setPosition(-1)
            isFirstPlaybackTriggered = false
        }

        // Trigger Onboarding for new users
        if (!SP.hasCompletedOnboarding) {
            showOnboarding()
        }
    }

    fun showOnboarding() {
        showFragment(onboardingFragment)
        onboardingFragment.resetAndShow()
    }

    fun hideOnboarding() {
        hideFragment(onboardingFragment)
    }

    private fun checkIntegrity(onComplete: () -> Unit) {
        // Only run integrity check in production or if enabled via remote config
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val integrityEnabled = remoteConfig.getBoolean("integrity_enabled")
        
        if (!integrityEnabled && !BuildConfig.DEBUG) {
            onComplete()
            return
        }

        val integrityManager = IntegrityManagerFactory.create(applicationContext)
        
        // Use a random nonce for security
        val nonce = java.util.UUID.randomUUID().toString()
        
        val integrityTokenRequest = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()
            
        integrityManager.requestIntegrityToken(integrityTokenRequest)
            .addOnSuccessListener { response ->
                val integrityToken = response.token()
                // Store or send this token to your backend for verification
                // SP.integrityToken = integrityToken 
                onComplete()
            }
            .addOnFailureListener { e ->
                if (!BuildConfig.DEBUG) {
                    // In production, you might want to block access if integrity fails
                    // showFragment(errorFragment.apply { setMessage("Security check failed. Please ensure you are using the official version.") })
                    onComplete() // For now, proceed anyway to avoid blocking users during rollout
                } else {
                    onComplete()
                }
            }
    }

    private fun checkAuthAndSubscription(onFinished: () -> Unit) {
        val currentUser = SP.userId
        
        if (currentUser == null) {
            runOnUiThread {
                webFragment.stop()
                showFragment(loginFragment)
                hideFragment(loadingFragment)
            }
        } else {
            runOnUiThread {
                showFragment(loadingFragment)
                hideFragment(loginFragment)
            }
            SubscriptionManager.checkSubscription(
                onSuccess = { 
                    // [NEW] Cloud Sync: Fetch favorites and custom sources
                    SyncManager.syncDown {
                        runOnUiThread { onFinished() } 
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        val isHardError = error.contains("[AUTH_ERR]")
                        val cleanError = error.replace("[AUTH_ERR]", "").replace("[NET_ERR]", "").trim()
                        
                        Toast.makeText(this@MainActivity, cleanError, Toast.LENGTH_LONG).show()
                        
                        if (isHardError) {
                            // Hard Security Error (Banned, Limit Reached): Force Logout
                            webFragment.stop() 
                            showFragment(loginFragment)
                            hideFragment(loadingFragment)
                        } else {
                            // Soft Network Error: Allow graceful fallback to main UI if already logged in
                            Log.d("AUTH", "Transient auth error: $cleanError. Allowing session to continue.")
                            onFinished() // Proceed to data load/bootstrap completion
                        }
                    }
                },
                onDowngrade = { message ->
                    runOnUiThread {
                        showDowngradeDialog(message)
                    }
                },
                onTrialInfo = { daysLeft ->
                    runOnUiThread {
                        if (daysLeft > 0) {
                            Toast.makeText(this@MainActivity, "Premium Trial: $daysLeft days remaining!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Premium Trial: Last day! Enjoy!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    fun onLoginSuccess() {
        // Full re-bootstrap on manual login - skip sub check as it's just been verified
        bootstrap(skipSubCheck = true)
    }

    private fun setupObservers() {
        
        // 1. Observe Group Changes (Initial Load or Update)
        TVList.groupModel.change.observe(this) { _ ->
            val currentGroup = TVList.groupModel.tvGroupModel.value
            if (currentGroup != null) {
                val currentPlayingUrl = webFragment.getCurrentUrl() ?: ""
                val pos = TVList.position.value ?: -1
                
                if ((pos == -1 || pos == 0) && !isFirstPlaybackTriggered) {
                    // Initial playback on load (Professional approach: only trigger once per cold start)
                    val targetPos = if (SP.watchLast) com.codesrahul.exclusivetv.models.TVList.restorePosition() else if (SP.channel > 0) SP.channel - 1 else 0
                    
                    // Verify data existence before committing to first play
                    if (com.codesrahul.exclusivetv.models.TVList.getTVModel(targetPos) != null) {
                        isFirstPlaybackTriggered = true
                        com.codesrahul.exclusivetv.models.TVList.setPosition(targetPos)
                    } else if (com.codesrahul.exclusivetv.models.TVList.listModel.isNotEmpty()) {
                        isFirstPlaybackTriggered = true
                        com.codesrahul.exclusivetv.models.TVList.setPosition(0)
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
                    // No video playing or fresh state, but verify URL before restarting
                    TVList.getTVModel(pos)?.let { model ->
                        val currentUrl = webFragment.getCurrentUrl() ?: ""
                        val targetUrl = model.tv.uris.firstOrNull() ?: ""
                        if (targetUrl != currentUrl || currentUrl.isEmpty()) {
                             playChannel(model)
                        }
                    }
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

    private fun initRemoteConfig(onComplete: () -> Unit) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600 // 0 for debug, 1 hour for prod
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set defaults
        val defaults = mapOf(
            "standard_api_url" to "", // Will be gracefully handled by TVList
            "premium_api_url" to SecretManager.getPremiumApiUrl(),
            SecretManager.getMaintenanceModeKey() to false,
            "registration_enabled" to true,  // Controls new user sign-up (ON by default)
            "portal_profiles" to ""         // Managed dynamically for high-security CDNs
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

                // --- SCHEDULE BACKGROUND SYNC AFTER CONFIG UPDATE ---
                // This ensures that if the server interval changes, we reschedule
                scheduleBackgroundSync()

                // Fetch EPG URL
                val remoteEpgUrl = remoteConfig.getString("epg_url")
                if (remoteEpgUrl.isNotBlank()) {
                    SP.remoteEpgUrl = remoteEpgUrl
                }

                // Fetch Tiered Configs (Encrypted from Firebase)
                val rawStandardUrl = remoteConfig.getString("standard_api_url")
                val rawPremiumUrl = remoteConfig.getString("premium_api_url")
                
                val key = SecretManager.getAppKey()
                
                if (rawStandardUrl.isNotBlank()) {
                    val decryptedStandard = SecurityUtil.decryptChannelData(rawStandardUrl, key)
                    SP.standardConfig = decryptedStandard
                }
                
                if (rawPremiumUrl.isNotBlank()) {
                    val decryptedPremium = SecurityUtil.decryptChannelData(rawPremiumUrl, key)
                    SP.premiumConfig = decryptedPremium
                }
                
                isMaintenanceMode = remoteConfig.getBoolean(SecretManager.getMaintenanceModeKey())
                SecurityUtil.isMaintenanceMode = isMaintenanceMode
                if (isMaintenanceMode) {
                    onAppMaintenance()
                    return@addOnCompleteListener
                }

                // AUTO-UPDATE CERT PINS: Fetch remote SSL pins and refresh OkHttpClient
                // If Let's Encrypt rotates its intermediate CA, update "ssl_pins_indevs" in Firebase
                // and all app instances will pick up the new pins on next startup without an APK update.
                val remoteSslPins = remoteConfig.getString("ssl_pins_indevs")
                val remoteSslPinsGithub = remoteConfig.getString("ssl_pins_github")
                val remoteSslPinsVercel = remoteConfig.getString("ssl_pins_vercel")
                
                var pinsUpdated = false
                
                if (remoteSslPins.isNotBlank()) {
                    SP.sslPinsIndevs = remoteSslPins
                    pinsUpdated = true
                }
                if (remoteSslPinsGithub.isNotBlank()) {
                    SP.sslPinsGithub = remoteSslPinsGithub
                    pinsUpdated = true
                }
                if (remoteSslPinsVercel.isNotBlank()) {
                    SP.sslPinsVercel = remoteSslPinsVercel
                    pinsUpdated = true
                }
                
                if (pinsUpdated) {
                    // BUG FIX: OkHttpClient construction (thread pool + connection pool allocation)
                    // must NOT run on the main thread. Dispatch to IO.
                    CoroutineScope(Dispatchers.IO).launch {
                        SecureHttpClient.refresh()
                    }
                }

                // Apply registration gate from Remote Config
                SP.registrationEnabled = remoteConfig.getBoolean("registration_enabled")

                // Update dynamic security profiles (Zero-Hardcoding Logic)
                OptimizationManager.loadProfiles(remoteConfig.getString("portal_profiles"))

                // CRITICAL: Call the callback to advance the bootstrap
                onComplete()
            }
    }


    override fun onResume() {
        super.onResume()
        startCoreHealthMonitoring()
        
        if (!SecurityUtil.isAppOutdated && !isMaintenanceMode) {
            val now = Utils.getDateTimestamp() * 1000L
            if (now - lastRefreshTime > resumeRefreshThreshold) {
                if (!SP.standardConfig.isNullOrEmpty() || !SP.premiumConfig.isNullOrEmpty()) {
                    TVList.update(this, silent = true, force = true)
                    lastRefreshTime = now
                }
            }
        }
    }

    override fun onPause() {
        stopCoreHealthMonitoring()
        super.onPause()
    }

    private fun startCoreHealthMonitoring() {
        coreHealthJob?.cancel()
        coreHealthJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    // 1. Root Security Check
                    val isRooted = RootCheckUtil.isDeviceRooted(this@MainActivity)
                    if (isRooted && !wasRooted) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Rooted device detected. App cannot run.", Toast.LENGTH_LONG).show()
                            finishAffinity()
                        }
                    }
                    wasRooted = isRooted

                    // 2. Force Update / Maintenance Checks (Every 15 mins)
                    // We check security state which is updated by SubscriptionManager/CloudTV
                    if (SecurityUtil.isAppOutdated && !isFinishing) {
                        runOnUiThread { onForceUpdate() }
                    }
                    if (SecurityUtil.isMaintenanceMode && !isFinishing) {
                        runOnUiThread { onAppMaintenance() }
                    }

                    // 3. Live Data Sync Heartbeat (Silent Refresh)
                    val now = System.currentTimeMillis()
                    if (now - lastDataSyncTime >= dataSyncInterval) {
                        lastDataSyncTime = now
                        withContext(Dispatchers.Main) {
                            com.codesrahul.exclusivetv.models.TVList.update(this@MainActivity, silent = true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Health check error", e)
                }
                kotlinx.coroutines.delay(securityCheckInterval)
            }
        }
    }

    private fun stopCoreHealthMonitoring() {
        coreHealthJob?.cancel()
        coreHealthJob = null
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
        if (!loginFragment.isHidden) return // Prevent playback behind login screen
        
        // --- NOW PLAYING TRACKING ---
        TVList.updatePlaybackModel(tvModel)
        
        // Hide error and show loader
        hideErrorFragment()
        showFragment(loadingFragment)
        hideSearchFragment()
        
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
        val url = tvModel.videoUrl.value ?: ""
        if (url.isNotEmpty()) {
            SP.addRecentlyWatched(url)
        }

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
        
        // Single centralized observer for ALL channel "like" changes
        com.codesrahul.exclusivetv.models.TVList.likeChangedEvent.observe(this) { (tvModel, liked) ->
            if (tvModel != null) {
                val collectionModel = com.codesrahul.exclusivetv.models.TVList.groupModel.getTVListModel(0)
                if (liked) {
                    collectionModel?.replaceTVModel(tvModel)
                } else {
                    collectionModel?.removeTVModel(tvModel.tv.id)
                }
                
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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            val screenWidth = windowManager.defaultDisplay.width
            val isRightHalf = event.x > screenWidth / 2
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && loginFragment.isHidden) {
                        gestureListener.startTimedLongPress(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gestureListener.cancelTimedLongPress()
                    gestureListener.onScrollEnd()
                }
            }
            
            if (loginFragment.isHidden) {
                gestureDetector.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        private val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var isLongPressActive = false
        /* Commented out mobile audio track long-press - re-enable if requested
        private val audioRunnable = Runnable {
            if (isLongPressActive) {
                showAudioSelector()
                isLongPressActive = false 
            }
        }
        */

        private val settingsRunnable = Runnable {
            if (isLongPressActive) {
                showSetting()
                isLongPressActive = false
            }
        }

        // [NEW] Mobile center hold search trigger (3s)
        private val mobileSearchRunnable = Runnable {
            if (isLongPressActive) {
                showSearchFragment()
                isLongPressActive = false
            }
        }

        private val menuHoldRunnable = Runnable {
            if (isLongPressActive) {
                showFragment(menuFragment)
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
            
            // 1. Left Side Tap (< 25% width) -> Channels & Categories
            if (x < screenWidth * 0.25) {
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
            // 1. Left Side Double Tap -> Channels
            if (e.x < screenWidth * 0.25) {
                showFragment(menuFragment)
                return true
            }
            // 2. Right side Double Tap -> Settings
            if (e.x > screenWidth * 0.75) {
                showSetting()
                return true
            }
            return false
        }

        fun startTimedLongPress(x: Float, y: Float) {
            val screenWidth = windowManager.defaultDisplay.width
            isLongPressActive = true
            
            if (x < screenWidth * 0.25) {
                // Left side -> Channel Management (3s hold)
                longPressHandler.postDelayed(menuHoldRunnable, 3000)
            } else if (x > screenWidth * 0.75) {
                // Right side -> Settings Menu (3s hold)
                longPressHandler.postDelayed(settingsRunnable, 3000)
            } else if (x > screenWidth * 0.3 && x < screenWidth * 0.7) {
                // Center -> Search (1.5s)
                longPressHandler.postDelayed(mobileSearchRunnable, 1500)
            }
        }

        fun cancelTimedLongPress() {
            if (isLongPressActive) {
            }
            isLongPressActive = false
            longPressHandler.removeCallbacks(settingsRunnable)
            longPressHandler.removeCallbacks(menuHoldRunnable)
            longPressHandler.removeCallbacks(mobileSearchRunnable)
        }
        // showSeekHud moved below GestureListener


        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || isScrolling) return false
            
            val screenWidth = windowManager.defaultDisplay.width
            val screenHeight = windowManager.defaultDisplay.height
            val x = e1.x
            val y = e1.y
            
            // Top Middle Zone: Swipe for Volume/Brightness is handled by onScroll now.
            // Fling for Channel Change only if not scrolling.
            
            val isMiddle = x > screenWidth * 0.3 && x < screenWidth * 0.7
            
            if (isMiddle) {
                if (velocityY > 2000) { 
                     if (menuFragment.isHidden && settingFragment.isHidden) {
                        prev()
                        showChannelChangeHud(true) 
                        return true
                    }
                }
                if (velocityY < -2000) { 
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        next()
                        showChannelChangeHud(false) 
                        return true
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val screenWidth = windowManager.defaultDisplay.width
            val screenHeight = windowManager.defaultDisplay.height
            
            if (!isScrolling) {
                hideAllHud()
                gestureHudRoot.visibility = View.VISIBLE
                isScrolling = true
                scrollBasePosition = webFragment.getCurrentPosition()
                scrollBaseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                
                val lp = window.attributes
                scrollBaseBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
            }

            val x = e1.x
            
            // 1. Vertical Swipe on Left 30% -> Brightness
            if (x < screenWidth * 0.3) {
                adjustBrightness(distanceY)
                return true
            }
            
            // 2. Vertical Swipe on Right 30% -> Volume
            if (x > screenWidth * 0.7) {
                adjustVolume(distanceY)
                return true
            }

            // 3. Horizontal Swipe on Center 40% -> Seek
            if (x > screenWidth * 0.3 && x < screenWidth * 0.7) {
                if (Math.abs(distanceX) > Math.abs(distanceY)) {
                    adjustSeek(distanceX)
                    return true
                }
            }
            
            return true
        }

        private fun adjustSeek(deltaX: Float) {
            val screenWidth = windowManager.defaultDisplay.width
            val duration = webFragment.getDuration()
            if (duration <= 0) return // Cannot seek if no duration (live stream without buffer)

            // Total swipe of screen width = 5 minutes (300,000 ms)
            // But let's limit it to duration if it's shorter
            val maxSeekRange = Math.min(300000L, duration).toFloat()
            val seekScale = maxSeekRange / screenWidth
            
            scrollSeekDelta -= (deltaX * seekScale).toLong()
            showSeekHud(scrollSeekDelta)
        }

        fun onScrollEnd() {
            if (isScrolling) {
                if (scrollSeekDelta != 0L) {
                    webFragment.seekRelative(scrollSeekDelta)
                }
                isScrolling = false
                scrollSeekDelta = 0
                
                // Hide HUD after a short delay
                longPressHandler.postDelayed({
                    if (!isScrolling) {
                        gestureHudRoot.visibility = View.GONE
                        hideAllHud()
                    }
                }, 1000)
            }
        }

        private fun hideAllHud() {
            this@MainActivity.hideAllHud()
        }

        private fun adjustBrightness(deltaY: Float) {
            val lp = window.attributes
            val screenHeight = windowManager.defaultDisplay.height
            
            // Total swipe of screen height = 100% brightness change
            val delta = deltaY / screenHeight 
            val newBrightness = (scrollBaseBrightness + delta).coerceIn(0.01f, 1.0f)
            
            // Update base for next scroll delta if we want it relative or just rely on total delta
            // Actually distanceY in onScroll is delta since last call. 
            // My scrollBaseBrightness is from the START of the scroll.
            // So I should accumulate deltaY into a scrollTotalDeltaY.
            // Wait, onScroll distanceY is indeed delta since LAST call.
            // So I should update scrollBaseBrightness continuously or accumulate.
            
            scrollBaseBrightness = newBrightness
            
            lp.screenBrightness = newBrightness
            window.attributes = lp
            
            hudBrightness.visibility = View.VISIBLE
            brightnessValue.text = "${(newBrightness * 100).toInt()}%"
            brightnessProgress.progress = (newBrightness * 100).toInt()
        }

        fun resetHudTimeout() {
            longPressHandler.removeCallbacksAndMessages(null)
            longPressHandler.postDelayed({
                if (!isScrolling) {
                    gestureHudRoot.visibility = View.GONE
                    hideAllHud()
                }
            }, 1000)
        }


        private var currentToast: Toast? = null

        private fun adjustVolume(deltaY: Float) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val screenHeight = windowManager.defaultDisplay.height
            
            // Total swipe of screen height = 100% volume change
            val deltaVolume = (deltaY / screenHeight) * maxVolume.toFloat()
            val newVolume = (scrollBaseVolume + deltaVolume).coerceIn(0f, maxVolume.toFloat())
            
            scrollBaseVolume = newVolume
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)
            
            hudVolume.visibility = View.VISIBLE
            volumeValue.text = "${(newVolume / maxVolume * 100).toInt()}%"
            volumeProgress.progress = (newVolume / maxVolume * 100).toInt()
        }
    }

    private fun showSeekHud(deltaMs: Long) {
        val duration = webFragment.getDuration()
        val currentPos = webFragment.getCurrentPosition()
        
        gestureHudRoot.visibility = View.VISIBLE
        hideAllHud()
        hudSeek.visibility = View.VISIBLE
        seekDelta.text = (if (deltaMs >= 0) "+" else "-") + formatTime(Math.abs(deltaMs))
        seekTime.text = "${formatTime(currentPos)} / ${formatTime(duration)}"
        
        if (duration > 0) {
            seekProgress.progress = (currentPos * 100 / duration).toInt()
            seekProgress.visibility = View.VISIBLE
        } else {
            seekProgress.visibility = View.GONE
        }
        
        gestureListener.resetHudTimeout()
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun hideAllHud() {
        hudBrightness.visibility = View.GONE
        hudVolume.visibility = View.GONE
        hudSeek.visibility = View.GONE
        hudSpeed.visibility = View.GONE
        hudChannelChange.visibility = View.GONE
    }

    private fun showChannelChangeHud(isUp: Boolean) {
        val tvModel = TVList.getTVModel() ?: return
        gestureHudRoot.visibility = View.VISIBLE
        hideAllHud()
        hudChannelChange.visibility = View.VISIBLE
        channelChangeArrow.text = if (isUp) "▲" else "▼"
        channelChangeLabel.text = tvModel.tv.name
        
        gestureListener.resetHudTimeout()
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
            Toast.makeText(this, "Channel ${position + 1} does not exist", Toast.LENGTH_LONG).show()
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

    private var offlineDebounceJob: kotlinx.coroutines.Job? = null

    fun showOfflineScreen() {
        // [PROFESSIONAL] Async debounce to prevent flickering during network switching
        offlineDebounceJob?.cancel()
        offlineDebounceJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            if (!isNetworkAvailable() && offlineFragment.isHidden) {
                showFragment(offlineFragment)
            }
        }
    }

    fun hideOfflineScreen() {
        offlineDebounceJob?.cancel()
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

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // [CRITICAL] Alexa/Search Button Integration: Block Alexa only if we have a native engine
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH || 
            event.keyCode == 219 || // KEYCODE_ASSIST (Alexa)
            event.keyCode == 231) { // KEYCODE_VOICE_SEARCH
            
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d("EXCL_KEYS", "Alexa/Voice Button Pressed - Intercepting for App-Exclusive Search")
                
                showSearchFragment()
                
                // If native recognition is available, use it internally.
                // Otherwise, the fragment will handle the system dialog fallback.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!searchFragment.isHidden) {
                        searchFragment.triggerVoiceSearch()
                    }
                }, 300)
            }
            return true // [CRITICAL] Block FireTV system search results screen
        }

        Log.d("EXCL_KEYS", "Key: ${event.keyCode}, Action: ${event.action}")
        if (isMaintenanceMode) return true // Block all keys

        // If login screen is shown, let it handle keys (numeric entry, navigation, etc.)
        if (!loginFragment.isHidden) {
            return try {
                super.dispatchKeyEvent(event)
            } catch (e: IllegalStateException) {
                Log.e("EXCL_KEYS", "Focus search failed during login key dispatch: ${e.message}")
                false
            }
        }
        
        // Reset ALL auto-hide timers on ANY key event before dispatching it to views
        if (!menuFragment.isHidden) menuActive()
        if (!settingFragment.isHidden) settingActive()
        if (!trackSelectionFragment.isHidden) trackSelectionActive()
        
        // Intercept Select/Enter for Info Card or Channel Switch when in Playback Mode (No menus visible)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            // Only toggle Info/Search if NO overlay menu is showing
            if (menuFragment.isHidden && settingFragment.isHidden && 
                trackSelectionFragment.isHidden && epgGridFragment.isHidden && 
                searchFragment.isHidden && loginFragment.isHidden && 
                loadingFragment.isHidden && maintenanceFragment.isHidden && 
                offlineFragment.isHidden && onboardingFragment.isHidden) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        isSearchPressed = true
                        searchHoldHandler.postDelayed(searchHoldRunnable, 1500)
                    }
                    if (channelFragment.isNumberEntering()) {
                        channelFragment.playNow()
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (isSearchPressed) {
                        isSearchPressed = false
                        searchHoldHandler.removeCallbacks(searchHoldRunnable)
                        
                        // Short press behavior
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
                return true
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
            // Handle right arrow for settings menu (3s hold) when all fragments are hidden
            if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden && onboardingFragment.isHidden && searchFragment.isHidden) {
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

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // Handle left arrow for Voice Search (3s hold) when all fragments are hidden
            if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden && onboardingFragment.isHidden && searchFragment.isHidden) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        isLeftArrowPressed = true
                        if (isTvDevice()) {
                            leftArrowHoldJob?.cancel()
                            leftArrowHoldJob = this@MainActivity.lifecycleScope.launch {
                                delay(2000)
                                if (isLeftArrowPressed) {
                                    isLeftArrowPressed = false
                                    showSearchFragment()
                                }
                            }
                        }
                    }
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (isLeftArrowPressed) {
                        isLeftArrowPressed = false
                        leftArrowHoldJob?.cancel()
                        showFragment(menuFragment)
                    }
                    return true
                }
            }
        }

        // Media Keys Handling
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    webFragment.seekRelative(10000)
                    showSeekHud(10000)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    webFragment.seekRelative(-10000)
                    showSeekHud(-10000)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    webFragment.safeTogglePlayback()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    next()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    prev()
                    return true
                }
            }
        }


        // DPAD & Navigation Logic
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden && onboardingFragment.isHidden && searchFragment.isHidden) {
                        channelUp()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden && onboardingFragment.isHidden && searchFragment.isHidden) {
                        channelDown()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    // Short press handled above to allow long-press
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    back()
                    return true
                }
                KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HELP, KeyEvent.KEYCODE_BOOKMARK -> {
                    showSetting()
                    return true
                }
                KeyEvent.KEYCODE_M -> {
                    SP.moveMode = !SP.moveMode
                    Toast.makeText(this, "Move mode: ${if(SP.moveMode) "on" else "off"}", Toast.LENGTH_SHORT).show()
                    return true
                }
                KeyEvent.KEYCODE_A -> {
                    cycleAspectRatio()
                    return true
                }
                // (Already handled above)
                KeyEvent.KEYCODE_SEARCH, 219, 231 -> {
                    return true
                }
                // Numeric Entry
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    if (menuFragment.isHidden && settingFragment.isHidden && trackSelectionFragment.isHidden && epgGridFragment.isHidden && onboardingFragment.isHidden && searchFragment.isHidden) {
                        showChannel((event.keyCode - KeyEvent.KEYCODE_0).toString())
                        return true
                    }
                }
            }
        }

        // MANDATORY UPDATE BLOCK: Ignore all remote keys if app is outdated
        if (SecurityUtil.isAppOutdated) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Allow only the 'Enter' key and 'D-Pad' to interact with the Update Dialog
                val allowedKeys = listOf(
                    KeyEvent.KEYCODE_DPAD_CENTER, 
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT
                )
                if (event.keyCode !in allowedKeys) {
                    Toast.makeText(this, "Update Required", Toast.LENGTH_SHORT).show()
                    return true
                }
            }
            return try {
                super.dispatchKeyEvent(event)
            } catch (e: IllegalStateException) {
                Log.e("EXCL_KEYS", "Focus search failed during key dispatch: ${e.message}")
                false
            }
        }

        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            Log.e("EXCL_KEYS", "Focus search failed during key dispatch: ${e.message}")
            false
        }
    }

    fun showSearchFragment() {
        if (!searchFragment.isHidden) return
        showFragment(searchFragment)
        if (isTvDevice()) {
            searchFragment.showKeyboard()
        } else {
            searchFragment.hideKeyboard()
        }
    }

    fun hideSearchFragment() {
        if (searchFragment.isHidden) return
        hideFragment(searchFragment)
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
        if (menuFragment.isHidden && settingFragment.isHidden && onboardingFragment.isHidden) {
            if (SP.channelReversal) {
                prev()
                return
            }
            next()
        }
    }

    private fun channelDown() {
        if (menuFragment.isHidden && settingFragment.isHidden && onboardingFragment.isHidden) {
            if (SP.channelReversal) {
                next()
                return
            }
            prev()
        }
    }

    private fun back() {
        if (!onboardingFragment.isHidden) {
            hideOnboarding()
            return
        }
        if (!searchFragment.isHidden) {
            hideSearchFragment()
            return
        }
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

        if (infoFragment.isShowing()) {
            infoFragment.dismiss()
            return
        }

        if (channelFragment.isShowing()) {
            channelFragment.dismiss()
            return
        }

        // MANDATORY UPDATE BLOCK: Prevent exiting via Back button if app is outdated
        if (SecurityUtil.isAppOutdated) {
            Toast.makeText(this, "Please update to continue", Toast.LENGTH_LONG).show()
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
        
        // Dynamic sub-message based on actual connectivity
        if (isNetworkAvailable()) {
            errorFragment.showSubMsg("Low speed or server issue detected. Please wait...")
        } else {
            errorFragment.showSubMsg("Please check your internet connection and try again.")
        }

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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // [ALEXA BRIDGE] Handle results from system speech dialog
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0)
            if (!spokenText.isNullOrEmpty()) {
                Log.d("EXCL_VOICE", "System Dialog Result: $spokenText")
                if (searchFragment.isAdded) {
                    searchFragment.applyExternalQuery(spokenText)
                }
            }
            return
        }

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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopCoreHealthMonitoring()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        SP.removeOnSharedPreferenceChangeListener(spListener)
        server?.stop()
        if (::updateManager.isInitialized) {
            updateManager.destroy()
        }
        super.onDestroy()
    }

    // ADVANCED SECURITY: Deep Kernel-Level VPN Detection
    private fun isVpnActive(): Boolean {
        // FIRETV FIX: TV devices (Leanback/FireTV) use system-level network tunnels
        // that are indistinguishable from VPN connections. The OS-level check, hardware
        // interface scan, and native C++ scan all report false positives on these devices.
        // VPN enforcement on a TV stick is not a meaningful security boundary (users cannot
        // install unauthorized VPN apps on FireTV without sideloading), so we skip all checks.
        if (isTvDevice()) {
            return false
        }

        // 1. High-Level API Check (Catches Basic VPNs on Mobile)
        try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (networkCapabilities != null && networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore if API fails
        }

        // 2. DEEP HARDWARE SCAN (Defeats root/Xposed hider apps on Mobile)
        // VPN bypass apps hook the API above to return false.
        // We bypass them by physically checking the Linux subsystem for virtual network adapters.
        try {
            val networkInterfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                // Must be 'up' running
                if (networkInterface.isUp) {
                    val name = networkInterface.name ?: ""
                    // Common Kernel VPN Interfaces
                    if (name.startsWith("tun") || 
                        name.startsWith("ppp") || 
                        name.startsWith("pptp") || 
                        name.startsWith("tap")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
             // Ignore interface read errors
        }

        // 3. ULTIMATE NATIVE C++ SCAN (Defeats LSPosed/Magisk that hook java.net.NetworkInterface)
        try {
            if (SecretManager.isVpnActiveNative()) {
                return true
            }
        } catch (e: Exception) {
            // Ignore native crash / load failure
        }

        return false
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun startRootMonitoring() {
        // PROFESSIONAL FIX: Move root check to background thread to prevent UI thread blockage
        // and potential playback stutter on low-resource devices like FireTV.
        CoroutineScope(Dispatchers.IO).launch {
            while (!isFinishing) {
                try {
                    val isRooted = RootCheckUtil.isDeviceRooted(this@MainActivity)
                    if (isRooted && !wasRooted) {
                        runOnUiThread {
                            if (!isFinishing) {
                                Toast.makeText(this@MainActivity, "Rooted device detected. App cannot run.", Toast.LENGTH_LONG).show()
                                finishAffinity()
                            }
                        }
                        break // Exit loop
                    }
                    wasRooted = isRooted
                } catch (e: Exception) {
                    // Ignore background security check errors to prevent crashes
                }
                kotlinx.coroutines.delay(securityCheckInterval)
            }
        }
    }

    override fun onForceUpdate() {
        SecurityUtil.isAppOutdated = true
        com.codesrahul.exclusivetv.models.TVList.clear(this)
        
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
        runOnUiThread {
            isMaintenanceMode = true
            SecurityUtil.isMaintenanceMode = true
            com.codesrahul.exclusivetv.models.TVList.clear(this)
            
            // Stop playback and server
            webFragment.stop()
            server?.stop()
            server = null
            
            // Show maintenance screen
            showFragment(maintenanceFragment)
            hideFragment(loginFragment)
            hideFragment(loadingFragment)
            
            Toast.makeText(this, "System under maintenance", Toast.LENGTH_LONG).show()
        }
    }

    private fun cycleAspectRatio() {
        val nextMode = (SP.resizeMode + 1) % 3
        SP.resizeMode = nextMode
    }

    private fun showDowngradeDialog(message: String) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Premium Trial Ended")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
        dialog.show()
    }

    protected override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        val action = intent?.action
        Log.d("EXCL_VOICE", "onNewIntent received with action: $action")
        
        // [ALEXA BRIDGE] Catch search queries from system Alexa UI or Amazon-specific searches
        if (Intent.ACTION_SEARCH == action || 
            action == "com.amazon.tv.leanbacklauncher.recs.QUERY" ||
            action == "com.amazon.tv.launcher.SEARCH") {
            
            val query = intent.getStringExtra(android.app.SearchManager.QUERY) 
                ?: intent.getStringExtra("query")
                
            if (!query.isNullOrEmpty()) {
                Log.d("EXCL_VOICE", "Captured Alexa Query: $query")
                Toast.makeText(this, "Searching for: $query", Toast.LENGTH_SHORT).show()
                
                // Try to bring the app to the front over the system results
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                
                if (searchFragment.isAdded) {
                    showSearchFragment()
                    searchFragment.applyExternalQuery(query)
                }
            }
        }
    }

    override fun onSearchRequested(): Boolean {
        // [PROFESSIONAL] Block system-wide Alexa search and show internal Voice Search instead
        if (SP.voiceSearch) {
            showSearchFragment()
            if (!searchFragment.isHidden) {
                searchFragment.triggerVoiceSearch()
            }
            return true // Consume to prevent Alexa from appearing
        }
        return false // Let system handle if voice search is disabled in settings
    }

    /**
     * Schedules periodic background sync using WorkManager.
     * This makes the app "smart" by keeping the playlist fresh automatically.
     */
    private fun scheduleBackgroundSync() {
        Log.d("EXCL_SYNC", "Scheduling Periodic Background Sync...")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule every 6 hours
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(6, TimeUnit.HOURS) 
            .build()

        try {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PlaylistSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            Log.e("EXCL_SYNC", "Failed to schedule background sync: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
