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



import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class WebFragment : Fragment() {
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

    data class AudioTrack(val index: Int, val name: String, val isSelected: Boolean)

    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        webView = binding.webView
        playerView = binding.playerView

        val application = requireActivity().applicationContext as MyTVApplication
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

        webView.setOnTouchListener { v, event ->
            if (event != null) {
                (activity as MainActivity).gestureDetector.onTouchEvent(event)
            }
            true
        }

        (activity as MainActivity).ready(TAG)
        
        // Force ExoPlayer Fill Mode
        playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        
        return binding.root
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var retryCount = 0
    private var currentUrlIndex = 0
    private val maxRetries = 10
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize WakeLock
        // Initialize WakeLock & Keep Screen On
        try {
            playerView.keepScreenOn = true
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
        currentUrlIndex = 0 // Reset URL index
        val url = tvModel.videoUrl.value ?: return
        this.currentVideoUrl = url

        
        savedAudioTrackToApply = SP.getAudioTrack(url)

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
        val currentTv = tvModel ?: return
        // Cancel any pending retries first
        playbackHandler.removeCallbacksAndMessages(null)
        // Use a short delay to ensure network stacks are fully ready
        playbackHandler.postDelayed({
            play(currentTv)
        }, 1000)
    }

    private fun initializePlayer(url: String) {
        try {
            doInitializePlayer(url)
        } catch (e: Exception) {
            tvModel?.setErrInfo("Playback Error")
            releasePlayer()
        }
    }

    private fun doInitializePlayer(url: String) {
        // Always release the previous player to ensure we can configure DRM correctly for the new content
        releasePlayer()

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
                drmConfig = DrmConfig(currentTv.drmScheme!!, currentTv.drmLicenseUrl ?: "")
            }
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

        // FORCE FIX FOR SONYLIV / SONY CHANNELS
        // These channels often fail if "bad" headers (like API Cookies or mobile UAs) are sent.
        // We enforce the Desktop Chrome UA which is known to work (same as Source Config default).
        val nameLower = currentTv?.name?.lowercase() ?: ""
        val titleLower = currentTv?.title?.lowercase() ?: ""
        if (nameLower.contains("sony") || nameLower.contains("liv") || 
            titleLower.contains("sony") || titleLower.contains("liv")) {
             userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
             requestHeaders.remove("Cookie")
             requestHeaders.remove("Authorization")
             requestHeaders.remove("Referer") // Sometimes Referer breaks it too if not exact
        }

        // OPTIMIZED BUFFER SETTINGS
        val loadControl = getLoadControl()
        
        // Use Extension Renderers if available (e.g. FFMpeg) and ENABLE FALLBACK
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(requireContext())
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true) // IMPORTANT: Swaps to software decoder if hardware hangs

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
        
        val httpDataSourceFactory = OkHttpDataSource.Factory(SecureHttpClient.client)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)
        

        // HOTSTAR FIX: Ensure Origin/Referer are set correctly
        if (url.contains("hotstar.com") || url.contains("livetv.hotstar")) {
            // Clean up Origin (Hotstar is picky about trailing slashes)
            requestHeaders["Origin"] = "https://www.hotstar.com"
            requestHeaders["Referer"] = "https://www.hotstar.com/"
            
            // Sync with WebView CookieManager if needed (Best Effort)
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url)
                if (!cookies.isNullOrEmpty() && !requestHeaders.containsKey("Cookie")) {
                    requestHeaders["Cookie"] = cookies
                }
            } catch (e: Exception) {
            }

            // Update factory with new headers
            httpDataSourceFactory.setDefaultRequestProperties(requestHeaders)
        }

        val hlsExtractorFactory = DefaultHlsExtractorFactory(
            1 or 8, // FLAG_ALLOW_NON_IDR_KEYFRAMES (1) | FLAG_DETECT_ACCESS_UNIT_DELIMITERS (8)
            true
        )

        // ENHANCED EXTRACTOR FACTORY FOR .TS FILES (Multi-Audio / H265 / DD5.1)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(requireContext(), extractorsFactory)
        mediaSourceFactory.setDataSourceFactory(httpDataSourceFactory)

        // FIX: Configure DRM Provider to use our Cookie-enabled DataSource
        mediaSourceFactory.setDrmSessionManagerProvider { mediaItem: androidx.media3.common.MediaItem ->
            // Use local variable capture for configuration
            val schemeUuid = if (drmConfig != null) {
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

            // Prepare DataSource for DRM (reuse main factory with headers)
            val drmDataSourceFactory = OkHttpDataSource.Factory(SecureHttpClient.client)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(requestHeaders)

            // Logic for License URL
            val licenseUrl = drmConfig?.license ?: mediaItem.localConfiguration?.drmConfiguration?.licenseUri?.toString() ?: ""

            if (schemeUuid == C.CLEARKEY_UUID && !licenseUrl.startsWith("http")) {
                 // Local ClearKey
                 val drmCallback = LocalMediaDrmCallback(createClearKeyJson(licenseUrl).toByteArray())
                 DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
            } else {
                 // Remote License (Widevine/PlayReady or Remote ClearKey)
                 val drmCallback = HttpMediaDrmCallback(licenseUrl, drmDataSourceFactory)
                 
                 // Pass specific headers if manually set (already in requestHeaders, but ensure)
                 for ((k, v) in requestHeaders) {
                     drmCallback.setKeyRequestProperty(k, v)
                 }

                 DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
            }
        }
        
        builder.setMediaSourceFactory(mediaSourceFactory)
        
        exoPlayer = builder.build()

        // Logic Correction: "Force High Quality" should ENABLE High Quality (No Limit), not restrict to SD.
        // If the toggle is ON, we want MAX resolution.
        // If the toggle is OFF, we might want to save data (SD)?
        // For now, let's assume the user wants the BEST quality by default.
        if (!SP.forceHighQuality) {
            // If High Quality is FORCED OFF (i.e. User wants Data Saver), we cap at SD
            val trackSelectionParameters = exoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.setMaxVideoSizeSd() 
                ?.build()
            if (trackSelectionParameters != null) {
                exoPlayer?.trackSelectionParameters = trackSelectionParameters
            }
        } else {
             // Ensure no limits suitable for HD/4K
             val trackSelectionParameters = exoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.clearVideoSizeConstraints()
                ?.build()
             if (trackSelectionParameters != null) {
                exoPlayer?.trackSelectionParameters = trackSelectionParameters
             }
        }

        // Apply Default Audio Language Preference
        val defaultLang = SP.defaultAudioLanguage
        if (defaultLang.isNotEmpty() && exoPlayer != null) {
             try {
                 val currentParams = exoPlayer!!.trackSelectionParameters
                 val newParams = currentParams
                    .buildUpon()
                    .setPreferredAudioLanguages(defaultLang)
                    .build()
                 exoPlayer!!.trackSelectionParameters = newParams
             } catch (e: Exception) {
             }
        }
        
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                
                // AUTO RETRY LOGIC
                if (retryCount < maxRetries) {
                    retryCount++
                    
                    val delay = if(error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) 1000L else 3000L
                    
                    // Improved Logic: Don't show "Retrying" immediately on the first transient error.
                    // This prevents the error screen from flashing on working channels that have a minor hiccup.
                    if (retryCount > 1) {
                        tvModel?.setErrInfo("Retrying... ($retryCount/$maxRetries)")
                    } else {
                        // First retry - keep silent (looks like loading)
                        tvModel?.setErrInfo("") 
                    }
                    
                    playbackHandler.postDelayed({
                        if (retryCount > 0 && currentVideoUrl.isNotEmpty()) { 
                            initializePlayer(currentVideoUrl)
                        }
                    }, delay)
                } else if (currentTv != null && currentUrlIndex < (currentTv.uris.size - 1)) {
                    // TRY NEXT URL FALLBACK
                    currentUrlIndex++
                    retryCount = 0
                    val nextUrl = currentTv.uris[currentUrlIndex]
                    tvModel?.setErrInfo("Trying Backup Link...")
                    
                    playbackHandler.post {
                        initializePlayer(nextUrl)
                    }
                } else {
                     // Check if we should fallback to WebView (Universal Support)
                     if (isAdded) {
                         val errorUrl = currentVideoUrl
                         if (!errorUrl.isNullOrEmpty() && (errorUrl.startsWith("http") || errorUrl.startsWith("https"))) {
                             if (!isWebMode) {
                                  isWebMode = true
                                  val b = binding ?: return
                                  b.playerView.visibility = View.GONE
                                  b.webView.visibility = View.VISIBLE
                                  releasePlayer()
                                  
                                  // Re-apply WebView settings if needed
                                  val errWebUrl = errorUrl
                                  val errWebUri = Uri.parse(errWebUrl)
                                  
                                  // Some site-specifics might need re-triggering
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
                        // Success Signal: Explicitly mark as ready for MainActivity to hide loader
                        tvModel?.setErrInfo("success") 
                        retryCount = 0 // Reset retry count on success
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
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                super.onVideoSizeChanged(videoSize)
                val height = videoSize.height
                val label = when {
                    height >= 2160 -> "4K"
                    height >= 1440 -> "2K"
                    height >= 1080 -> "FHD"
                    height >= 720 -> "HD"
                    else -> "SD"
                }
                tvModel?.setVideoQuality(label)
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
        val isHls = videoUrl.contains(".m3u8", ignoreCase = true) || 
                   (videoUrl.contains(".php", ignoreCase = true) && (videoUrl.contains("id=") || videoUrl.contains("stream") || videoUrl.contains("live")))

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(if (isHls) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null)
            .build()

        if (isHls) {
            // UNIVERSAL FIX: Apply robust settings to ALL HLS streams
            val hlsExtractorFactory = DefaultHlsExtractorFactory(
                1, // FLAG_ALLOW_NON_IDR_KEYFRAMES (1)
                true
            )
            val hlsMediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpDataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setAllowChunklessPreparation(false) // Strict Sync for stability on all streams
                .createMediaSource(mediaItem)
            exoPlayer?.setMediaSource(hlsMediaSource)
        } else {
            exoPlayer?.setMediaItem(mediaItem)
        }
        
        exoPlayer?.prepare()
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
    }


    private fun getOptimalUserAgent(url: String): String {
        return when {
            // SPECIAL HANDLING: IPTV Providers blocking standard browsers
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
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
    }

    data class DrmConfig(val scheme: String, val license: String)

    private fun createClearKeyJson(license: String): String {
        // License format: keyId:key
        val parts = license.split(":")
        val keyIdHex = parts[0]
        val keyHex = parts[1]

        val keyIdBase64 = hexToBase64Url(keyIdHex)
        val keyBase64 = hexToBase64Url(keyHex)

        val keyObject = JSONObject()
        keyObject.put("kty", "oct")
        keyObject.put("k", keyBase64)
        keyObject.put("kid", keyIdBase64)

        val keysArray = JSONArray()
        keysArray.put(keyObject)

        val jsonObject = JSONObject()
        jsonObject.put("keys", keysArray)
        jsonObject.put("type", "temporary")

        return jsonObject.toString()
    }

    private fun hexToBase64Url(hex: String): String {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            val j = Integer.parseInt(hex.substring(index, index + 2), 16)
            bytes[i] = j.toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
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

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        exoPlayer?.release()
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
        if (exoPlayer == null && playerView.visibility == View.VISIBLE) {
            tvModel?.let { play(it) }
        }
        webView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
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

    private fun getLoadControl(): androidx.media3.exoplayer.LoadControl {
        val bufferMode = SP.bufferMode
        
        // Mode 0: Default (Balanced)
        // Mode 1: Max Stability (Large buffer for slow net)
        // Mode 2: Low Latency (Small buffer for fast net)

        val minBuffer = when (bufferMode) {
            1 -> 30000 // 30s
            2 -> 5000  // 5s
            else -> 15000 // Increased default to 15s to prevent pausing
        }

        val maxBuffer = when (bufferMode) {
            1 -> 60000 // 60s
            2 -> 15000 // 15s
            else -> 60000 // Increased default to 60s
        }

        val startBuffer = when (bufferMode) {
            1 -> 2500 // 2.5s start
            2 -> 1000 // 1s start
            else -> 1000 // Optimized default: 1s start (was 2000)
        }
        
        // DYNAMIC BUFFER SIZING (Professional Solution)
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMemGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val isHighEnd = totalMemGb > 2.0
        
        // Target Buffer: 128MB for High-End, 50MB for Low-End
        val targetBufferBytes = if (isHighEnd) {
            128 * 1024 * 1024 
        } else {
            50 * 1024 * 1024
        }
        
        // Priority: Time for High-End (Smoothness), Size for Low-End (Stability)
        val prioritizeTime = isHighEnd

        return DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                if (isHighEnd) 30000 else minBuffer, // 30s min for High-End (Safe for 3GB RAM @ 50Mbps)
                if (isHighEnd) 50000 else maxBuffer, // 50s max for High-End
                startBuffer,
                2500 
            )
            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(prioritizeTime) 
            .build()
    }

    fun isPlaying(): Boolean {
        return try {
            exoPlayer?.isPlaying == true || (getCurrentUrl()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }
}
