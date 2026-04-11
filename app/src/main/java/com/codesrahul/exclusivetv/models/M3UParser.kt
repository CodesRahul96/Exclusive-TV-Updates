package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.util.ArrayList
import java.util.HashMap
import com.codesrahul.exclusivetv.OptimizationManager
import org.json.JSONObject

/**
 * TiviMate-Grade Smart M3U Parser.
 * Robustly extracts metadata from diverse IPTV sources without hardcoded rules.
 * Handles: #EXTINF, #EXTHTTP, #KODIPROP, #EXTVLCOPT, Pipe-Headers, and Query-Headers.
 */
object M3UParser {
    private const val TAG = "M3UParser"
    // Regex for key="value" or key=value
    private val PROP_REGEX = Regex("([a-zA-Z0-9\\-_]+)=(?:\"([^\"]*)\"|([^, ]+))")

    fun parse(reader: BufferedReader): List<TV> {
        val channels = mutableListOf<TV>()
        var line: String?

        // Current Channel State
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentTvgId = ""
        var currentHeaders = mutableMapOf<String, String>()
        var currentDrmScheme: String? = null
        var currentDrmLicense: String? = null
        var currentMimeType: String? = null
        var currentCatchupType: String? = null
        var currentCatchupDays: String? = null
        var currentCatchupSource: String? = null
        
        // Advanced Metadata
        var currentLanguage: String? = null
        var currentCountry: String? = null
        var currentResolution: String? = null
        var currentBitrate: String? = null
        var currentFrameRate: String? = null
        var currentVideoCodec: String? = null
        
        var isAudioOnly = false
        var isWebViewEmbed = false
        var currentGenre: String? = null
        val currentSubtitles = mutableListOf<SubtitleTrack>()
        
        var channelCount = 0
        val currentUris = mutableListOf<String>()
        val globalHeaders = mutableMapOf<String, String>()
        
        // GLOBAL TEMPLATE STATE (For leading tags)
        var globalDrmScheme: String? = null
        var globalDrmLicense: String? = null
        var globalMimeType: String? = null

        fun saveAndReset() {
            if (currentUris.isNotEmpty()) {
                // Priority Merging: Global Template < Tag-Level < URL-Pipe-Level
                val finalHeaders = globalHeaders.toMutableMap()
                
                // Merge current headers with global template headers (if any were found before first #EXTINF)
                finalHeaders.putAll(currentHeaders)

                // TEMPLATE MERGE: If current channel is missing DRM, use global template DRM (from top of file)
                val finalDrmScheme = currentDrmScheme ?: globalDrmScheme
                val finalDrmLicense = currentDrmLicense ?: globalDrmLicense
                val finalMime = currentMimeType ?: globalMimeType

                // SMART AUTO-DRM: If we have a license but no scheme, detect from URL/Headers
                var resolvedDrmScheme = finalDrmScheme
                if (finalDrmLicense != null && resolvedDrmScheme == null) {
                    val url = currentUris.firstOrNull()?.lowercase() ?: ""
                    resolvedDrmScheme = when {
                        url.contains(".mpd") || url.contains("dash") || finalMime?.contains("mpd") == true || finalMime?.contains("dash") == true -> "widevine"
                        url.contains("m3u8") || url.contains("hls") || finalMime?.contains("m3u8") == true || finalMime?.contains("hls") == true -> "clearkey"
                        else -> null
                    }
                }

                // HYPER-PARITY FIX: If MIME indicates DASH/MPD, force isAudioOnly to false
                // This resolves the 'audio symbol' issue for naked streams that specify manifest_type=mpd
                val isActuallyDash = currentUris.any { it.contains(".mpd") } || finalMime?.contains("dash") == true || finalMime?.contains("mpd") == true
                if (isActuallyDash) {
                    isAudioOnly = false
                }

                // FIX for inputstream.adaptive: format the stream_headers string for DASH/DRM
                if (finalHeaders.isNotEmpty() && (resolvedDrmScheme != null || isActuallyDash)) {
                    val builder = StringBuilder()
                    finalHeaders.forEach { (k, v) ->
                        if (builder.isNotEmpty()) builder.append("|")
                        builder.append(k).append("|").append(v)
                    }
                    if (builder.isNotEmpty()) {
                        finalHeaders["inputstream.adaptive.stream_headers"] = builder.toString()
                    }
                }

                val finalId = if (currentTvgId.isNotEmpty()) currentTvgId else "id_${currentName.hashCode()}"

                channels.add(createTV(
                    channelCount++, finalId, currentName, currentLogo, currentGroup, 
                    currentUris, finalHeaders, resolvedDrmScheme, finalDrmLicense,
                    currentCatchupType, currentCatchupDays, currentCatchupSource,
                    currentLanguage, currentCountry, currentResolution, currentBitrate,
                    currentFrameRate, currentVideoCodec, isAudioOnly, isWebViewEmbed,
                    currentGenre, finalMime
                ))

                // Reset per-channel state
                currentName = ""; currentLogo = ""; currentGroup = ""; currentTvgId = ""
                currentHeaders = mutableMapOf(); currentDrmScheme = null; currentDrmLicense = null
                currentMimeType = null; currentCatchupType = null; currentCatchupDays = null; currentCatchupSource = null
                currentLanguage = null; currentCountry = null; currentResolution = null
                currentBitrate = null; currentFrameRate = null; currentVideoCodec = null
                isAudioOnly = false; isWebViewEmbed = false; currentGenre = null
                currentSubtitles.clear()
                currentUris.clear()
            }
        }

        try {
            while (reader.readLine().also { line = it } != null) {
                var trimmedLine = line?.trim() ?: continue
                if (trimmedLine.isEmpty()) continue

                if (trimmedLine.startsWith("# ") || trimmedLine.startsWith("#  ")) {
                    trimmedLine = "#" + trimmedLine.substring(1).trimStart()
                }

                when {
                    trimmedLine.startsWith("#EXTM3U") -> {
                        parsePropertiesToMap(trimmedLine, globalHeaders)
                    }
                    trimmedLine.startsWith("#EXTINF:") -> {
                        if (currentUris.isNotEmpty()) {
                            saveAndReset()
                        }
                        
                        val lastComma = trimmedLine.lastIndexOf(',')
                        if (lastComma != -1) {
                            currentName = trimmedLine.substring(lastComma + 1).trim()
                        }
                        
                        val propsPart = if (lastComma != -1) trimmedLine.substring(0, lastComma) else trimmedLine
                        val props = mutableMapOf<String, String>()
                        parsePropertiesToMap(propsPart, props)
                        
                        currentTvgId = props["tvg-id"] ?: props["id"] ?: props["channel-id"] ?: ""
                        currentLogo = props["tvg-logo"] ?: props["logo"] ?: props["icon"] ?: ""
                        currentGroup = props["group-title"] ?: props["group"] ?: props["category"] ?: ""
                        if (props.containsKey("tvg-name")) currentName = props["tvg-name"]!!
                        
                        // Localization
                        currentLanguage = props["tvg-language"] ?: props["language"] ?: props["lang"]
                        currentCountry = props["tvg-country"] ?: props["country"]
                        currentGenre = props["tvg-genre"] ?: props["genre"]
                        
                        // Behavior Flags
                        isAudioOnly = props["audio"]?.toBoolean() ?: props["radio"]?.toBoolean() ?: false
                        isWebViewEmbed = props["embed"]?.toBoolean() ?: props["webview"]?.toBoolean() ?: false
                        
                        // Technical Specs
                        currentResolution = props["resolution"] ?: props["quality"]
                        currentFrameRate = props["frame-rate"] ?: props["fps"]
                        currentBitrate = props["bitrate"]
                        currentVideoCodec = props["video-codec"] ?: props["codec"]
                        
                        // Logo with Header unrolling (url|Referer=...)
                        currentLogo = props["tvg-logo"] ?: props["logo"] ?: props["icon"] ?: ""
                        if (currentLogo.contains("|")) {
                            val logoParts = currentLogo.split("|", limit = 2)
                            currentLogo = logoParts[0]
                            logoParts[1].split("&").forEach { pair ->
                                val kv = pair.split("=", limit = 2)
                                if (kv.size == 2) currentHeaders[normalizeHeaderKey(kv[0])] = kv[1]
                            }
                        }
                        
                        // Headers as props
                        props["user-agent"]?.let { currentHeaders["User-Agent"] = it }
                        props["referer"]?.let { currentHeaders["Referer"] = it }
                        props["cookie"]?.let { currentHeaders["Cookie"] = it }
                        
                        currentCatchupType = props["catchup"] ?: props["catchup-type"]
                        currentCatchupDays = props["catchup-days"] ?: props["timeshift"]
                        currentCatchupSource = props["catchup-source"]
                    }
                    trimmedLine.startsWith("#EXTGRP:") -> {
                        currentGroup = trimmedLine.substringAfter(":").trim()
                    }
                    trimmedLine.startsWith("#EXTHTTP:") -> {
                        if (currentUris.isNotEmpty()) {
                            saveAndReset()
                        }
                        // Support for JSON-formatted headers (Common in mixed-source playlists)
                        val json = trimmedLine.substringAfter(":").trim()
                        try {
                            val obj = JSONObject(json)
                            val headerObj = obj.optJSONObject("headers") ?: obj
                            headerObj.keys().forEach { k ->
                                currentHeaders[normalizeHeaderKey(k)] = headerObj.getString(k)
                            }
                        } catch (e: Exception) {}
                    }
                    trimmedLine.startsWith("#EXT-X-STREAM-INF:") -> {
                        // This indicates the FOLLOWING line is a manifest variant, not just a raw stream.
                        // We can use this to set a hint for the mimeType.
                        currentMimeType = "application/x-mpegURL"
                        parsePropertiesToMap(trimmedLine, currentHeaders)
                    }
                    trimmedLine.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES") -> {
                        // Extract External Subtitle Track
                        val props = mutableMapOf<String, String>()
                        parsePropertiesToMap(trimmedLine, props)
                        val subUri = props["uri"]
                        val subName = props["name"] ?: props["language"] ?: "Subtitle"
                        val subLang = props["language"]
                        if (!subUri.isNullOrEmpty()) {
                            currentSubtitles.add(SubtitleTrack(
                                language = subLang ?: "und",
                                languageName = subName,
                                url = subUri,
                                format = "vtt" // Default for HLS
                            ))
                        }
                    }
                    trimmedLine.startsWith("#KODIPROP:") || trimmedLine.startsWith("#EXTVLCOPT:") -> {
                        if (currentUris.isNotEmpty()) {
                            saveAndReset()
                        }
                        
                        // Cross-Compatibility for Kodi and VLC style properties (Headers/DRM)
                        val parts = trimmedLine.substringAfter(":").split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            
                            // If channelCount is 0 and name is empty, these might be global template tags
                            val isGlobalPhase = channelCount == 0 && currentName.isEmpty()
                            
                            processTagProperty(key, value, currentHeaders, 
                                { drm -> 
                                    currentDrmScheme = drm
                                    if (isGlobalPhase) globalDrmScheme = drm
                                }, 
                                { lic -> 
                                    currentDrmLicense = lic
                                    if (isGlobalPhase) globalDrmLicense = lic
                                }, 
                                { mime -> 
                                    currentMimeType = mime
                                    if (isGlobalPhase) globalMimeType = mime
                                }
                            )
                        }
                    }
                    !trimmedLine.startsWith("#") -> {
                        // SANITIZATION: Verify it's actually a URL (contains protocol or is a valid file path)
                        // This ignores decorative lines like "------- TATA PLAY -------------"
                        if (!trimmedLine.contains("://") && !trimmedLine.contains("/") && !trimmedLine.contains("\\")) {
                            continue
                        }

                        if (currentUris.isNotEmpty() && currentName.isEmpty()) {
                             // This is a naked URL following another naked URL with no metadata in between
                             saveAndReset()
                        }
                        // This is a Stream URL
                        var finalUrl = trimmedLine
                        
                        // TiviMate Support: Pipe Header Extraction (|Header=Value&Header2=Value2)
                        if (finalUrl.contains("|")) {
                            val parts = finalUrl.split("|", limit = 2)
                            finalUrl = parts[0].trim()
                            val headersStr = parts[1]
                            headersStr.split("&").forEach { pair ->
                                val kv = pair.split("=", limit = 2)
                                if (kv.size == 2) currentHeaders[normalizeHeaderKey(kv[0])] = kv[1]
                            }
                        }
                        
                        // TiviMate Support: Query String Header Extraction (?user-agent=...&referer=...)
                        if (finalUrl.contains("?")) {
                            val query = finalUrl.substringAfter("?")
                            query.split("&").forEach { pair ->
                                val kv = pair.split("=", limit = 2)
                                if (kv.size == 2) {
                                    val k = kv[0].lowercase()
                                    if (k == "user-agent" || k == "referer" || k == "cookie" || k == "origin") {
                                        currentHeaders[normalizeHeaderKey(k)] = kv[1]
                                    }
                                }
                            }
                        }



                        // 3. UNIVERSAL GROUPING HEURISTIC: Derive group from domain if missing
                        if (currentGroup.isEmpty()) {
                            val uri = android.net.Uri.parse(finalUrl)
                            val host = uri.host?.lowercase() ?: ""
                            if (host.isNotEmpty()) {
                                currentGroup = host.split(".")
                                    .filter { it != "www" && it != "com" && it != "net" && it != "org" && it != "tv" && it != "pages" && it != "dev" }
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                    .trim()
                                    
                                if (currentGroup.isEmpty() && host.isNotEmpty()) {
                                    currentGroup = host.replaceFirstChar { it.uppercase() }
                                }
                            }
                        }

                        currentUris.add(finalUrl)
                        
                        // HYPER-PARITY HEURISTIC: Force video mode for DASH manifests
                        if (finalUrl.contains(".mpd", ignoreCase = true)) {
                            isAudioOnly = false
                        }

                        if (currentName.isEmpty()) {
                            currentName = extractNameFromUrl(finalUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "M3U Smart Parsing Error", e)
        }
        
        saveAndReset()
        return channels
    }

    private fun parsePropertiesToMap(line: String, map: MutableMap<String, String>) {
        PROP_REGEX.findAll(line).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
            map[key] = value
        }
    }

    private fun processTagProperty(key: String, value: String, headers: MutableMap<String, String>, setDrm: (String) -> Unit, setLicense: (String) -> Unit, setMime: (String) -> Unit) {
        val k = key.lowercase()
        when {
            k.contains("license_type") || k.contains("drm-scheme") -> {
                setDrm(when(value.lowercase()) {
                    "com.widevine.alpha", "widevine" -> "widevine"
                    "org.w3.clearkey", "clearkey" -> "clearkey"
                    "com.microsoft.playready", "playready" -> "playready"
                    else -> value
                })
            }
            k.contains("license_key") || k.contains("license_url") || k == "clearkey" -> {
                // Support both direct URL and pipe-delimited headers in license field
                if (value.contains("|")) {
                    val parts = value.split("|")
                    setLicense(parts[0])
                    if (parts.size > 1) {
                         parts[1].split("&").forEach { pair ->
                             val kv = pair.split("=", limit = 2)
                             if (kv.size == 2) headers[normalizeHeaderKey(kv[0])] = kv[1]
                         }
                    }
                } else {
                    setLicense(value)
                }
            }
            k == "manifest_type" || k == "inputstream.adaptive.manifest_type" -> {
                setMime(when(value.lowercase()) {
                    "mpd", "dash" -> "application/dash+xml"
                    "m3u8", "hls" -> "application/x-mpegURL"
                    else -> value
                })
            }
            k == "user-agent" || k == "http-user-agent" || k == "useragent" -> headers["User-Agent"] = value
            k == "referer" || k == "http-referer" || k == "referrer" -> headers["Referer"] = value
            k == "cookie" || k == "http-cookie" -> headers["Cookie"] = value
            k == "origin" -> headers["Origin"] = value
            k == "exclusivetv.drm_profile" -> {
                // Format: scheme|signature|regex|template
                val p = value.split("|")
                if (p.size >= 4) {
                    val scheme = p[0].trim()
                    val signature = p[1].trim()
                    val regex = p[2].trim()
                    val template = p[3].trim()
                    OptimizationManager.registerProfile(OptimizationManager.PortalProfile(
                        signature = signature,
                        drmScheme = scheme,
                        idRegex = regex,
                        licenseTemplate = template
                    ))
                }
            }
            else -> {
                // Pass through other Kodi/VLC properties as headers if they aren't internal plugin keys
                // Special handling for stream_headers which is often used in Kodi
                if (k == "stream_headers" || k == "inputstream.adaptive.stream_headers") {
                     value.split("|").forEach { pair ->
                         val kv = pair.split("=", limit = 2)
                         if (kv.size == 2) headers[normalizeHeaderKey(kv[0].trim())] = kv[1].trim()
                     }
                } else if (!k.startsWith("inputstream")) {
                    headers[normalizeHeaderKey(key)] = value
                }
            }
        }
    }

    private fun normalizeHeaderKey(key: String): String {
        return when (key.lowercase().trim()) {
            "user-agent", "ua", "http-user-agent", "useragent" -> "User-Agent"
            "referer", "referrer", "http-referer" -> "Referer"
            "cookie", "http-cookie" -> "Cookie"
            "origin" -> "Origin"
            "authorization" -> "Authorization"
            "token" -> "Token"
            "x-forwarded-for" -> "X-Forwarded-For"
            else -> key
        }
    }

    private fun extractNameFromUrl(url: String): String {
        return try {
            val segments = url.substringBefore('?').split('/').filter { it.isNotEmpty() }
            
            // Try to find a meaningful path segment (e.g., /mp1/CH_NAME/index.mpd)
            var rawName = when {
                segments.size >= 2 && segments.last().contains("index") -> segments[segments.size - 2]
                segments.isNotEmpty() -> segments.last().substringBeforeLast('.')
                else -> ""
            }

            if (rawName.length <= 1) return ""

            // 1. SEMANTIC FORENSICS: Strip CDN Noise (Hex strings, timestamps, hashes)
            // Example: "57ae0b6fa2b64281984574d406f9a696" -> Strip
            // Example: "1540057075" -> Strip
            val noiseRegex = "^[a-f0-9]{8,}$|^[a-f0-9]{32,}$|^\\d{10,}$".toRegex()
            if (noiseRegex.matches(rawName.lowercase())) {
                // If the last segment was noise, try the previous one
                if (segments.size >= 3 && segments.last().contains("index")) {
                    rawName = segments[segments.size - 3]
                } else if (segments.size >= 2) {
                    rawName = segments[segments.size - 2]
                }
            }

            // 2. CLEANING: Replace separators with spaces
            var cleanName = rawName.replace('_', ' ').replace('-', ' ').trim()

            // 3. CAMEL CASE SPLITTING: Insert spaces in "StarPlusHD" -> "Star Plus HD"
            val camelRegex = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()
            cleanName = cleanName.replace(camelRegex, " ")

            // 4. NORMALIZATION: Capitalize words
            cleanName.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { 
                it.lowercase().replaceFirstChar { char -> char.uppercase() } 
            }
        } catch (e: Exception) { "" }
    }

    private fun createTV(
        id: Int, apiId: String, name: String, logo: String, group: String, uris: List<String>, 
        headers: Map<String, String>, drmScheme: String?, drmLicense: String?, 
        catchupType: String?, catchupDays: String?, catchupSource: String?,
        language: String?, country: String?, resolution: String?, bitrate: String?,
        frameRate: String?, videoCodec: String?, isAudioOnly: Boolean, 
        isWebViewEmbed: Boolean, genre: String?, mimeType: String?
    ): TV {
        val finalName = name.ifEmpty { "ExclusiveTV ${id + 1}" }
        return TV(
            id = id,
            apiId = apiId,
            name = finalName,
            title = finalName,
            logo = logo,
            uris = ArrayList(uris),
            headers = if (headers.isNotEmpty()) HashMap(headers) else null,
            group = group,
            type = Type.STREAM,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            language = language,
            country = country,
            resolution = resolution,
            bitrate = bitrate,
            frameRate = frameRate,
            videoCodec = videoCodec,
            isAudioOnly = isAudioOnly,
            isWebViewEmbed = isWebViewEmbed,
            genre = genre,
            audioFormats = setOf(),
            compatibleDevices = setOf("androidtv", "mobile"),
            child = emptyList(),
            mimeType = mimeType
        )
    }
}
