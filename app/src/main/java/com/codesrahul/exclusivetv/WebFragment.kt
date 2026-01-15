package com.codesrahul.exclusivetv

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        super.onViewCreated(view, savedInstanceState)

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

                    "besttllapp.online"-> webView.evaluateJavascript(context.resources.openRawResource(R.raw.snx)
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

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val url = tvModel.videoUrl.value as? String ?: return

        Log.i(TAG, "play ${tvModel.tv.title} $url")
        
        savedAudioTrackToApply = SP.getAudioTrack(url)
        Log.i(TAG, "Saved audio track to apply: $savedAudioTrackToApply")

        if (url.endsWith(".m3u8", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) ||
            url.endsWith(".mpd", ignoreCase = true) ||
            url.startsWith("rtmp://") || url.startsWith("rtsp://") || url.contains("?|")) {
            
            webView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            webView.loadUrl("about:blank") // Stop webview
            
            initializePlayer(url)
            return
        }

        // Not ExoPlayer supported URL, use WebView
        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        releasePlayer()

        if (url.contains("yupptv.com") || url.contains("athavantv.com") || url.contains("ttn.tv") || url.contains("youtube.com")) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = performNetworkRequest(url)
                if(result!=null){
                    withContext(Dispatchers.Main) {
                        val encodedUrl = java.net.URLEncoder.encode(result, "UTF-8")
                        webView.loadUrl("file:///android_asset/tll_player.html?channel=$encodedUrl")
                    }
                }
            }
            return
        }

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
        // Always release the previous player to ensure we can configure DRM correctly for the new content
        releasePlayer()

        var videoUrl = url
        var drmConfig: DrmConfig? = null
        val requestHeaders = mutableMapOf<String, String>()
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
                            if (value.lowercase() == "clearkey") {
                                // Will parse license later or store it now
                            }
                        }
                        "drmlicense" -> {
                             // Check if it's currently set to clearkey logic above, simplified here:
                             // We re-check params logic or just store it. 
                             // Let's iterate again or store in map first? 
                             // Better to iterate once.
                        }
                        "user-agent" -> userAgent = value
                        "cookie" -> requestHeaders["Cookie"] = value
                        "referer" -> requestHeaders["Referer"] = value
                        "origin" -> requestHeaders["Origin"] = value
                        "x-forwarded-for" -> requestHeaders["X-Forwarded-For"] = value
                        // Add other headers if needed
                    }
                }
            }
            
            // Re-parse for DRM specifically to keep existing logic structure or adapt it
            val queryParams = params.associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
            }
            
            // Case insensitive lookup for DRM
            val schemeKey = queryParams.keys.find { it.equals("drmScheme", ignoreCase = true) }
            val licenseKey = queryParams.keys.find { it.equals("drmLicense", ignoreCase = true) }

            if (schemeKey != null && queryParams[schemeKey]?.lowercase() == "clearkey") {
                 val drmLicense = queryParams[licenseKey]
                 if (drmLicense != null) {
                     drmConfig = DrmConfig("clearkey", drmLicense)
                 }
            }
        }

        val builder = ExoPlayer.Builder(requireContext())
        
        // Configure Data Source with Headers
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)
        
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(requireContext())
            .setDataSourceFactory(httpDataSourceFactory)

        if (drmConfig != null && drmConfig.scheme == "clearkey") {
            Log.d(TAG, "Configuring ClearKey DRM with license: ${drmConfig.license}")
            val drmCallback = LocalMediaDrmCallback(createClearKeyJson(drmConfig.license).toByteArray())
            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(drmCallback)
            
            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
        }
        
        builder.setMediaSourceFactory(mediaSourceFactory)
        
        exoPlayer = builder.build()

        if (SP.forceHighQuality) {
            val trackSelectionParameters = exoPlayer?.trackSelectionParameters
                ?.buildUpon()
                ?.setMaxVideoSizeSd() // Start with SD as baseline
                ?.setForceHighestSupportedBitrate(true)
                ?.build()
            if (trackSelectionParameters != null) {
                exoPlayer?.trackSelectionParameters = trackSelectionParameters
            }
        }
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e(TAG, "ExoPlayer Error: ${error.message}", error)
                tvModel?.setErrInfo("Player Error: ${error.message}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                 if (playbackState == Player.STATE_READY) {
                        tvModel?.setErrInfo("") // Clear error info on successful play
                 }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                super.onVideoSizeChanged(videoSize)
                val height = videoSize.height
                val label = when {
                    height >= 2160 -> "4K"
                    height >= 1440 -> "2K"
                    height >= 1080 -> "1080p"
                    height >= 720 -> "720p"
                    height >= 480 -> "480p"
                    height > 0 -> "SD"
                    else -> ""
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
        // Simulated network operation
        val m3u8Link = null
        try {

// Create the request
            val request = url?.let {
                Request.Builder()
                    .url(it)
                    .build()
            }

            // Execute the request
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    val regex = """src:\s*"(https://.*?\.m3u8.*?)"""".toRegex()
                    // Find matches
                    val match = responseBody?.let { regex.find(it) }

                    if (match != null) {
                        val m3u8Link = match.groupValues[1]
                        println("Extracted m3u8 Link: $m3u8Link")
                        return m3u8Link
                    }else{
                        // specifically for athavantv
                        val athavantvRegex = """file:"(https?://[^\"]+\.m3u8)"""".toRegex()
                        val matchResult = athavantvRegex.find(responseBody)
                        val hlsLink = matchResult?.groups?.get(1)?.value

                        if (hlsLink != null) {
                            println("Extracted HLS Link: $hlsLink")
                            return hlsLink
                        }else{
                            val ttnregex = """source:\s*['"]([^'"]+\.m3u8)['"]""".toRegex()

                            val matchResult1 = ttnregex.find(responseBody)
                            val ttnhlsLink = matchResult1?.groups?.get(1)?.value

                            if (ttnhlsLink != null) {
                                println("Extracted HLS Link: $ttnhlsLink")
                                return ttnhlsLink
                            } else {
                                // youtube
                                val youtubeRegex = """"hlsManifestUrl":"(https?:\/\/[^"]+\.m3u8)"""".toRegex()

                                // Extracting the match
                                val matchResult2 = youtubeRegex.find(responseBody)
                                val hlsLink2 = matchResult2?.groups?.get(1)?.value

                                // Output the result
                                if (hlsLink2 != null) {
                                    println("Extracted HLS Link: $hlsLink2")
                                    return hlsLink2
                                } else {
                                    println("No HLS link found.")
                                }
                            }
                        }
                    }

                }


            }
        } catch (e: IOException) {
            println("Error during the API call: ${e.message}")
        }
        return m3u8Link
    }
}
