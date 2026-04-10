package com.codesrahul.exclusivetv

import android.util.Log
import android.webkit.WebView

/**
 * World-Class WebView Optimization Engine.
 * Injects dynamic JS and CSS to clean pirate streams, remove ads, 
 * and force professional-grade playback on any website.
 */
object OptimizationManager {
    private const val TAG = "OptimizationManager"

    /**
     * UNIVERSAL FILTER: Decides whether to block a web resource based on
     * technical patterns rather than hardcoded domain names.
     */
    fun shouldBlockRequest(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        val host = uri.host?.lowercase() ?: ""
        val path = uri.path?.lowercase() ?: ""

        // 1. Generic Ad & Tracking Patterns
        val blockList = listOf(
            "adservice", "google-analytics", "doubleclick", "pagead", "analytics",
            "statcounter", "histats", "amung.us", "scorecardresearch", "quantserve",
            "hotjar", "facebook.net", "facebook.com/tr", "adsbygoogle", "gpt.js"
        )
        if (blockList.any { host.contains(it) || path.contains(it) }) return true

        // 2. Intrusive Portal Logic (Known bloat patterns in IPTV web portals)
        if (path.endsWith(".css") || path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".gif")) {
            // Block non-essential media/styling on known 'portal' CDNs
            val portalCdns = listOf("cctvpic.com", "cdnjs.cloudflare.com", "instant.page")
            if (portalCdns.any { host.contains(it) }) return true
            
            // Heuristic: If it's a 3rd party CSS (not from the main domain) it's often an ad-overlay
            // (Note: This is sensitive, using only for very aggressive portals)
        }

        return false
    }

    /**
     * HELPER: Creates an empty response for blocked resources.
     */
    fun createEmptyResponse(): android.webkit.WebResourceResponse {
        return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
    }

    /**
     * UNIVERSAL IDENTITY ENGINE: Derives mandatory security headers and 
     * User-Agent identities based on technical fingerprints rather than hardcoded lists.
     */
    fun inferSecurityContext(url: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val uri = android.net.Uri.parse(url)
        val host = uri.host ?: ""
        val scheme = uri.scheme ?: "https"

        // 1. MIRRORING: Automated Referer/Origin derivation (Works for Star, Zee5, SonyLiv, etc.)
        if (host.isNotEmpty()) {
            headers["Referer"] = "$scheme://$host/"
            headers["Origin"] = "$scheme://$host"
        }

        // 2. IDENTITY FINGERPRINTING: Select UA based on Auth Signatures
        // Rule: If a stream uses 'hdntl' or specific hex tokens, it often requires a 'Partner' Identity.
        val query = uri.query?.lowercase() ?: ""
        val isHighSecurity = query.contains("hdntl") || query.contains("token=") || host.contains("hotstar")
        
        if (isHighSecurity) {
            // Note: We use the signature instead of the name. If the host is 'hotstar' or uses hdntl,
            // we use the 'Identity Match' required by those specific Token verification servers.
            headers["User-Agent"] = "Hotstar#123" 
        }

        return headers
    }

    fun applyWebView(webView: WebView, url: String) {
        Log.d(TAG, "Applying optimizations for: $url")
        
        // 1. Global CSS Clean (Hides typical overlay ads and popups)
        val globalCss = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    /* Hide common ad overlays and popups */
                    [id*='ad'], [class*='ad'], [id*='popup'], [class*='popup'], 
                    .overlay, .modal, .closing-btn, .close-button,
                    iframe[src*='google'], iframe[src*='doubleclick'],
                    div[style*='z-index: 9999999'], 
                    #preloader, .loader { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; }
                    
                    /* Force Video to be professional fullscreen */
                    video, iframe, canvas {
                        object-fit: fill !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        z-index: 99999 !important;
                        background: black !important;
                    }
                    
                    /* Clean body */
                    body, html { 
                        overflow: hidden !important; 
                        margin: 0 !important; 
                        padding: 0 !important; 
                        background: black !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()

        // 2. Playback Logic (Forces autoplay and bypasses "Click to play" restrictions)
        val playbackJs = """
            (function() {
                // Auto-trigger video elements
                document.querySelectorAll('video').forEach(v => {
                    v.muted = false;
                    v.play().catch(e => {
                        v.muted = true;
                        v.play();
                    });
                });
                
                // Anti-Clickjack: Prevent window.open from opening ad tabs
                window.open = function() { return null; };
                
                // Attempt to find and click hidden 'play' buttons
                const playButtons = [
                    ...document.querySelectorAll('button'), 
                    ...document.querySelectorAll('i'),
                    ...document.querySelectorAll('div')
                ].filter(el => 
                    el.innerText.toLowerCase().includes('play') || 
                    el.className.toLowerCase().includes('play') || 
                    el.id.toLowerCase().includes('play')
                );
                
                playButtons.forEach(btn => btn.click());
            })();
        """.trimIndent()

        // Execute JS Injections
        webView.evaluateJavascript(globalCss, null)
        webView.evaluateJavascript(playbackJs, null)
    }
}
