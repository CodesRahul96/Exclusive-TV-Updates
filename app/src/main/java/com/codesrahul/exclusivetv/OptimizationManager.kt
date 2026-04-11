package com.codesrahul.exclusivetv

import android.util.Log
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * World-Class WebView Optimization Engine.
 * Injects dynamic JS and CSS to clean pirate streams, remove ads, 
 * and force professional-grade playback on any website.
 */
object OptimizationManager {
    private const val TAG = "OptimizationManager"

    private const val DEFAULT_PROFILES_JSON = """
        [
            {
                "signature": ".hotstar.com",
                "origin": "https://www.hotstar.com",
                "referer": "https://www.hotstar.com/",
                "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                "highSecurity": true
            },
            {
                "signature": ".sunnxt.com",
                "origin": "https://www.sunnxt.com",
                "referer": "https://www.sunnxt.com/",
                "userAgent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
                "highSecurity": true
            },
            {
                "signature": "workers.dev/live/",
                "drmScheme": "clearkey",
                "idRegex": "/live/([^/?#.]+)",
                "licenseTemplate": "{host_base}/{id}/key.json"
            },
            {
                "signature": "workers.dev",
                "drmScheme": "clearkey",
                "licenseTemplate": "{base}/key.json"
            }
        ]
    """

    // SHARED IDENTITY TOKENS (Identity Parity with high-fidelity browsers)
    private const val UA_IDENTITY_PORTAL_PRESET = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    const val UA_CHROME_MOBILE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    const val UA_CHROME_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    /**
     * PORTAL REGISTRY: Maps technical signatures to security contexts.
     * This achieves 100% provider-agnosticism by externalizing signatures from core logic.
     */
    data class PortalProfile(
        val signature: String, // String to match in host or path
        val origin: String? = null,
        val referer: String? = null,
        val userAgent: String = UA_CHROME_DESKTOP,
        val highSecurity: Boolean = false,
        val drmScheme: String? = null,
        val idRegex: String? = null,
        val licenseTemplate: String? = null
    )

    private val portalProfiles = java.util.concurrent.CopyOnWriteArrayList<PortalProfile>()

    init {
        // Bootstrap: Load default signatures to ensure basic functionality without network
        loadProfiles(DEFAULT_PROFILES_JSON)
    }

    /**
     * DYNAMIC LOADER: Updates the security registry from Remote Config.
     */
    fun loadProfiles(json: String?) {
        if (json.isNullOrBlank()) return
        try {
            val array = JSONArray(json)
            val newProfiles = mutableListOf<PortalProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                newProfiles.add(PortalProfile(
                    signature = obj.getString("signature"),
                    origin = obj.optString("origin").let { if (it.isEmpty()) null else it },
                    referer = obj.optString("referer").let { if (it.isEmpty()) null else it },
                    userAgent = obj.optString("userAgent", UA_CHROME_DESKTOP),
                    highSecurity = obj.optBoolean("highSecurity", false),
                    drmScheme = obj.optString("drmScheme").let { if (it.isEmpty()) null else it },
                    idRegex = obj.optString("idRegex").let { if (it.isEmpty()) null else it },
                    licenseTemplate = obj.optString("licenseTemplate").let { if (it.isEmpty()) null else it }
                ))
            }
            portalProfiles.clear()
            portalProfiles.addAll(newProfiles)
            Log.d(TAG, "Successfully synchronized ${newProfiles.size} security profiles")
        } catch (e: Exception) {
            Log.e(TAG, "Identity Engine Error: Failed to parse profiles", e)
        }
    }

    /**
     * RUNTIME REGISTRATION: Allows the M3U parser or other components to 
     * register custom provider behavior on the fly.
     */
    fun registerProfile(profile: PortalProfile) {
        // Prevent duplicates
        if (portalProfiles.any { it.signature == profile.signature }) {
             portalProfiles.removeAll { it.signature == profile.signature }
        }
        portalProfiles.add(0, profile) // High priority
        Log.d(TAG, "Registered dynamic profile for: ${profile.signature}")
    }

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
     * User-Agent identities based on technical signatures rather than hardcoded rules.
     */
    fun inferSecurityContext(url: String, groupName: String? = null): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val uri = android.net.Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""
        val scheme = uri.scheme ?: "https"
        val query = uri.query?.lowercase() ?: ""

        // 1. MIRRORING: Automated Referer/Origin derivation (Works for most standard CDNs)
        if (host.isNotEmpty()) {
            headers["Referer"] = "$scheme://$host/"
            headers["Origin"] = "$scheme://$host"
        }

        // 2. SIGNATURE MATCHING: Apply profile if technical fingerprints match
        val matchedProfile = portalProfiles.find { profile ->
            host.contains(profile.signature) || groupName?.contains(profile.signature, ignoreCase = true) == true
        }

        matchedProfile?.let { profile ->
            profile.origin?.let { headers["Origin"] = it }
            profile.referer?.let { headers["Referer"] = it }
            headers["User-Agent"] = profile.userAgent
        }

        // 3. SECURITY ESCALATION: Detect generic high-security tokens (Token-based signatures)
        if (headers["User-Agent"] == null) {
            val hasSecurityTokens = query.contains("hdntl") || query.contains("token=")
            if (hasSecurityTokens || (matchedProfile?.highSecurity == true)) {
                headers["User-Agent"] = UA_CHROME_DESKTOP
            }
        }

        return headers
    }

    /**
     * DYNAMIC DRM ENGINE: Resolves DRM context for streams lacking explicit metadata.
     * Uses template matching and regex extraction for 100% provider-agnosticism.
     */
    fun inferDrmContext(url: String): Pair<String?, String?> {
        val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null } ?: return Pair(null, null)
        val host = uri.host?.lowercase() ?: ""
        val path = uri.path?.lowercase() ?: ""

        val matchedProfile = portalProfiles.find { host.contains(it.signature) || path.contains(it.signature) }

        if (matchedProfile != null && matchedProfile.drmScheme != null) {
            val scheme = matchedProfile.drmScheme
            var template = matchedProfile.licenseTemplate ?: ""
            
            if (template.isEmpty()) return Pair(scheme, null)

            // Dynamic ID Extraction
            var derivedId = ""
            matchedProfile.idRegex?.let { regexStr ->
                try {
                    val regex = regexStr.toRegex()
                    val match = regex.find(url)
                    if (match != null && match.groupValues.size > 1) {
                        derivedId = match.groupValues[1]
                    }
                } catch (e: Exception) {}
            }

            // Template Hydration
            val finalLicense = template
                .replace("{id}", derivedId)
                .replace("{base}", url.substringBeforeLast("/", url))
                .replace("{host_base}", "${uri.scheme}://${uri.host}")

            return Pair(scheme, finalLicense)
        }

        return Pair(null, null)
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
