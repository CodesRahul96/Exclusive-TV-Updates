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
    private var playbackWatchdog: Runnable? = null
    private var lastPlaybackPosition: Long = -1
    private var bufferingStartTime: Long = -1
    private var lastCheckTime: Long = -1
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
        webView.settings.userAgentString =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 *"

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
                if (uri?.host == "www.nmtv.cn" && uri.path?.endsWith(
                        ".css"
                    ) == true
                ) {
                    return null
                }
                if (uri?.host == "cdnjs.cloudflare.com" && uri.path?.endsWith(
                        "controls.min.css"
                    ) == true
                ) {
                    return null
                }





                if ((uri?.host == "www.btzx.com.cn"
                            || uri?.host == "g.cbg.cn"
                            || uri?.host == "www.ahtv.cn"
//                            || uri?.host == "mapi.ahtv.cn"
//                            || uri?.host == "live.kankanews.com"
//                            || uri?.host == "skin.kankanews.com"

                            ) && uri.path?.endsWith(
                        ".css"
                    ) == true
                ) {
                    return null
                }
                if ((uri?.host == "www.yupptv.com"

                            ) && uri.path?.endsWith(
                        "jioAds.js"
                    ) == true
                ) {
                    return null
                }
//                if (uri?.host == "aj2031.online" ||
//                    uri?.host == "www.googletagmanager.com"
//
//                ) {
//                    return null
//                }
                if (  uri?.path?.endsWith(
                        "gpt.js"
                    ) == true
                ) {
                    return null
                }

//                if (uri?.host == "www.xjtvs.com.cn" && uri.path?.endsWith(
//                        ".css"
//                    ) == true
//                ) {
//                    return null
//                }

                if (request?.isForMainFrame == false && (uri?.path?.endsWith(".jpg") == true || uri?.path?.endsWith(
                        ".png"
                    ) == true || uri?.path?.endsWith(
                        ".gif"
                    ) == true || uri?.path?.endsWith(
                        ".css"
                    ) == true)
                ) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }

                if (uri?.host?.endsWith("cctvpic.com") == true && uri.path?.endsWith(
                        ".css"
                    ) == true
                ) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                if ( uri?.host == "pagead2.googlesyndication.com"
                    || uri?.host == "www.googletagmanager.com"
                    || uri?.host == "jouwaikekaivep.net"
                    || uri?.host == "instant.page"
                    || uri?.path?.endsWith(
                        "adsbygoogle.js"
                    ) == true
                    || uri?.path?.endsWith(
                        "anti_copy.js"
                    ) == true
                ) {
                    return WebResourceResponse("text/plain", "utf-8", null)
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

                val uri = Uri.parse(url)
                when (uri.host) {
                    "tv.cctv.com" -> webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                        .bufferedReader()
                        .use { it.readText() }) { value ->
                        if (value == "success") {
                        }
                    }

                    "www.tvmalaysia.live"-> webView.evaluateJavascript(context.resources.openRawResource(R.raw.tvmalaysia)
                        .bufferedReader()
                        .use { it.readText() }) { value ->
                        if (value == "success") {
                        }
                    }



                    "www.gdtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.setv.sh.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.gdtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.yangshipin.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ysp)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.sztv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "news.hbtv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }
//                    "www.ahtv.cn" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                            }
//                        }
//                    }
                    "www.nxtv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "tv.gxtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "live.fjtv.net" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "tc.hnntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.hebtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "live.mgtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.hnntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.yupptv.com" -> {

//                        webView.postDelayed({
//                            val javaScript = "javascript:document.querySelectorAll('.jw-icon-fullscreen')[0].click();"
//                            webView.loadUrl(javaScript)
//                        },60000)

                            webView.loadUrl(
                                "javascript:(function() { " +
                                        "const divElement = document.createElement('div'); " +
                                        "divElement.id = 'overlayDiv'; " +
                                        "divElement.style.position = 'fixed'; " +
                                        "divElement.style.top = '0'; " +
                                        "divElement.style.left = '0'; " +
                                        "divElement.style.width = '100%'; " +
                                        "divElement.style.height = '100%'; " +
                                        "divElement.style.backgroundColor = '#000'; " +
                                        "divElement.style.zIndex = '99998'; " +
                                        "document.body.appendChild(divElement); " +
                                        "})()"
                            )



                            webView.postDelayed({
                                webView.evaluateJavascript(context.resources.openRawResource(R.raw.yupp)
                                    .bufferedReader()
                                    .use { it.readText() }) { value ->
                                    if (value == "success") {
                                    }
                                }
                            }, 1000) // Ensure the page is loaded


                    }
//
//                    "news.hbtv.com.cn" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                            }
//                        }
//                    }

                    "cricktv.site"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                            }
                        }
                    }

                    "l455o.com"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                            }
                        }
                    }

                    "filemoon.nl"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                            }
                        }
                    }

                    "filemoon.sx"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                            }
                        }
                    }

                    "tapmadtv.live"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.gdtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.8088yyy.news" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.jxtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                            }
//                        }
                    }

                    "www.gzstv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.cztv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.jlntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

//                    "v.iqilu.com" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                            }
//                        }
//                    }

                    "www.qhbtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.qhtb.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.hljtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "static.hntv.tv" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.btzx.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "live.snrtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

//                    "www.snrtv.com" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                            }
//                        }
//                    }

                    "www.nmtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.nmgtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.yntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.yntv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.yb983.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.xjtvs.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.xjtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.sxrtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.sxrtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "www.cbg.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.cqtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }

                    "live.kankanews.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.shtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        webView.loadUrl("about:blank")
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
        
        // PERSISTENT RESUME: Check for previously saved position if it's a Movie/VOD
        if (isVODContent()) {
            val savedPos = SP.getVODPosition(url)
            if (savedPos > 0) {
                lastPlaybackPosition = savedPos
            }
        }

        
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
            url.contains("?|")) {
            
            webView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            webView.loadUrl("about:blank") // Stop webview
            
            try {
                initializePlayer(url)
            } catch (e: Exception) {
                tvModel.setErrInfo("Player Init Failed")
            }
            return
        }

        // Not ExoPlayer supported URL, use WebView
        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        releasePlayer()



        val uri = Uri.parse(url)
        when (uri.host) {
            "tv.cctv.com" -> {
                webView.evaluateJavascript(
                    "localStorage.setItem('cctv_live_resolution', '720');",
                    null
                )
            }
        }

    webView.loadUrl(url)
    }

    fun refreshPlayback() {
        val currentTv = tvModel?.tv ?: return
        val isVod = isVODContent()
        
        // HARD RESET: Release player and create new one
        retryCount = 0
        uaFallbackIndex = 0
        
        // Logic Correction: Don't reset position if it's a VOD (preserve progress)
        if (!isVod) {
            lastPlaybackPosition = -1L
        }
        
        bufferingStartTime = -1L

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
        doInitializePlayer(currentVideoUrl, seamless = true, seekPosition = lastPlaybackPosition)
    }

    private fun initializePlayer(url: String) {
        if (context == null) return
        
        // FIX: Ensure a global CookieHandler exists so HttpURLConnection handles Set-Cookie
        // from CDNs (e.g. JioTV/Akamai) perfectly into subsequent .m3u8 or .ts chunk requests.
        if (java.net.CookieHandler.getDefault() == null) {
            val cookieManager = java.net.CookieManager()
            cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER)
            java.net.CookieHandler.setDefault(cookieManager)
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // If we have a saved position (from a VOD hard reset escape), apply it
                val seekPos = if (isVODContent()) lastPlaybackPosition else -1L
                
                // ASYNC PRE-FETCH: If ClearKey URL is present, fetch it now on Dispatchers.IO
                val currentTv = tvModel?.tv
                if (currentTv != null && currentTv.drmScheme?.lowercase() == "clearkey") {
                    var licenseUrl = currentTv.drmLicenseUrl ?: ""
                    if (licenseUrl.startsWith("http")) {
                        // Extract URL from pipes if present
                        if (licenseUrl.contains("|")) {
                            licenseUrl = licenseUrl.split("|")[0]
                        }
                        
                        val fetchedJson = withContext(Dispatchers.IO) {
                            try {
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                val request = Request.Builder().url(licenseUrl).get().build()
                                val response = client.newCall(request).execute()
                                response.body?.string() ?: ""
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to pre-fetch clearkey: ${e.message}")
                                ""
                            }
                        }
                        
                        if (fetchedJson.contains("\"keys\"") || fetchedJson.contains("\"k\"")) {
                            // Update the model temporarily with the actual JSON so doInitializePlayer uses it
                            currentTv.drmLicenseUrl = fetchedJson
                            Log.d(TAG, "Successfully pre-fetched remote ClearKey JSON")
                        }
                    }
                }
                
                doInitializePlayer(url, seamless = false, seekPosition = seekPos)
            } catch (e: Exception) {
                Log.e(TAG, "Playback Init Error: ${e.message}")
                tvModel?.setErrInfo("Playback Error")
                releasePlayer()
            }
        }
    }

    private fun doInitializePlayer(url: String, seamless: Boolean = false, seekPosition: Long = -1L) {
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

        // 1. Load from TV Model (Priority)
        val currentTv = tvModel?.tv
        if (currentTv != null) {
            // Headers
            currentTv.headers?.let { requestHeaders.putAll(it) }
            
            // User-Agent override
            requestHeaders["User-Agent"]?.let { 
                userAgent = it 
                requestHeaders.remove("User-Agent") // Remove from generic headers to avoid duplicate/conflict if set via setter
            }

            // DRM
            if (!currentTv.drmScheme.isNullOrEmpty()) {
                // Pre-parse license URL headers if they exist using the | delimiter
                var rawLicenseUrl = currentTv.drmLicenseUrl ?: ""
                if (rawLicenseUrl.contains("|")) {
                    val parts = rawLicenseUrl.split("|")
                    rawLicenseUrl = parts[0]
                    if (parts.size > 1) {
                        val headerParts = parts[1].split("&")
                        for (h in headerParts) {
                            val kv = h.split("=", limit = 2)
                            if (kv.size == 2) {
                                requestHeaders[kv[0].trim()] = kv[1].trim()
                            }
                        }
                    }
                }
                drmConfig = DrmConfig(currentTv.drmScheme!!, rawLicenseUrl)
            }
        }

        // HARDCODED OPTIMIZATION: Portal/TS Stream Persistence
        if (url.contains("mac=", ignoreCase = true) || url.contains("extension=ts", ignoreCase = true) || url.contains(".ts", ignoreCase = true)) {
            requestHeaders["Connection"] = "keep-alive"
            requestHeaders["Keep-Alive"] = "timeout=60, max=100"
        }
        
        // HARDCODED OPTIMIZATION: SunNxt Proactive Header Injection
        if (url.contains("sunnxt.com")) {
            requestHeaders["Origin"] = "https://www.sunnxt.com"
            requestHeaders["Referer"] = "https://www.sunnxt.com/"
            requestHeaders["sec-ch-ua"] = "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\""
            requestHeaders["sec-ch-ua-mobile"] = "?1"
            requestHeaders["sec-ch-ua-platform"] = "\"Android\""
            requestHeaders["sec-fetch-dest"] = "empty"
            requestHeaders["sec-fetch-mode"] = "cors"
            requestHeaders["sec-fetch-site"] = "same-site"
        }


        // 2. Legacy/URL-based Overrides (Backward Compatibility & Specific overrides)
        val regex = "(?i)(\\?\\|)|(\\?%7C)".toRegex()
        val matchResult = regex.find(url)

        if (matchResult != null) {
            val splitIndex = matchResult.range.first
            videoUrl = url.substring(0, splitIndex)
            val paramsString = url.substring(matchResult.range.last + 1)
            
            val params = paramsString.split("&")
            for (param in params) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    when (key.lowercase()) {
                        "drmscheme" -> {
                             // URL override? Maybe. Let's allow it.
                             val license = params.find { it.startsWith("drmLicense", ignoreCase = true) }?.split("=", limit=2)?.getOrNull(1) ?: ""
                             drmConfig = DrmConfig(value, license)
                        }
                        "user-agent" -> userAgent = value
                        else -> requestHeaders[key] = value // Add other params as headers
                    }
                }
            }
        }


        // OPTIMIZED BUFFER SETTINGS
        val loadControl = getLoadControl(url)
        
        // HARDCODED OPTIMIZATION: SonyLiv Performance Fix
        val nameLower = tvModel?.tv?.name?.lowercase() ?: ""
        val titleLower = tvModel?.tv?.title?.lowercase() ?: ""
        if (nameLower.contains("sony") || nameLower.contains("liv") || 
            titleLower.contains("sony") || titleLower.contains("liv")) {
             userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
             requestHeaders.remove("Cookie")
             requestHeaders.remove("Authorization")
             requestHeaders.remove("Referer") 
        }

        // Use Extension Renderers if available (e.g. FFMpeg) and ENABLE FALLBACK
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(requireContext())
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true) // IMPORTANT: Swaps to software decoder if hardware hangs
            .setEnableAudioTrackPlaybackParams(true) 
            .setEnableAudioFloatOutput(false) // FIRETV FIX: Force 16-bit integer PCM output to prevent floating-point silences
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                )
                
                // Allow hardware decoders for audio first, only try software if they fail during playback
                // Previous forced software decode caused CPU lag on high bitrate 4K streams
                decoders
            }

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
        
        // FIX: Revert to DefaultHttpDataSource for video playback to prevent SecureHttpClient 
        // from aggressively overwriting User-Agent headers or blocking VPNs via NO_PROXY.
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        // HARDCODED OPTIMIZATION: Hotstar Cookie & Origin Sync
        if (url.contains("hotstar.com") || url.contains("livetv.hotstar")) {
            requestHeaders["Origin"] = "https://www.hotstar.com"
            requestHeaders["Referer"] = "https://www.hotstar.com/"
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    requestHeaders["Cookie"] = cookies
                }
            } catch (e: Exception) {}
            httpDataSourceFactory.setDefaultRequestProperties(requestHeaders)
        }


        val hlsExtractorFactory = DefaultHlsExtractorFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
            true
        )

        // ENHANCED EXTRACTOR FACTORY FOR .TS FILES (Multi-Audio / H265 / DD5.1)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or 
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(requireContext(), extractorsFactory)
        mediaSourceFactory.setDataSourceFactory(httpDataSourceFactory)
        mediaSourceFactory.setLoadErrorHandlingPolicy(IPTVLoadErrorHandlingPolicy())

        // FIX: Configure DRM Provider to use our Cookie-enabled DataSource
        val drmProvider = androidx.media3.exoplayer.drm.DrmSessionManagerProvider { mediaItem: androidx.media3.common.MediaItem ->
            
            var schemeUuid = if (drmConfig != null) {
                when (drmConfig?.scheme?.lowercase()) {
                    "widevine" -> C.WIDEVINE_UUID
                    "playready" -> C.PLAYREADY_UUID
                    "clearkey" -> C.CLEARKEY_UUID
                    else -> C.WIDEVINE_UUID
                }
            } else {
                 val drmConf = mediaItem.localConfiguration?.drmConfiguration
                 if (drmConf != null) drmConf.scheme else C.WIDEVINE_UUID
            }

            var licenseUrl = drmConfig?.license ?: mediaItem.localConfiguration?.drmConfiguration?.licenseUri?.toString() ?: ""

            // HARDCODED OPTIMIZATION: Force ClearKey for SunNxt to bypass manifest conflicts
            // Also map the Common PSSH UUID (1077efec...) which is standard for ClearKey DASH.
            val commonPsshUuid = java.util.UUID.fromString("1077efec-c0b2-4d02-ace3-3c1e52e2fb4b")
            if ((schemeUuid == commonPsshUuid || url.contains("sunnxt.com")) && !licenseUrl.startsWith("http") && licenseUrl.isNotEmpty()) {
                schemeUuid = C.CLEARKEY_UUID
            }

            val drmDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(requestHeaders)
                .setAllowCrossProtocolRedirects(true)

            if (schemeUuid == C.CLEARKEY_UUID && licenseUrl.isNotEmpty()) {
                 val clearkeyJson = createClearKeyJson(licenseUrl)
                 if (clearkeyJson.isNotEmpty()) {
                     val drmCallback = LocalMediaDrmCallback(clearkeyJson.toByteArray())
                     DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .setPlayClearSamplesWithoutKeys(true)
                        .build(drmCallback)
                 } else {
                     // Fallback to standard HTTP POST
                     HttpMediaDrmCallback(licenseUrl, drmDataSourceFactory).let { callback ->
                        DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .setMultiSession(true)
                            .setPlayClearSamplesWithoutKeys(true)
                            .build(callback)
                     }
                 }
            } else if (licenseUrl.isNotEmpty()) {
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
                
                // ENHANCED ERROR LOGGING
                android.util.Log.e("PlayerError", "Error Code: ${error.errorCode}, Message: ${error.message}", error.cause)
                
                // CODEC/DECRYPT FAILURE RECOVERY - Check for codec-related errors
                val errorCause = error.cause?.toString() ?: ""
                val isCodecError = errorCause.contains("decrypt") || 
                                   errorCause.contains("MediaCodec") || 
                                   errorCause.contains("Codec") ||
                                   errorCause.contains("decoder")
                
                if (isCodecError && retryCount < 2) {
                    // FIRST ATTEMPT: Codec error likely due to encrypted stream or hardware decoder failure
                    // Log and retry with fresh player (may trigger software decoder fallback)
                    android.util.Log.w("PlayerError", "Codec/Decrypt error detected, forcing retry: ${error.cause}")
                    retryCount++
                    tvModel?.setErrInfo("") // Silent retry for codec issues
                    playbackHandler.postDelayed({
                        if (currentVideoUrl.isNotEmpty()) {
                            initializePlayer(currentVideoUrl) 
                        }
                    }, 2000L)
                    return
                }
                
                // AUTO RETRY & MULTI-URL FALLBACK LOGIC
                if (retryCount < maxRetries) {
                    retryCount++
                    val delay = if(error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) 1000L else 3000L
                    
                    // Improved Logic: Don't show "Retrying" immediately on the first transient error.
                    // This prevents the error screen from flashing on working channels that have a minor network drop.
                    if (retryCount > 1) {
                        tvModel?.setErrInfo("Retrying... ($retryCount/$maxRetries)")
                    } else {
                        tvModel?.setErrInfo("") // Keep silent loading UI
                    }
                    
                    playbackHandler.postDelayed({
                        if (retryCount > 0 && currentVideoUrl.isNotEmpty()) { 
                            // SMART UA ROTATION: Increment UA fallback after some retries or specific errors
                            if (retryCount % 2 == 0 && uaFallbackIndex < 3) {
                                uaFallbackIndex++
                            }
                            // Last-Resort Global Fix: Use MAG Identity if any channel fails 4+ times
                            if (retryCount >= 4 && uaFallbackIndex < 4) {
                                uaFallbackIndex = 4 
                            }
                            initializePlayer(currentVideoUrl) // Re-initialize the same URL
                        }
                    }, delay)
                } else if (currentTv != null && currentUrlIndex < (currentTv.uris.size - 1)) {
                    // TRY NEXT SECONDARY URL FALLBACK
                    currentUrlIndex++
                    retryCount = 0 // Reset retries for the new URL
                    uaFallbackIndex = 0 // Reset UA for new URL
                    val nextUrl = currentTv.uris[currentUrlIndex]
                    tvModel?.setErrInfo("Switching to Backup Stream...")
                    
                    playbackHandler.post {
                        initializePlayer(nextUrl)
                    }
                } else {
                     // All Retries and Fallbacks failed. Check if we should fallback to WebView (Universal Support)
                     tvModel?.setErrInfo("Stream Offline or Unsupported")
                     if (isAdded) {
                         // Persistent error. Trigger a silent force-update in the background in case keys rotated.
                         try {
                             com.codesrahul.exclusivetv.models.TVList.update(mainActivity, silent = true, force = true)
                         } catch (e: Exception) {}

                         val errorUrl = currentVideoUrl
                         if (!errorUrl.isNullOrEmpty() && (errorUrl.startsWith("http") || errorUrl.startsWith("https"))) {
                             if (!isWebMode) {
                                  isWebMode = true
                                  val b = binding ?: return
                                  b.playerView.visibility = View.GONE
                                  b.webView.visibility = View.VISIBLE
                                  releasePlayer()
                                  
                                  val errWebUrl = errorUrl
                                  val errWebUri = Uri.parse(errWebUrl)
                                  if (errWebUri.host == "tv.cctv.com") {
                                      b.webView.evaluateJavascript("localStorage.setItem('cctv_live_resolution', '720');", null)
                                  }
                                  b.webView.loadUrl(errWebUrl)
                             }
                         }
                     }
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
                // Success: Cancel Watchdog
                playbackWatchdog?.let { playbackHandler.removeCallbacks(it) }
                playbackWatchdog = null
                // Potentially reset UA fallback index here or keep it for stability
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
                   (videoUrl.contains(".php", ignoreCase = true) && (videoUrl.contains("id=") || videoUrl.contains("stream") || videoUrl.contains("live")))) &&
                   !videoUrl.contains("extension=ts", ignoreCase = true) &&
                   !videoUrl.contains(".ts", ignoreCase = true)

        val isDash = videoUrl.contains(".mpd", ignoreCase = true) || 
                    (videoUrl.contains("/mpd/", ignoreCase = true)) || 
                    videoUrl.contains("dash", ignoreCase = true)
                    
        val finalMimeType = when {
            isHls -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            isDash -> androidx.media3.common.MimeTypes.APPLICATION_MPD
            else -> null
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(finalMimeType)

        // 3. DRM Configuration (Universal Activation)
        if (drmConfig != null) {
            val uuid = when (drmConfig?.scheme?.lowercase()) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
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
                    .build()
            )
        }

        val mediaItem = mediaItemBuilder.build()

        if (isHls) {
            // UNIVERSAL FIX: Apply robust settings to ALL HLS streams including TS audio flags
            val hlsExtractorFactory = DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
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
        playbackWatchdog?.let { playbackHandler.removeCallbacks(it) }
        playbackWatchdog = object : Runnable {
            override fun run() {
                if (isAdded && exoPlayer != null) {
                    val state = exoPlayer?.playbackState ?: Player.STATE_IDLE
                    val isPlaying = exoPlayer?.playWhenReady == true
                    val currentPos = exoPlayer?.currentPosition ?: -1L
                    val now = System.currentTimeMillis()
                    
                    if (isPlaying && state == Player.STATE_READY) {
                        bufferingStartTime = -1L // Reset buffering clock
                        if (currentPos != -1L && currentPos == lastPlaybackPosition) {
                            // STALL DETECTED: Position frozen for 5s (responsive check)
                            playbackHandler.post { seamlessRefresh() }
                            return 
                        }
                        lastPlaybackPosition = currentPos
                    } else if (isPlaying && state == Player.STATE_BUFFERING) {
                        if (bufferingStartTime == -1L) bufferingStartTime = now
                        if (now - bufferingStartTime > 10000) {
                            // BUFFERING TIMEOUT: Stuck for 10s
                            playbackHandler.post { seamlessRefresh() }
                            return
                        }
                    } else if (state == Player.STATE_IDLE) {
                         // Silent startup hang
                        if (retryCount < maxRetries && lastPlaybackPosition == -1L) {
                            if (uaFallbackIndex < 3) { uaFallbackIndex++ }
                            retryCount++
                            initializePlayer(currentVideoUrl)
                            return
                        }
                    } else if (state == Player.STATE_ENDED) {
                        // VOD COMPLETION: Clear the saved progress if the movie reached the end
                        if (isVODContent()) {
                            SP.setVODPosition(currentVideoUrl, 0)
                        }
                    } else {
                         bufferingStartTime = -1L
                    }
                    
                    // PERIODIC SAVE: Persist VOD position every 15 seconds during playback
                    if (isVODContent() && isPlaying && state == Player.STATE_READY) {
                        if (lastCheckTime == -1L) lastCheckTime = now
                        if (now - lastCheckTime > 15000) { // 15 Second intervals
                            if (currentPos > 0) {
                                SP.setVODPosition(currentVideoUrl, currentPos)
                            }
                            lastCheckTime = now
                        }
                    }
                    
                    // Aggressive periodic check every 3 seconds
                    playbackHandler.postDelayed(this, 3000)
                }
            }
        }
        
        // Reset state for new stream ONLY if it's not a VOD resume
        if (!isVODContent()) {
            lastPlaybackPosition = -1L
        }
        bufferingStartTime = -1L
        playbackHandler.postDelayed(playbackWatchdog!!, 8000) // 8s grace before first check
    }


    private fun getOptimalUserAgent(url: String): String {
        // TIERED FALLBACK SYSTEM: Use rotation index to bypass blocks
        return when (uaFallbackIndex) {
            1 -> "TiviMate/4.7.0 (Linux; Android 11; TV Box Build/RTM1.211111.111)"
            2 -> "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"
            3 -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            4 -> "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200/2.0.4 Safari/533.3"
            else -> {
                // Tier 0: Smart Logic based on Domain
                when {
                    // GENERIC PREMIUM IDENTITY: If it's a DASH stream with DRM, it's almost always 
                    // a restricted stream that needs a Chrome-like browser identity to work.
                    (url.contains(".mpd", ignoreCase = true) || url.contains("/mpd/", ignoreCase = true) || url.contains("dash", ignoreCase = true)) 
                      && (url.contains("drm", ignoreCase = true) || url.contains("license", ignoreCase = true) || tvModel?.tv?.drmScheme != null) ->
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"

                    // IPTV Portals / Global Last Resort (Requires MAG200/MAG250 Identity)
                    uaFallbackIndex == 4 || url.contains("mac=", ignoreCase = true) || url.contains("portal", ignoreCase = true) -> 
                        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200/2.0.4 Safari/533.3"
                    
                    url.contains("drmlive.net") || url.contains("servertvhub.site") || url.contains("workers.dev") -> 
                        "TiviMate/4.7.0 (Linux; Android 11; TV Box Build/RTM1.211111.111)"
                    
                    // OSTV / Tokenized Streams / General TS - TiviMate is the gold standard for compatibility
                    url.contains("ostv.info") || url.contains("token=") || url.endsWith(".ts", ignoreCase = true) -> 
                        "TiviMate/4.7.0 (Linux; Android 11; TV Box Build/RTM1.211111.111)"
        
                    url.contains("googlevideo.com") || url.contains("youtube.com") -> 
                        "com.google.android.youtube/19.05.36 (Linux; U; Android 14; en_US) gzip"
                        
                    url.contains("facebook.com") || url.contains("fbcdn.net") ->
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"
                        
                    url.contains("twitch.tv") ->
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                        
                    else -> "Dalvik/2.1.0 (Linux; U; Android 14; SM-S911B Build/UP1A.231005.007)"
                }
            }
        }
    }

    data class DrmConfig(val scheme: String, val license: String)

    private fun createClearKeyJson(license: String): String {
        val trimmedLicense = license.trim()
        
        // 1. JSON PATH: Support JWK format (M3U KODIPROP or remote fetch)
        if (trimmedLicense.startsWith("{") && trimmedLicense.endsWith("}")) {
            try {
                val jsonObj = JSONObject(trimmedLicense)
                // RECURSIVE SEARCH: Find "keys" array anywhere in the response (Support nested keys)
                val keysArray = findKeysArrayRecursive(jsonObj)
                
                if (keysArray != null) {
                    // SANITIZATION: Create a new object with ONLY the keys array.
                    // This fixes Android 14/Samsung CDM errors caused by "type":"temporary" or other extra fields.
                    val sanitized = JSONObject()
                    sanitized.put("keys", keysArray)
                    Log.d(TAG, "Successfully extracted ${keysArray.length()} keys (including nested) for ClearKey")
                    return sanitized.toString()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error parsing JSON license: ${e.message}")
            }
        }
        
        // 2. LEGACY PATH: Support comma-separated strings (KID1:KEY1,KID2:KEY2)
        val keyEntries = trimmedLicense.split(",")
        val keysArray = JSONArray()

        for (entry in keyEntries) {
            val parts = entry.split(":")
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
        return jsonObject.toString()
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
            // STRIP DASHES & SPACES: SunNxt KIDs are often UUIDs (5EA9...-3B1E...)
            val trimmed = input.trim().replace("-", "").replace(" ", "")
            
            // AUTO-DETECT: If it's already a valid Base64 string ...
            if (trimmed.length in 20..44 && (trimmed.contains("_") || trimmed.contains("/") || trimmed.contains("+") || trimmed.endsWith("="))) {
                val decoded = if (trimmed.contains("_")) {
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
        
        var trackIndex = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: ""
                    val label = format.label ?: if (lang.isNotEmpty()) lang else "Audio ${trackIndex + 1}"
                    tracks.add(AudioTrack(trackIndex, label, group.isTrackSelected(i)))
                    trackIndex++
                }
            }
        }
        return tracks
    }

    fun setAudioTrack(trackIndex: Int) {
        val currentTracks = exoPlayer?.currentTracks ?: return
        var currentIndex = 0
        
        // If trackIndex is -1 (Default/Auto), clear overrides
        if (trackIndex == -1) {
             exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                ?.build() ?: return
             return
        }

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (currentIndex == trackIndex) {
                        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            ?.build() ?: return
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
        _binding = null
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

    private fun getLoadControl(url: String? = null): androidx.media3.exoplayer.LoadControl {
        val bufferMode = SP.bufferMode
        val isVod = isVODContent()
        
        // Mode 0: Default (Balanced)
        // Mode 1: Max Stability (Large buffer for slow net)
        // Mode 2: Low Latency (Small buffer for fast net)

        // SIGNIFICANTLY INCREASED FOR IPTV STABILITY
        var minBuffer = when (bufferMode) {
            1 -> 60000 // 60s
            2 -> 15000 // 15s
            else -> if (isVod) 50000 else 30000 // 50s for VOD, 30s for Live
        }
        // Force minimums for stability
        minBuffer = Math.max(minBuffer, if (isVod) 45000 else 15000)

        var maxBuffer = when (bufferMode) {
            1 -> 150000 // 150s
            2 -> 45000  // 45s
            else -> if (isVod) 120000 else 90000 // 120s for VOD, 90s for Live
        }
        // Force minimum 90s for Global Stability
        maxBuffer = Math.max(maxBuffer, 90000)

        var startBuffer = when (bufferMode) {
            1 -> 5000 // 5s start
            2 -> 1500 // 1.5s start
            else -> if (isVod) 5000 else 2500 // 5s for Movies, 2.5s for Live
        }
        
        val context = context ?: return DefaultLoadControl.Builder().build()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMemGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val isHighEnd = totalMemGb > 3.0 // Raised threshold
        val is4K = url?.contains("420.m3u8") == true || url?.contains("4K", ignoreCase = true) == true || tvModel?.tv?.title?.contains("4K", ignoreCase = true) == true

        // Target Buffer: Optimized for IPTV and 4K Streams
        val targetBufferBytes = if (isHighEnd) {
            if (is4K) 256 * 1024 * 1024 else 128 * 1024 * 1024 
        } else {
            if (is4K) 128 * 1024 * 1024 else 64 * 1024 * 1024
        }
        
        return DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                if (isHighEnd && isVod) 60000 else if (is4K) minBuffer * 2 else minBuffer,
                if (isHighEnd && isVod) 150000 else if (is4K) maxBuffer * 2 else maxBuffer,
                if (is4K) 5000 else startBuffer,
                if (isVod) 10000 else if (is4K) 5000 else 2500 // Re-buffering threshold
            )
            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true) 
            .build()
    }

    private class IPTVLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
            // Exponential backoff for IPTV stability
            val errorCount = loadErrorInfo.errorCount
            if (errorCount > 5) return C.TIME_UNSET // Fallback to URL rotation after 5 internal retries
            
            return Math.min(2000L * Math.pow(2.0, (errorCount - 1).toDouble()).toLong(), 10000L)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            return 5
        }
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
        try {
            val builder = player.trackSelectionParameters.buildUpon()
            if (lang.isNotEmpty()) {
                builder.setPreferredAudioLanguages(lang)
            } else {
                builder.setPreferredAudioLanguages()
            }
            player.trackSelectionParameters = builder.build()
            
            // Show feedback toast if language was changed manually in settings
            if (lang.isNotEmpty()) {
                val locale = java.util.Locale(lang)
                Toast.makeText(requireContext(), "Audio Language: ${locale.displayLanguage}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
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
}
