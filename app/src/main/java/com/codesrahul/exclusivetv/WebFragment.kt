package com.codesrahul.exclusivetv

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.codesrahul.exclusivetv.databinding.PlayerBinding
import com.codesrahul.exclusivetv.models.TVModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import java.util.UUID
import android.util.Base64
import java.nio.charset.StandardCharsets
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.json.JSONObject
import org.json.JSONArray
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import android.view.SurfaceView
import androidx.media3.extractor.ts.TsExtractor



import androidx.annotation.OptIn
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@android.annotation.SuppressLint("SetJavaScriptEnabled")
class WebFragment : Fragment(), OnSharedPreferenceChangeListener {
    private lateinit var mainActivity: MainActivity

    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var currentVideoUrl: String = ""
    val client = SecureHttpClient.client
    private var tvModel: TVModel? = null
    private var savedAudioTrackToApply: Int = -1
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private val playbackHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isWebMode = false
    private var uaFallbackIndex = 0
    private var seamlessRetryCount = 0
    data class AudioTrack(val index: Int, val name: String, val isSelected: Boolean)

    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        // mainActivity is used in some methods, but it's safer to access activity directly when needed.
        // If we must store it, ensure it's updated.
        (activity as? MainActivity)?.let {
            mainActivity = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        webView = binding.webView
        playerView = binding.playerView

        val application = (activity?.applicationContext as? MyTVApplication) ?: return binding.root
        webView.setBackgroundColor(android.graphics.Color.BLACK)

        webView.layoutParams.width = application.shouldWidthPx()
        webView.layoutParams.height = application.shouldHeightPx()
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.userAgentString = OptimizationManager.UA_CHROME_DESKTOP

        webView.isClickable = false
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        // Newly added settings
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        WebView.setWebContentsDebuggingEnabled(true)

        webView.setOnTouchListener { _, event ->
            if (event != null) {
                (activity as? MainActivity)?.gestureDetector?.onTouchEvent(event)
            }
            true
        }

        (activity as? MainActivity)?.ready(TAG)
        
        // Force Initial Resize Mode
        val savedMode = SP.resizeMode
        playerView.resizeMode = when(savedMode) {
            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        return binding.root
    }

    private fun isVODContent(): Boolean {
        val tv = tvModel?.tv ?: return false
        val group = tv.group.lowercase()
        val url = currentVideoUrl.lowercase()
        
        // 1. Detection via Group Name
        if (group.contains("movie") || group.contains("cinema") || group.contains("series") || group.contains("vod")) {
            return true
        }
        
        // 2. Detection via URL extension (Static files)
        val vodExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv")
        if (vodExtensions.any { url.endsWith(it) || url.contains("$it?") }) {
            // Ensure it's not a known live stream type if it matches extension
            if (!url.contains("live") && !url.contains("stream")) {
                return true
            }
        }
        return false
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var retryCount = 0
    private var currentUrlIndex = 0
    private val maxRetries = 10
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = context ?: return
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize WakeLock
        // Initialize WakeLock & Keep Screen On
        try {
            playerView.keepScreenOn = true
            playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ExclusiveTV:WakeLock")
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ExclusiveTV:WifiLock")
            } else {
                wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ExclusiveTV:WifiLock")
            }
            wifiLock?.setReferenceCounted(false)
            SP.setOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
//                        "WebViewConsole",
//                        "Message: ${consoleMessage.message()}, Source: ${consoleMessage.sourceId()}, Line: ${consoleMessage.lineNumber()}"
//                    )

                    if (consoleMessage.message() == "success") {
                        tvModel?.setErrInfo("web ok")
                    }
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                webView: WebView?,
                handler: SslErrorHandler,
                error: SslError?
            ) {
                handler.cancel()
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url
                if (OptimizationManager.shouldBlockRequest(uri)) {
                    return OptimizationManager.createEmptyResponse()
                }

                // Generic Logic: Block non-essential media/css on non-mainframe requests 
                // to speed up Portal-based streams.
                if (request?.isForMainFrame == false && (
                    uri?.path?.endsWith(".jpg") == true || 
                    uri?.path?.endsWith(".png") == true || 
                    uri?.path?.endsWith(".gif") == true || 
                    uri?.path?.endsWith(".css") == true || 
                    uri?.path?.endsWith(".ico") == true)) {
                    return OptimizationManager.createEmptyResponse()
                }

                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // DATA-DRIVEN: Force Fill Aspect Ratio via CSS Injection
                val fillCss = """
                    javascript:(function() {
                        var style = document.createElement('style');
                        style.innerHTML = 'video, iframe, canvas, div.video-container { object-fit: fill !important; width: 100vw !important; height: 100vh !important; position: fixed !important; top: 0 !important; left: 0 !important; z-index: 99999 !important; } body, html { overflow: hidden !important; margin: 0 !important; padding: 0 !important; }';
                        document.head.appendChild(style);
                    })()
                """.trimIndent()
                webView.loadUrl(fillCss)

                // APPLY DYNAMIC OPTIMIZATIONS (JS/CSS)
                if (url != null) {
                    OptimizationManager.applyWebView(webView, url)
                }
            }
        }
    }

    fun stop() {
        clearWebViewResources()
        releasePlayer()
        retryCount = 0 // Stop retrying
        playerView.visibility = View.GONE
        webView.visibility = View.GONE
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        tvModel.setErrInfo("") // Clear any previous error state immediately
        retryCount = 0 // Reset for new channel
        uaFallbackIndex = 0 // Reset for new channel
        currentUrlIndex = 0 // Reset URL index
        val url = tvModel.videoUrl.value ?: return
        this.currentVideoUrl = url
        
        // [PROFESSIONAL] Resume logic is now handled natively in initializePlayer()
        // using SP.getVODPosition(url) for better decoupling.

        
        // Use the first URI as the canonical key for audio track preference (matches save logic in MainActivity)
        val channelKey = if (tvModel.tv.uris.isNotEmpty()) tvModel.tv.uris[0] else url
        savedAudioTrackToApply = SP.getAudioTrack(channelKey)

        // Check if explicit type forces Player, or if URL detected as stream
        val isStreamType = tvModel.tv.type == com.codesrahul.exclusivetv.models.Type.STREAM || 
                           tvModel.tv.type == com.codesrahul.exclusivetv.models.Type.HLS
                           
        if (isStreamType || 
            url.contains(".m3u8", ignoreCase = true) || 
            url.contains(".ts", ignoreCase = true) ||
            url.contains(".mpd", ignoreCase = true) ||
            url.contains(".mkv", ignoreCase = true) ||
            url.contains(".mp4", ignoreCase = true) ||
            // Support for php/script based streams (common in IPTV)
            (url.contains(".php", ignoreCase = true) && (url.contains("id=") || url.contains("stream") || url.contains("live"))) ||
            url.startsWith("rtmp://", ignoreCase = true) || 
            url.startsWith("rtsp://", ignoreCase = true) || 
            url.contains("?|") ||
            (tvModel.tv.isAudioOnly && !tvModel.tv.isWebViewEmbed)) {
            
            // PRIORITY OVERRIDE: If metadata forces WebView (e.g. Wiseplay embed:true)
            if (tvModel.tv.isWebViewEmbed) {
                switchToWebView(url)
                return
            }
            
            webView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            clearWebViewResources() // Aggressive cleanup before player init
            
            try {
                initializePlayer(url)
            } catch (e: Exception) {
                tvModel.setErrInfo("Player Init Failed")
            }
            return
        }

        // Zero-Hardcode: Pure WebView Loading
        switchToWebView(url)
    }

    private fun switchToWebView(url: String) {
        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        releasePlayer()
        webView.loadUrl(url)
    }

    fun refreshPlayback() {
        val currentTv = tvModel?.tv ?: return
        val isVod = isVODContent()
        
        // HARD RESET: Release player and create new one
        retryCount = 0
        uaFallbackIndex = 0
        
        // Logic Correction: Don't show "Refreshing..." on fast transitions
        tvModel?.setErrInfo("Refreshing...")

        // Success: Cancel any pending recovery tasks
        playbackHandler.removeCallbacksAndMessages(null)
        playbackHandler.postDelayed({
            tvModel?.let { play(it) }
        }, 1000)
    }

    fun seamlessRefresh() {
        if (exoPlayer == null || currentVideoUrl.isEmpty()) {
            refreshPlayback()
            return
        }
        
        seamlessRetryCount++
        
        // SMART ESCALATION: Allow more silent retries for VOD content (Movies) 
        // to prevent interrupting the viewer with a "Hard Resetting" message.
        val maxSeamlessRetries = if (isVODContent()) 5 else 2
        
        if (seamlessRetryCount > maxSeamlessRetries) {
            // ESCALATION: Fast re-connect failed multiple times. Force a Hard Reset.
            tvModel?.setErrInfo("Hard Resetting Stream...")
            refreshPlayback()
            return
        }
        
        // SILENT AUTO-RECOVERY: Keep player surface, just re-connect
        doInitializePlayer(currentVideoUrl, seamless = true, seekPosition = exoPlayer?.currentPosition ?: -1L)
    }

    private fun initializePlayer(url: String) {
        if (context == null) return
        
        // FIX: Ensure a global CookieHandler exists so HttpURLConnection handles Set-Cookie
        // from CDNs (e.g. provider-specific/Akamai) perfectly into subsequent .m3u8 or .ts chunk requests.
        if (java.net.CookieHandler.getDefault() == null) {
            val cookieManager = java.net.CookieManager()
            cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER)
            java.net.CookieHandler.setDefault(cookieManager)
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // If we have a saved position (from a VOD hard reset escape or fresh start), apply it
                val seekPos = if (isVODContent()) SP.getVODPosition(url) else -1L
                
                // ASYNC PRE-FETCH: If ClearKey URL is present (or derived), fetch it now on Dispatchers.IO
                val currentTv = tvModel?.tv
                if (currentTv != null) {
                    var licenseUrl = currentTv.drmLicenseUrl ?: ""
                    var scheme = currentTv.drmScheme?.lowercase() ?: ""
                    
                    // HEURISTIC RESOLVER: Use OptimizationManager to resolve DRM context without hardcoding strings
                    if (licenseUrl.isEmpty() || scheme.isEmpty()) {
                        val (inferredScheme, inferredLicense) = OptimizationManager.inferDrmContext(url)
                        if (inferredScheme != null) {
                            scheme = inferredScheme
                            if (inferredLicense != null) licenseUrl = inferredLicense
                        }
                    }

                    if (scheme == "clearkey" && licenseUrl.startsWith("http")) {
                        
                        // IDENTITY BRIDGE: Use the same User-Agent for the license fetch
                        val fallbackUa = OptimizationManager.UA_CHROME_DESKTOP
                        val explicitUa = currentTv.headers?.get("User-Agent") ?: currentTv.headers?.get("user-agent")
                        val finalUa = explicitUa ?: fallbackUa

                        val requestHeaders = mutableMapOf<String, String>()
                        
                        // BROAD SECURITY BRIDGE: Carry over all headers (Cookie, Referer, Authorization, etc.) 
                        // from the channel metadata to the license fetch request for full identity parity.
                        currentTv.headers?.forEach { (k, v) ->
                             if (k.lowercase() != "user-agent") {
                                 requestHeaders[k] = v
                             }
                        }
                        
                        // Extract URL from pipes if present (Support token-guarded license URLs)
                        if (licenseUrl.contains("|")) {
                            val parts = licenseUrl.split("|")
                            licenseUrl = parts[0]
                            if (parts.size > 1) {
                                parts[1].split("&").forEach { pair ->
                                    val kv = pair.split("=", limit = 2)
                                    if (kv.size == 2) requestHeaders[kv[0].trim()] = kv[1].trim()
                                }
                            }
                        }
                        
                        val fetchedJson = withContext(Dispatchers.IO) {
                            try {
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                
                                val reqBuilder = Request.Builder().url(licenseUrl).get()
                                reqBuilder.header("User-Agent", finalUa)
                                requestHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                                
                                val response = client.newCall(reqBuilder.build()).execute()
                                if (response.isSuccessful) {
                                    response.body?.string() ?: ""
                                } else {
                                    Log.w(TAG, "ClearKey Fetch Failed: ${response.code}")
                                    ""
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to pre-fetch clearkey: ${e.message}")
                                ""
                            }
                        }
                        
                        if (fetchedJson.contains("\"keys\"") || fetchedJson.contains("\"k\"")) {
                            currentTv.drmLicenseUrl = fetchedJson
                            Log.d(TAG, "Successfully pre-fetched remote ClearKey JSON via ${if (explicitUa != null) "Explicit" else "Inferred"} Identity")
                        }
                    }
                }

                // SMART SNIFFING for ambiguous URLs (e.g. .m, .php, .aspx)
                var sniffedMime: String? = null
                if (!url.contains(".m3u8") && !url.contains(".mpd") && !url.contains(".ts")) {
                    sniffedMime = withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                .followRedirects(true)
                                .build()
                            val request = Request.Builder().url(url).head().build()
                            client.newCall(request).execute().use { response ->
                                response.header("Content-Type")?.lowercase()
                            }
                        } catch (e: Exception) { null }
                    }
                }
                
                doInitializePlayer(url, seamless = false, seekPosition = seekPos, sniffedMime = sniffedMime)
            } catch (e: Exception) {
                Log.e(TAG, "Playback Init Error: ${e.message}")
                tvModel?.setErrInfo("Playback Error")
                releasePlayer()
            }
        }
    }

    private fun doInitializePlayer(url: String, seamless: Boolean = false, seekPosition: Long = -1L, sniffedMime: String? = null) {
        // Only release the previous player if we're not doing a seamless re-connect
        if (!seamless) {
            releasePlayer()
        }

        // Acquire WakeLock
        if (wakeLock?.isHeld == false) {
             wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours timeout safety
        }
        if (wifiLock?.isHeld == false) {
             wifiLock?.acquire()
        }

        currentVideoUrl = url
        var videoUrl = url
        var drmConfig: DrmConfig? = null
        val requestHeaders = mutableMapOf<String, String>()
        var userAgent = getOptimalUserAgent(url)
        var uaExplicitlySet = false

        // 1. Metadata-Driven Headers & Identity (Priority: Channel Metadata)
        val currentTv = tvModel?.tv
        if (currentTv != null) {
            currentTv.headers?.let { requestHeaders.putAll(it) }
            
            // Extract UA from headers if present
            requestHeaders["User-Agent"]?.let { 
                userAgent = it 
                uaExplicitlySet = true
                requestHeaders.remove("User-Agent") 
            }
            requestHeaders["user-agent"]?.let { 
                userAgent = it 
                uaExplicitlySet = true
                requestHeaders.remove("user-agent") 
            }

            // DRM: Smart detection from model
            if (!currentTv.drmScheme.isNullOrEmpty()) {
                var rawLicenseUrl = currentTv.drmLicenseUrl ?: ""
                // Support pipe-delimited license headers
                if (rawLicenseUrl.contains("|")) {
                    val parts = rawLicenseUrl.split("|")
                    rawLicenseUrl = parts[0]
                    if (parts.size > 1) {
                        parts[1].split("&").forEach { pair ->
                            val kv = pair.split("=", limit = 2)
                            if (kv.size == 2) requestHeaders[kv[0].trim()] = kv[1].trim()
                        }
                    }
                }
                drmConfig = DrmConfig(currentTv.drmScheme!!, rawLicenseUrl)
            }
        }

        
        // 3. UNIVERSAL IDENTITY MIRRORING: Derivative security suite from technical context
        val inferredHeaders = OptimizationManager.inferSecurityContext(url, currentTv?.group)
        inferredHeaders.forEach { (k, v) ->
            if (k == "User-Agent") {
                if (!uaExplicitlySet || userAgent.isBlank()) userAgent = v
            } else {
                // BUG FIX: Allow inferred headers to override EMPTY or BLANK manual headers
                // If the user left the field empty in the UI, we should still apply the heuristic.
                val existing = requestHeaders[k]
                if (existing.isNullOrBlank()) {
                    requestHeaders[k] = v
                }
            }
        }
        
        // 4. Fallback URL Parameter Extraction (Legacy support)
        val regex = "(?i)(\\?\\|)|(\\?%7C)".toRegex()
        val matchResult = regex.find(url)
        if (matchResult != null) {
            val splitIndex = matchResult.range.first
            videoUrl = url.substring(0, splitIndex)
            val params = url.substring(matchResult.range.last + 1).split("&")
            params.forEach { param ->
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].lowercase()
                    val v = kv[1]
                    when (k) {
                        "drmscheme" -> drmConfig = DrmConfig(v, params.find { it.startsWith("drmlicense", true) }?.split("=")?.getOrNull(1) ?: "")
                        "user-agent" -> userAgent = v
                        else -> requestHeaders[k] = v
                    }
                }
            }
        }


        // OPTIMIZED BUFFER SETTINGS
        // DYNAMIC PLAYBACK STRATEGY: Resolves stability config based on stream fingerprints
        // instead of hardcoded checks, complying with "No Hardcoding" directive.
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val strategy = OptimizationManager.getPlaybackStrategy(url, totalMemGb)

        // DOLBY AUDIO LOGIC:
        // ON (PREFER): High-fidelity passthrough for AVR/Soundbar (Multi-channel)
        // OFF (ON): Standard compatibility mode. If a channel ONLY has Dolby audio,
        // ExoPlayer will still play it via hardware/software downmixing to Stereo.
        val extMode = if (SP.dolbyAudio) 
            androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        else 
            androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(requireContext())
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(strategy.enableDecoderFallback) // PRO FIX: Allow software fallback for corrupted hardware frames
            .setEnableAudioTrackPlaybackParams(true) 
            .setEnableAudioFloatOutput(false) 
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, _ ->
                // STABILITY FIX: Prefer standard hardware decoders without tunneling
                // Tunneling is the primary cause of flickering/sync issues in 4K TS.
                val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, false 
                )
                decoders
            }

        val loadControl = getLoadControl(url, strategy)

        val builder = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
        
        // TRANSPORT UPGRADE: Transitioning to OkHttpDataSource for high-fidelity networking.
        // Standard DefaultHttpDataSource is less stable for massive IPTV TS streams.
        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(OptimizationManager.okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)


        // ENHANCED EXTRACTOR FACTORY FOR .TS FILES (Multi-Audio / H265 / DD5.1)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(strategy.tsExtractorFlags)
            .setTsExtractorMode(strategy.tsExtractorMode) 

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(requireContext(), extractorsFactory)
        mediaSourceFactory.setDataSourceFactory(httpDataSourceFactory)
        mediaSourceFactory.setLoadErrorHandlingPolicy(IPTVLoadErrorHandlingPolicy())

        // FIX: Configure DRM Provider to use our Cookie-enabled DataSource
        val drmProvider = androidx.media3.exoplayer.drm.DrmSessionManagerProvider { mediaItem: androidx.media3.common.MediaItem ->
            
            var schemeUuid = if (drmConfig != null) {
                when (drmConfig?.scheme?.lowercase()) {
                    "widevine" -> C.WIDEVINE_UUID
                    "playready" -> C.PLAYREADY_UUID
                    "clearkey", "org.w3.clearkey" -> C.CLEARKEY_UUID
                    else -> C.WIDEVINE_UUID
                }
            } else {
                 val drmConf = mediaItem.localConfiguration?.drmConfiguration
                 if (drmConf != null) drmConf.scheme else C.WIDEVINE_UUID
            }

            var licenseUrl = drmConfig?.license ?: mediaItem.localConfiguration?.drmConfiguration?.licenseUri?.toString() ?: ""

            // HEURISTIC: Force ClearKey if we have local keys (KID:KEY format instead of URL)
            // This is mandatory for SunNxt/Times-Play where manifest might prefer Widevine but keys are ClearKey.
            if (!licenseUrl.startsWith("http") && licenseUrl.isNotEmpty() && licenseUrl.contains(":")) {
                schemeUuid = C.CLEARKEY_UUID
            }

            // BRIDGE: Provide the SAME authentication headers (Cookies, User-Agent) to the DRM key-server.
            // This is mandatory for portal-grade security contexts.
            val drmDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(requestHeaders)
                .setAllowCrossProtocolRedirects(true)

            val manager: androidx.media3.exoplayer.drm.DrmSessionManager = if (schemeUuid == C.CLEARKEY_UUID && licenseUrl.isNotEmpty()) {
                 val clearkeyJson = createClearKeyJson(licenseUrl)
                 if (clearkeyJson.isNotEmpty()) {
                     val drmCallback = LocalMediaDrmCallback(clearkeyJson.toByteArray())
                     DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .setPlayClearSamplesWithoutKeys(true)
                        .build(drmCallback)
                 } else if (licenseUrl.startsWith("http")) {
                     val callback = HttpMediaDrmCallback(licenseUrl, drmDataSourceFactory)
                     DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .setPlayClearSamplesWithoutKeys(true)
                        .build(callback)
                 } else {
                     androidx.media3.exoplayer.drm.DrmSessionManager.DRM_UNSUPPORTED
                 }
            } else if (licenseUrl.isNotEmpty() && licenseUrl.startsWith("http")) {
                 val drmCallback = HttpMediaDrmCallback(licenseUrl, drmDataSourceFactory)
                 drmCallback.setKeyRequestProperty("User-Agent", userAgent)
                 for ((k, v) in requestHeaders) {
                     drmCallback.setKeyRequestProperty(k, v)
                 }

                 DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .setPlayClearSamplesWithoutKeys(true)
                    .build(drmCallback)
            } else {
                 DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(androidx.media3.exoplayer.drm.LocalMediaDrmCallback(ByteArray(0)))
            }
            manager
        }
        
        mediaSourceFactory.setDrmSessionManagerProvider(drmProvider)
        
        if (seamless && exoPlayer != null) {
            // RE-USE EXISTING PLAYER (GAPLESS)
            // We do NOT call stop() or clearMediaItems() here to keep the last frame visible
        } else {
            // BUILD NEW PLAYER
            builder.setMediaSourceFactory(mediaSourceFactory)
            exoPlayer = builder.build()
        }

        // DYNAMIC QUALITY FIX: Set Video Scaling Mode based on strategy
        exoPlayer?.setVideoScalingMode(strategy.scalingMode)
        
        // PRO FIX: Configure Surface Z-Order to prioritize video layer over UI background
        setVideoSurfaceZOrder()
        
        // Re-apply common player settings 
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF // Ended detection handles re-connect smarter
        // Removed REPEAT_MODE_ONE as it repeats the cached content.
        // We now use STATE_ENDED listener to trigger a fresh URL request.

        // Logic Correction: "Force High Quality" should ENABLE High Quality (No Limit), not restrict to SD.
        // If the toggle is ON, we want MAX resolution.
        // If the toggle is OFF, we might want to save data (SD)?
        // For now, let's assume the user wants the BEST quality by default.
        // BITRATE & QUALITY SELECTION LOGIC
        applyBitrateParameters()

        // Apply Default Audio Language Preference & FireTV Audio Stability Fixes
        val defaultLang = SP.defaultAudioLanguage
        if (exoPlayer != null) {
             try {
                 val currentParams = exoPlayer!!.trackSelectionParameters
                 val builder = currentParams.buildUpon()
                 
                 if (defaultLang.isNotEmpty()) {
                     builder.setPreferredAudioLanguages(defaultLang)
                 }

                 // TV AUDIO FIX: Allow 5.1/6-channel audio to pass through to hardware rather than forcing stereo downmix
                 val uiModeManager = requireContext().getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
                 if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                     builder.setMaxAudioChannelCount(6) // 5.1 Surround Support
                 }
                 
                 exoPlayer!!.trackSelectionParameters = builder.build()
             } catch (e: Exception) {
             }
        }
        
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                
                // [PROFESSIONAL] High-level recovery and URL rotation
                // Transient retries for the same URL are handled natively by IPTVLoadErrorHandlingPolicy.
                
                val errorCause = error.cause?.toString() ?: ""
                val isCodecError = errorCause.contains("decrypt") || errorCause.contains("MediaCodec")
                
                if (isCodecError && retryCount < 1) {
                    retryCount++
                    initializePlayer(currentVideoUrl) // Hard reset to trigger potential software/DRM fallback
                    return
                }

                // URL / Identity Escalation on Persistence Failure
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    val msg = error.message ?: ""
                    if (msg.contains("403") || msg.contains("401")) {
                        if (uaFallbackIndex < 3) {
                            uaFallbackIndex++
                            initializePlayer(currentVideoUrl) 
                            return
                        }
                    }
                }

                if (currentTv != null && currentUrlIndex < (currentTv.uris.size - 1)) {
                    currentUrlIndex++
                    retryCount = 0
                    val nextUrl = currentTv.uris[currentUrlIndex]
                    tvModel?.setErrInfo("Switching to Backup Stream...")
                    initializePlayer(nextUrl)
                } else {
                    tvModel?.setErrInfo("Stream Offline or Unsupported")
                    switchToUniversalFallback(currentVideoUrl)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                  if (playbackState == Player.STATE_READY) {
                        tvModel?.setErrInfo("success") 
                        retryCount = 0 
                  } else if (playbackState == Player.STATE_ENDED) {
                        // End of stream detected (common in short-lived PHP IPTV links).
                        // Silent Seamless refresh without UI flashing 'Refreshing...'
                        seamlessRefresh()
                  }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    tvModel?.setErrInfo("success")
                }
            }

            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                tvModel?.setErrInfo("success")
                seamlessRetryCount = 0 // SUCCESS: Reset recovery escalation
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                super.onVideoSizeChanged(videoSize)
                updateQualityLabel(videoSize.height)
            }


            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                super.onTracksChanged(tracks)
                
                if (savedAudioTrackToApply != -1) {
                    val targetIndex = savedAudioTrackToApply
                    savedAudioTrackToApply = -1
                    setAudioTrack(targetIndex)
                }

                var audioLabel = ""
                var hasAudio = false
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        hasAudio = true
                        if (group.isSelected) {
                            val format = group.getTrackFormat(0)
                            val channels = format.channelCount
                            audioLabel = when (channels) {
                                1 -> "Mono"
                                2 -> "Stereo"
                                6 -> "5.1ch"
                                8 -> "7.1ch"
                                else -> if (channels > 0) "${channels}ch" else ""
                            }
                            // UI POLISH: Map Codecs to Friendly Names
                            val mime = format.sampleMimeType ?: ""
                            val codecName = when {
                                mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                                mime.contains("ac-3") || mime == androidx.media3.common.MimeTypes.AUDIO_AC3 -> "Dolby Digital"
                                mime.contains("eac-3") || mime == androidx.media3.common.MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
                                mime.contains("dts") || mime == androidx.media3.common.MimeTypes.AUDIO_DTS -> "DTS"
                                mime.contains("mpeg") -> "MP3"
                                mime.contains("opus") -> "Opus"
                                mime.contains("flac") -> "FLAC"
                                mime.contains("vorbis") -> "Vorbis"
                                else -> ""
                            }
                            
                                audioLabel = if (codecName.isNotEmpty()) {
                                    if (audioLabel.isNotEmpty()) "$codecName $audioLabel" else codecName
                                } else {
                                    // Fallback if unknown codec but channels detected
                                    if (audioLabel.isNotEmpty()) audioLabel else "Audio OK"
                                }
                                Log.d("PlayerLog", "Audio format: ${format.sampleMimeType}, ${format.channelCount}ch, ${format.sampleRate}Hz, ID: ${format.id ?: "none"}")
                            }
                    }
                }
                
                if (!hasAudio && tracks.groups.isNotEmpty()) {
                    tvModel?.setErrInfo("No Audio Track Found")
                } else if (hasAudio && audioLabel.isEmpty() && tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }) {
                     audioLabel = "Audio OK" // Fallback label
                }
                tvModel?.setAudioQuality(audioLabel)
            }
        })

        playerView.player = exoPlayer
        
        val uri = Uri.parse(videoUrl)
        val isHls = (videoUrl.contains(".m3u8", ignoreCase = true) || 
                   (videoUrl.contains(".php", ignoreCase = true) && (videoUrl.contains("id=") || videoUrl.contains("stream") || videoUrl.contains("live") || videoUrl.contains("ch="))) ||
                   sniffedMime?.contains("mpegurl") == true || sniffedMime?.contains("m3u8") == true) &&
                   !videoUrl.contains("extension=ts", ignoreCase = true) &&
                   !videoUrl.contains(".ts", ignoreCase = true)

        val isDash = videoUrl.contains(".mpd", ignoreCase = true) || 
                    (videoUrl.contains("/mpd/", ignoreCase = true)) || 
                    videoUrl.contains("dash", ignoreCase = true) ||
                    sniffedMime?.contains("dash+xml") == true
                    
        var finalMimeType = when {
            isHls -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            isDash -> androidx.media3.common.MimeTypes.APPLICATION_MPD
            else -> null
        }

        // Priority Hint: Use explicit MIME from TV model if provided (fixes 'naked' DASH/MPD streams)
        currentTv?.mimeType?.let { 
            if (it.isNotEmpty()) finalMimeType = it 
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(finalMimeType)

        // 3. DRM Configuration (Universal Activation)
        if (drmConfig != null) {
            val uuid = when (drmConfig?.scheme?.lowercase()) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                "clearkey", "org.w3.clearkey" -> C.CLEARKEY_UUID
                else -> C.WIDEVINE_UUID
            }
            
            // For local keys (Clearkey), we MUST provide a dummy license URI to trigger the CDM on many devices.
            val licenseUri = if (drmConfig?.license?.startsWith("http") == true) {
                Uri.parse(drmConfig?.license)
            } else {
                Uri.parse("https://localhost/clearkey") // Placeholder to activate CDM
            }

            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(uuid)
                    .setLicenseUri(licenseUri)
                    .setForceDefaultLicenseUri(true)
                    // PARITY CRITICAL: Force session activation even if the manifest lacks the ClearKey PSSH box
                    .setForceSessionsForAudioAndVideoTracks(true)
                    .build()
            )
        }

        val mediaItem = mediaItemBuilder.build()

        if (isHls) {
            // UNIVERSAL FIX: Apply robust settings to ALL HLS streams including TS audio flags
            val hlsExtractorFactory = DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
                true
            )
            val hlsMediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpDataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setAllowChunklessPreparation(false) // Strict Sync to extract undeclared tracks
                .setDrmSessionManagerProvider(drmProvider)
            
            exoPlayer?.setMediaSource(hlsMediaSource.createMediaSource(mediaItem), !seamless)
        } else if (isDash) {
            // DASH ROBUSTNESS: Explicit DashMediaSource for complex DRM manifests
            val dashMediaSource = androidx.media3.exoplayer.dash.DashMediaSource.Factory(httpDataSourceFactory)
                .setDrmSessionManagerProvider(drmProvider)
            
            exoPlayer?.setMediaSource(dashMediaSource.createMediaSource(mediaItem), !seamless)
        } else {
            exoPlayer?.setMediaItem(mediaItem, !seamless)
        }
        
        exoPlayer?.prepare()
        
        // SMART RESUME: If we have a valid seek position (VOD recovery), seek before playing
        if (seekPosition > 0) {
            exoPlayer?.seekTo(seekPosition)
        }
        
        exoPlayer?.playWhenReady = true
        // Audio Stabilizer (LoudnessEnhancer)
        if (SP.audioStabilizer) {
            try {
                val sessionId = exoPlayer?.audioSessionId ?: 0
                if (sessionId != 0) {
                     loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
                     loudnessEnhancer?.setTargetGain(800) // 800mB gain (approx +8dB boost for low volume)
                     loudnessEnhancer?.enabled = true
                }
            } catch (e: Exception) {
            }
        }
        
        exoPlayer?.play()

        // PLAYBACK WATCHDOG: Continuous robust check for stalling/infinite buffering
        // Success: Cancel any pending recovery tasks
        playbackHandler.removeCallbacksAndMessages(null)
    }


    private fun getOptimalUserAgent(url: String): String {
        // TiviMate-Grade Identity Rotation
        return when (uaFallbackIndex) {
            1 -> "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"
            2 -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            3 -> "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200/2.0.4 Safari/533.3"
            else -> "TiviMate/4.7.0 (Linux; Android 11; TV Box Build/RTM1.211111.111)"
        }
    }

    data class DrmConfig(val scheme: String, val license: String)

    private fun createClearKeyJson(license: String): String {
        val trimmedLicense = license.trim()
        
        // 1. JSON PATH: Support JWK format (M3U KODIPROP or remote fetch)
        if (trimmedLicense.startsWith("{") && trimmedLicense.endsWith("}")) {
            try {
                val jsonObj = JSONObject(trimmedLicense)
                val keysArray = findKeysArrayRecursive(jsonObj)
                
                if (keysArray != null) {
                    val sanitized = JSONObject()
                    val sanitizedArray = JSONArray()
                    
                    for (i in 0 until keysArray.length()) {
                        val keyObj = keysArray.optJSONObject(i) ?: continue
                        val kty = keyObj.optString("kty", "oct")
                        val kid = keyObj.optString("kid", "")
                        val k = keyObj.optString("k", "")
                        
                        if (kid.isNotEmpty() && k.isNotEmpty()) {
                            val cleanObj = JSONObject()
                            cleanObj.put("kty", kty)
                            cleanObj.put("kid", hexToBase64Url(kid))
                            cleanObj.put("k", hexToBase64Url(k))
                            sanitizedArray.put(cleanObj)
                        }
                    }
                    
                    sanitized.put("keys", sanitizedArray)
                    return sanitized.toString().replace(" ", "") // Minify to avoid parsing overhead
                }
            } catch (e: Exception) { }
        }
        
        // 2. LEGACY PATH: Support diverse delimiters (KID:KEY, KID-KEY, KID|KEY)
        // Split by common delimiters (comma, semicolon, space, or newline)
        val keyEntries = trimmedLicense.split(Regex("[,;\\s\\n]+")).filter { it.isNotBlank() }
        val keysArray = JSONArray()

        for (entry in keyEntries) {
            // Split KID and KEY by colon, dash, or pipe
            val parts = entry.split(Regex("[:|\\-]"))
            if (parts.size < 2) continue
            
            val val1 = parts[0].trim()
            val val2 = parts[1].trim()
            
            val b1 = hexToBase64Url(val1)
            val b2 = hexToBase64Url(val2)

            // DUAL-COMBINATION RECOVERY: Many playlists flip KID:KEY or use non-standard order.
            // By providing BOTH combinations, we ensure the CDM always correctly identifies the decryption key.
            
            // Combination A: assume [0]=KID, [1]=KEY
            val objA = JSONObject()
            objA.put("kty", "oct")
            objA.put("kid", b1)
            objA.put("k", b2)
            keysArray.put(objA)

            // Combination B: assume [0]=KEY, [1]=KID (Flipped)
            val objB = JSONObject()
            objB.put("kty", "oct")
            objB.put("kid", b2)
            objB.put("k", b1)
            keysArray.put(objB)
        }

        if (keysArray.length() == 0) return ""

        val jsonObject = JSONObject()
        jsonObject.put("keys", keysArray)
        return jsonObject.toString().replace(" ", "")
    }

    private fun findKeysArrayRecursive(obj: JSONObject): JSONArray? {
        if (obj.has("keys") && obj.get("keys") is JSONArray) {
            return obj.getJSONArray("keys")
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.optJSONObject(key)
            if (item != null) {
                val found = findKeysArrayRecursive(item)
                if (found != null) return found
            }
            val array = obj.optJSONArray(key)
            if (array != null) {
                for (i in 0 until array.length()) {
                    val nestedObj = array.optJSONObject(i)
                    if (nestedObj != null) {
                        val found = findKeysArrayRecursive(nestedObj)
                        if (found != null) return found
                    }
                }
            }
        }
        return null
    }

    private fun hexToBase64Url(input: String): String {
        try {
            val raw = input.trim()
            
            // 1. HEX UUID DETECTION: Only strip dashes/spaces if it matches a Hex pattern (0-9, a-f, dashes, spaces)
            // This prevents corrupting valid Base64Url strings that use dashes as part of their encoding.
            val hexUuidRegex = "^[0-9a-fA-F\\-\\s]{32,40}$".toRegex()
            val trimmed = if (hexUuidRegex.matches(raw)) {
                raw.replace("-", "").replace(" ", "")
            } else {
                raw.replace(" ", "") // Keep dashes for potential Base64Url
            }
            
            // AUTO-DETECT: If it's already a valid Base64/Base64Url string...
            // IMPROVED: A 16-byte key/ID is represented as 22-24 characters in Base64. 
            // We shouldn't only rely on special characters like '_' or '+'.
            val isBase64Pattern = trimmed.length in 20..44 && trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }
            val hasBase64SpecificChar = trimmed.contains("_") || trimmed.contains("/") || trimmed.contains("+") || trimmed.contains("-") || trimmed.endsWith("=")
            val isHex = trimmed.length % 2 == 0 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            
            if (isBase64Pattern && (hasBase64SpecificChar || !isHex)) {
                val decoded = if (trimmed.contains("_") || trimmed.contains("-")) {
                     Base64.decode(trimmed, Base64.URL_SAFE)
                } else {
                     Base64.decode(trimmed, Base64.DEFAULT)
                }
                return Base64.encodeToString(decoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            }

            // Assume HEX otherwise
            val bytes = ByteArray(trimmed.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                val j = Integer.parseInt(trimmed.substring(index, index + 2), 16)
                bytes[i] = j.toByte()
            }
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            // Final Fallback: return as-is or empty
            return input
        }
    }

    fun getAudioTracks(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val currentTracks = exoPlayer?.currentTracks ?: return tracks
        
        var totalIndex = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val langCode = format.language ?: ""
                    val langName = if (langCode.isNotEmpty()) {
                        java.util.Locale(langCode).displayLanguage.replaceFirstChar { it.uppercase() }
                    } else ""
                    
                    val channelCount = format.channelCount
                    val channelLabel = when {
                        channelCount >= 6 -> "5.1 Surround"
                        channelCount >= 3 -> "Multi-channel"
                        channelCount == 2 -> "Stereo"
                        channelCount == 1 -> "Mono"
                        else -> ""
                    }
                    
                    val mimeType = format.sampleMimeType?.substringAfterLast("/")?.uppercase()?.replace("E-", "") ?: ""
                    
                    val labelBuilder = StringBuilder()
                    if (langName.isNotEmpty()) labelBuilder.append("[$langName] ")
                    if (channelLabel.isNotEmpty()) labelBuilder.append(channelLabel)
                    if (mimeType.isNotEmpty()) labelBuilder.append(" ($mimeType)")
                    
                    val finalLabel = labelBuilder.toString().trim().ifEmpty { "Audio ${totalIndex + 1}" }
                    
                    tracks.add(AudioTrack(totalIndex, finalLabel, group.isTrackSelected(i)))
                    totalIndex++
                }
            }
        }
        return tracks
    }

    fun setAudioTrack(trackIndex: Int) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        var currentIndex = 0
        
        // If trackIndex is -1 (Default/Auto), clear overrides
        if (trackIndex == -1) {
             player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
             return
        }

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (currentIndex == trackIndex) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        return
                    }
                    currentIndex++
                }
            }
        }
    }

    private fun releasePlayer() {
        playbackHandler.removeCallbacksAndMessages(null)
        try {
            // CRITICAL: Unbind the player from the View FIRST to break renderer references
            playerView.player = null
            
            if (loudnessEnhancer != null) {
                loudnessEnhancer?.release()
                loudnessEnhancer = null
            }
        } catch (e: Exception) {
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) { }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) { }

        try {
            exoPlayer?.release()
        } catch (e: Exception) { }

        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true) {
            // Keep playing in PiP mode
            return
        }

        if (android.os.Build.VERSION.SDK_INT <= 23) {
            releasePlayer()
        } else {
            exoPlayer?.pause()
        }
        webView.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (android.os.Build.VERSION.SDK_INT > 23) {
            releasePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        // Robust Resume: If we have a TVModel (active channel) but no player, restart it.
        // We removed the visibility check because visibility might be unreliable during transitions.
        if (exoPlayer == null && tvModel != null) {
            play(tvModel!!)
        }
        webView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SP.removeOnSharedPreferenceChangeListener(this)
        releasePlayer()
        clearWebViewResources()
        _binding = null
    }

    private fun clearWebViewResources() {
        try {
            binding?.webView?.let { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.onPause() // Ensure the engine is paused
                // We don't destroy() here because the instance is often reused by binding,
                // but we clear all heavy state.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing WebView resources", e)
        }
    }

    companion object {
        private const val TAG = "WebFragment"
    }

    private fun performNetworkRequest(url: String): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val m3u8Regex = """src:\s*"(https://.*?\.m3u8.*?)"""".toRegex()
                    val m3u8Match = m3u8Regex.find(body)
                    if (m3u8Match != null) {
                        return m3u8Match.groupValues[1]
                    }

                    val athavantvRegex = """file:"(https?://[^\"]+\.m3u8)"""".toRegex()
                    val athavanMatch = athavantvRegex.find(body)
                    if (athavanMatch != null) {
                        return athavanMatch.groupValues[1]
                    }

                    val ttnRegex = """source:\s*['"]([^'"]+\.m3u8)['"]""".toRegex()
                    val ttnMatch = ttnRegex.find(body)
                    if (ttnMatch != null) {
                        return ttnMatch.groupValues[1]
                    }

                    val youtubeRegex = """"hlsManifestUrl":"(https?:\/\/[^"]+\.m3u8)"""".toRegex()
                    val youtubeMatch = youtubeRegex.find(body)
                    if (youtubeMatch != null) {
                        return youtubeMatch.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun getCurrentUrl(): String? {
        return currentVideoUrl
    }


    private fun getLoadControl(url: String?, strategy: OptimizationManager.PlaybackStrategy): DefaultLoadControl {
        val minBuffer = strategy.minBufferMs
        val maxBuffer = strategy.maxBufferMs
        val startBuffer = strategy.bufferForPlaybackMs
        
        return DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                startBuffer,
                strategy.bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(strategy.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true) 
            .build()
    }

    private fun switchToUniversalFallback(errorUrl: String) {
        if (!isAdded) return
        if (errorUrl.startsWith("http") || errorUrl.startsWith("https")) {
            if (!isWebMode) {
                isWebMode = true
                _binding?.let { b ->
                    b.playerView.visibility = View.GONE
                    b.webView.visibility = View.VISIBLE
                    releasePlayer()
                    b.webView.loadUrl(errorUrl)
                }
            }
        }
    }

    private class IPTVLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
            // [PROFESSIONAL] High-Speed Stream Recovery Limit (3 Retries / 3s Total)
            val errorCount = loadErrorInfo.errorCount
            if (errorCount > 3) return C.TIME_UNSET 
            
            return when (errorCount) {
                1 -> 1000L  // 1s
                2 -> 1000L  // 1s
                3 -> 1000L  // 1s
                else -> C.TIME_UNSET
            }
        }
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
    }

    fun isPlaying(): Boolean {
        return try {
            exoPlayer?.isPlaying == true || (getCurrentUrl()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }

    fun safeTogglePlayback() {
        if (exoPlayer != null) {
            if (exoPlayer!!.isPlaying) {
                exoPlayer!!.pause()
            } else {
                exoPlayer!!.play()
            }
        }
    }

    fun safeSeekForward() {
        if (exoPlayer != null) {
            val current = exoPlayer!!.currentPosition
            val duration = exoPlayer!!.duration
            if (duration != C.TIME_UNSET) {
                val newPos = (current + 10000).coerceAtMost(duration)
                exoPlayer!!.seekTo(newPos)
            } else {
                // Live stream or unknown duration - just try to seek safely or do nothing
                exoPlayer!!.seekTo(current + 10000)
            }
        }
    }

    fun safeSeekBackward() {
        if (exoPlayer != null) {
            val current = exoPlayer!!.currentPosition
            val newPos = (current - 10000).coerceAtLeast(0)
            exoPlayer!!.seekTo(newPos)
        }
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun isLive(): Boolean {
        return exoPlayer?.isCurrentMediaItemLive == true || exoPlayer?.duration == C.TIME_UNSET
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.let {
            val param = androidx.media3.common.PlaybackParameters(speed)
            it.setPlaybackParameters(param)
        }
    }

    fun setResizeMode(mode: Int) {
        playerView.resizeMode = mode
    }

    fun seekRelative(deltaMs: Long) {
        exoPlayer?.let {
            val current = it.currentPosition
            val duration = it.duration
            val newPos = if (duration != C.TIME_UNSET) {
                (current + deltaMs).coerceIn(0, duration)
            } else {
                (current + deltaMs).coerceAtLeast(0)
            }
            it.seekTo(newPos)
        }
    }

    override fun onSharedPreferenceChanged(key: String) {
        when (key) {
            SP.KEY_BITRATE_MODE -> {
                activity?.runOnUiThread {
                    applyBitrateParameters()
                }
            }
            SP.KEY_RESIZE_MODE -> {
                val mode = SP.resizeMode
                activity?.runOnUiThread {
                    if (isAdded) {
                        playerView.resizeMode = when (mode) {
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        val label = when (mode) {
                            1 -> "Fill"
                            2 -> "Zoom"
                            else -> "Fit"
                        }
                        Toast.makeText(requireContext(), "Aspect Ratio: $label", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SP.KEY_DEFAULT_AUDIO_LANG -> {
                activity?.runOnUiThread {
                    if (isAdded && exoPlayer != null) {
                        updateAudioTrackFromSettings()
                    }
                }
            }
        }
    }

    private fun applyBitrateParameters() {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        
        when (SP.bitrateMode) {
            0 -> { // Data Saver (480p) - Strict Limit
                builder.setMaxVideoSize(854, 480)
                builder.setMaxVideoBitrate(1_000_000)
            }
            1 -> { // Low (720p HD Max)
                builder.setMaxVideoSize(1280, 720)
                builder.setMaxVideoBitrate(2_500_000)
            }
            2 -> { // Medium (1080p FHD Max)
                builder.setMaxVideoSize(1920, 1080)
                builder.setMaxVideoBitrate(5_000_000)
            }
            3 -> { // High (No Limit / 4K)
                builder.clearVideoSizeConstraints()
                builder.setMaxVideoBitrate(Int.MAX_VALUE)
            }
        }
        
        player.trackSelectionParameters = builder.build()
        // Instant quality badge update
        updateQualityLabel(player.videoSize.height)
    }

    private fun updateAudioTrackFromSettings() {
        val player = exoPlayer ?: return
        val lang = SP.defaultAudioLanguage
        val preferDolby = SP.dolbyAudio
        
        try {
            val builder = player.trackSelectionParameters.buildUpon()
            
            // 1. Apply Language Preference
            if (lang.isNotEmpty()) {
                builder.setPreferredAudioLanguages(lang)
            } else {
                builder.setPreferredAudioLanguages()
            }
            
            // 2. Apply Dolby / Multi-channel Priority
            // When Dolby is ON, we avoid lowering channel count to Stereo if a 
            // multi-channel (AC3/5.1) track is available.
            if (preferDolby) {
                // Media3 doesn't have a direct "preferMaxChannels" boolean in builder, 
                // but we can influence it by ensuring we don't constrain to 2.
                builder.setMaxAudioChannelCount(Int.MAX_VALUE)
            } else {
                // If Dolby is OFF, we might want to prioritize compatibility (Stereo)
                // but standard auto behavior is usually fine.
            }
            
            player.trackSelectionParameters = builder.build()
            
            // Show feedback toast if language was changed manually in settings
            if (lang.isNotEmpty()) {
                val locale = java.util.Locale(lang)
                Toast.makeText(requireContext(), "Audio Language: ${locale.displayLanguage}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("WebFragment", "Error updating audio tracks from settings", e)
        }
    }

    private fun updateQualityLabel(height: Int) {
        var label = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "FHD"
            height >= 720 -> "HD"
            height >= 480 -> "SD+"
            height > 0 -> "SD"
            else -> ""
        }

        // Force 'Data Saver' label if mode 0 is active to reflect user choice
        if (SP.bitrateMode == 0 && label.isNotEmpty()) {
            label = "Data Saver"
        }
        
        tvModel?.setVideoQuality(label)
    }

    private fun setVideoSurfaceZOrder() {
        try {
            val playerView = binding?.playerView ?: return
            
            // Apply Z-order overlay to all content to ensure stable layering 
            // behind UI components, preventing surface flicker on diverse TV hardware.
            for (i in 0 until playerView.childCount) {
                val child = playerView.getChildAt(i)
                if (child is SurfaceView) {
                    child.setZOrderMediaOverlay(true)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("WebFragment", "Error setting surface Z-order", e)
        }
    }
}
