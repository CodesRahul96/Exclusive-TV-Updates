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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
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
    val client = OkHttpClient()
    private var tvModel: TVModel? = null
    private var savedAudioTrackToApply: Int = -1

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
    private var retryCount = 0
    private val maxRetries = 10
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize WakeLock
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ExclusiveTV:PlayerWakeLock")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock Init Failed", e)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
//                    Log.e(
//                        "WebViewConsole",
//                        "Message: ${consoleMessage.message()}, Source: ${consoleMessage.sourceId()}, Line: ${consoleMessage.lineNumber()}"
//                    )

                    if (consoleMessage.message() == "success") {
                        Log.e(TAG, "success")
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
                handler.proceed()
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

//                Log.i(TAG, "${request?.method} ${uri.toString()} ${request?.requestHeaders}")
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
                Log.e(TAG, "uri ${uri.host}")
                when (uri.host) {
                    "tv.cctv.com" -> webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                        .bufferedReader()
                        .use { it.readText() }) { value ->
                        if (value == "success") {
                            Log.e(TAG, "success")
                        }
                    }

                    "www.tvmalaysia.live"-> webView.evaluateJavascript(context.resources.openRawResource(R.raw.tvmalaysia)
                        .bufferedReader()
                        .use { it.readText() }) { value ->
                        if (value == "success") {
                            Log.e(TAG, "success")
                        }
                    }



                    "www.gdtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.setv.sh.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.gdtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.yangshipin.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ysp)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.sztv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "news.hbtv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }
//                    "www.ahtv.cn" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                                Log.e(TAG, "success")
//                            }
//                        }
//                    }
                    "www.nxtv.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "tv.gxtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "live.fjtv.net" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "tc.hnntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.hebtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "live.mgtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.hnntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
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
                                        Log.e(TAG, "success")
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
//                                Log.e(TAG, "success")
//                            }
//                        }
//                    }

                    "cricktv.site"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "l455o.com"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "filemoon.nl"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "filemoon.sx"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.moon)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "{}") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "tapmadtv.live"-> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.gdtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.8088yyy.news" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.jxtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                                Log.e(TAG, "success")
//                            }
//                        }
                    }

                    "www.gzstv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.cztv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.jlntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

//                    "v.iqilu.com" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                                Log.e(TAG, "success")
//                            }
//                        }
//                    }

                    "www.qhbtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.qhtb.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.hljtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "static.hntv.tv" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.btzx.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "live.snrtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

//                    "www.snrtv.com" -> {
//                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
//                            .bufferedReader()
//                            .use { it.readText() }) { value ->
//                            if (value == "success") {
//                                Log.e(TAG, "success")
//                            }
//                        }
//                    }

                    "www.nmtv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.nmgtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.yntv.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.yntv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.yb983.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.ahtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.xjtvs.com.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.xjtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.sxrtv.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.sxrtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "www.cbg.cn" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.cqtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }

                    "live.kankanews.com" -> {
                        webView.evaluateJavascript(context.resources.openRawResource(R.raw.shtv)
                            .bufferedReader()
                            .use { it.readText() }) { value ->
                            if (value == "success") {
                                Log.e(TAG, "success")
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        Log.i(TAG, "stop")
        webView.loadUrl("about:blank")
        releasePlayer()
        retryCount = 0 // Stop retrying
        playerView.visibility = View.GONE
        webView.visibility = View.GONE
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        retryCount = 0 // Reset for new channel
        val url = tvModel.videoUrl.value ?: return
        this.currentVideoUrl = url

        Log.i(TAG, "play ${tvModel.tv.title} $url")
        
        savedAudioTrackToApply = SP.getAudioTrack(url)
        Log.i(TAG, "Saved audio track to apply: $savedAudioTrackToApply")

        // Check if explicit type forces Player, or if URL detected as stream
        val isStreamType = tvModel.tv.type == com.codesrahul.exclusivetv.models.Type.STREAM || 
                           tvModel.tv.type == com.codesrahul.exclusivetv.models.Type.HLS
                           
        if (isStreamType || 
            url.endsWith(".m3u8", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) ||
            url.endsWith(".mpd", ignoreCase = true) ||
            url.startsWith("rtmp://") || url.startsWith("rtsp://") || url.contains("?|")) {
            
            webView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            webView.loadUrl("about:blank") // Stop webview
            
            try {
                initializePlayer(url)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize player for $url", e)
                tvModel.setErrInfo("Player Init Failed")
            }
            return
        }

        // Not ExoPlayer supported URL, use WebView
        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        releasePlayer()



        val uri = Uri.parse(url)
        Log.e(TAG, "uri ${uri.host}")
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

    private fun initializePlayer(url: String) {
        try {
            doInitializePlayer(url)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during initializePlayer", e)
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

        currentVideoUrl = url
        var videoUrl = url
        var drmConfig: DrmConfig? = null
        val requestHeaders = mutableMapOf<String, String>()
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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

        // OPTIMIZED BUFFER SETTINGS
        val loadControl = getLoadControl()

        val builder = ExoPlayer.Builder(requireContext())
            .setLoadControl(loadControl)
        
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)
        
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(requireContext())
            .setDataSourceFactory(httpDataSourceFactory)

        // DRM Configuration
        if (drmConfig != null) {
            val schemeUuid = when (drmConfig.scheme.lowercase()) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
                else -> C.WIDEVINE_UUID // Default
            }

            val drmSessionManager = if (schemeUuid == C.CLEARKEY_UUID && !drmConfig.license.startsWith("http")) {
                // Local ClearKey (Identity/JSON)
                Log.d(TAG, "Configuring Local ClearKey DRM")
                val drmCallback = LocalMediaDrmCallback(createClearKeyJson(drmConfig.license).toByteArray())
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
            } else {
                // Remote License (Widevine/PlayReady or Remote ClearKey)
                Log.d(TAG, "Configuring Remote DRM: ${drmConfig.scheme} @ ${drmConfig.license}")
                val drmCallback = HttpMediaDrmCallback(drmConfig.license, DefaultHttpDataSource.Factory())
                
                // Pass headers to license request if needed (e.g. Auth tokens)
                for ((k, v) in requestHeaders) {
                    drmCallback.setKeyRequestProperty(k, v)
                }
                
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(schemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
            }
            
            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
        }
        
        builder.setMediaSourceFactory(mediaSourceFactory)
        
        exoPlayer = builder.build()

        if (SP.forceHighQuality) {
            val trackSelectionParameters = exoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.setMaxVideoSizeSd() // Start with SD
                ?.build()
            if (trackSelectionParameters != null) {
                exoPlayer?.trackSelectionParameters = trackSelectionParameters
            }
        }
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e(TAG, "ExoPlayer Error: ${error.message}", error)
                
                // AUTO RETRY LOGIC
                if (retryCount < maxRetries) {
                    retryCount++
                    val delay = if(error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) 1000L else 3000L
                    tvModel?.setErrInfo("Retrying... ($retryCount/$maxRetries)")
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (retryCount > 0 && currentVideoUrl.isNotEmpty()) { 
                            Log.i(TAG, "Retrying playback (Re-initializing)...")
                            initializePlayer(currentVideoUrl)
                        }
                    }, delay)
                } else {
                     // Try fallback to next source
                     if (tvModel?.nextVideoUrl() == true) {
                         Log.i(TAG, "Switching to next source...")
                         tvModel?.setErrInfo("Switching Source...")
                         retryCount = 0 // Reset for new source
                         val nextUrl = tvModel?.videoUrl?.value
                         if (!nextUrl.isNullOrEmpty()) {
                             initializePlayer(nextUrl)
                         }
                     } else {
                        tvModel?.setErrInfo("Channel Not Available")
                     }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                 if (playbackState == Player.STATE_READY) {
                        tvModel?.setErrInfo("") 
                        retryCount = 0 // Reset retry count on success
                 }
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
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                        val format = group.getTrackFormat(0)
                        val channels = format.channelCount
                        audioLabel = when (channels) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1ch"
                            8 -> "7.1ch"
                            else -> if (channels > 0) "${channels}ch" else ""
                        }
                        // Optional: Check for Dolby
                        val mime = format.sampleMimeType
                        if (mime == androidx.media3.common.MimeTypes.AUDIO_AC3 || 
                            mime == androidx.media3.common.MimeTypes.AUDIO_E_AC3) {
                            audioLabel = if (audioLabel.isNotEmpty()) "$audioLabel Dolby" else "Dolby"
                        }
                        break // Found the selected audio track
                    }
                }
                tvModel?.setAudioQuality(audioLabel)
            }
        })

        playerView.player = exoPlayer
        
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
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
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
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
                val body = response.body()?.string()
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
            Log.e(TAG, "Error during the API call: ${e.message}")
        }
        return null
    }

    fun getCurrentUrl(): String? {
        return currentVideoUrl
    }

    private fun getLoadControl(): androidx.media3.exoplayer.LoadControl {
        val bufferMode = SP.bufferMode
        Log.i(TAG, "Initializing Player with Buffer Mode: $bufferMode")
        
        // Mode 0: Default (Balanced)
        // Mode 1: Max Stability (Large buffer for slow net)
        // Mode 2: Low Latency (Small buffer for fast net)

        val minBuffer = when (bufferMode) {
            1 -> 30000 // 30s
            2 -> 5000  // 5s
            else -> 15000 // 15s
        }

        val maxBuffer = when (bufferMode) {
            1 -> 60000 // 60s
            2 -> 15000 // 15s
            else -> 50000 // 50s
        }

        val startBuffer = when (bufferMode) {
            1 -> 5000 // 5s start
            2 -> 1000 // 1s start
            else -> 2500 // 2.5s start
        }
        
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                startBuffer,
                5000 // Rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}
