package com.codesrahul.exclusivetv.models

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringReader

/**
 * World-Class Deep Heuristic JSON Parser.
 * Uses intent-recognition and scoring to extract channel data from ANY JSON structure.
 * Outperforms standard parsers by ignoring fixed schemas and following streaming patterns.
 */
object DeepHeuristicParser {
    private const val TAG = "DeepHeuristicParser"
    private const val MIN_SCORE_THRESHOLD = 30

    // Scoring Keys
    private val URL_HINTS = setOf("url", "link", "stream", "file", "uri", "m3u", "mpd", "play", "source", "data", "cmd", "path", "stream_url", "link_url", "src", "manifest")
    private val NAME_HINTS = setOf("name", "title", "label", "station", "channel", "tvg-name", "caption", "stb_name")
    private val LOGO_HINTS = setOf("logo", "icon", "image", "thumb", "img", "tvg-logo", "pic", "ch_logo", "stb_logo", "thumb_url")
    private val GROUP_HINTS = setOf("group", "cat", "genre", "category", "folder", "stations", "itv_group", "genre_name")
    private val TECHNICAL_HINTS = setOf("quality", "resolution", "res", "bitrate", "codec", "frame", "fps", "lang", "language", "country")
    private val BEHAVIOR_HINTS = setOf("embed", "webview", "iframe", "radio", "audio")
    private val SUBTITLE_HINTS = setOf("sub", "subtitle", "vtt", "srt")

    fun parse(reader: Reader): List<TV> {
        val channels = mutableListOf<TV>()
        try {
            val jsonElement = JsonParser.parseReader(reader)
            val context = ExtractionContext()
            recursiveSearch(jsonElement, context, channels)
        } catch (e: Exception) {
            Log.e(TAG, "Deep Parsing Critical Failure", e)
        }
        return channels.distinctBy { it.uris.firstOrNull() }
    }

    fun parse(jsonString: String): List<TV> {
        return parse(StringReader(jsonString))
    }

    private class ExtractionContext {
        var groupHierarchy = mutableListOf<String>()
        var globalHeaders = mutableMapOf<String, String>()
    }

    private fun recursiveSearch(element: JsonElement, context: ExtractionContext, output: MutableList<TV>) {
        when {
            element.isJsonArray -> {
                element.asJsonArray.forEach { recursiveSearch(it, context, output) }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val score = calculateStreamingScore(obj)
                
                if (score >= MIN_SCORE_THRESHOLD) {
                    val tv = extractChannelFromObject(obj, output.size, context)
                    if (tv != null) {
                        output.add(tv)
                        return // Found a channel, don't need to go deeper into this object
                    }
                }

                // If not a channel, or if it might contain a list of channels inside
                obj.entrySet().forEach { (key, value) ->
                    // Update hierarchy if key looks like a group/category
                    val isGroupKey = GROUP_HINTS.any { key.lowercase().contains(it) }
                    if (isGroupKey && value.isJsonPrimitive) {
                        context.groupHierarchy.add(value.asString)
                    }
                    
                    recursiveSearch(value, context, output)
                    
                    if (isGroupKey && value.isJsonPrimitive && context.groupHierarchy.isNotEmpty()) {
                        context.groupHierarchy.removeAt(context.groupHierarchy.size - 1)
                    }
                }
            }
        }
    }

    private fun calculateStreamingScore(obj: JsonObject): Int {
        var score = 0
        obj.entrySet().forEach { (key, value) ->
            val k = key.lowercase()
            
            // 1. URL Confidence (Highest Priority)
            if (URL_HINTS.any { k.contains(it) }) {
                if (value.isJsonPrimitive) {
                    val v = value.asString.lowercase()
                    if (v.startsWith("http") || v.startsWith("rtmp") || v.contains("://")) {
                        score += 50
                        if (v.contains(".m3u8") || v.contains(".mpd") || v.contains(".m3u")) score += 20
                    }
                }
            }

            // 2. Identity Confidence
            if (NAME_HINTS.any { k.contains(it) }) score += 20
            if (LOGO_HINTS.any { k.contains(it) }) score += 10
            
            // 3. DRM Confidence
            if (k.contains("license") || k.contains("drm") || k.contains("clearkey")) score += 30

            // 4. Metadata Confidence
            if (TECHNICAL_HINTS.any { k.contains(it) }) score += 5
            if (BEHAVIOR_HINTS.any { k.contains(it) }) score += 10
            if (SUBTITLE_HINTS.any { k.contains(it) }) score += 5
        }
        return score
    }

    private fun extractChannelFromObject(obj: JsonObject, index: Int, context: ExtractionContext): TV? {
        val uris = mutableListOf<String>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var drmScheme: String? = null
        var drmLicense: String? = null
        
        var language: String? = null
        var country: String? = null
        var resolution: String? = null
        var bitrate: String? = null
        var frameRate: String? = null
        var videoCodec: String? = null
        var isAudioOnly = false
        var isWebViewEmbed = false
        
        val headers = context.globalHeaders.toMutableMap()

        obj.entrySet().forEach { (key, value) ->
            if (!value.isJsonPrimitive && !value.isJsonObject) return@forEach
            
            val k = key.lowercase()
            val v = if (value.isJsonPrimitive) value.asString else ""

            when {
                // URL Extraction
                URL_HINTS.any { k.contains(it) } && (v.startsWith("http") || v.contains("://")) -> {
                    uris.add(v)
                }
                
                // Name Extraction
                NAME_HINTS.any { k == it } || (name == null && NAME_HINTS.any { k.contains(it) }) -> {
                    if (v.length < 100) name = v
                }

                // Logo Extraction
                LOGO_HINTS.any { k.contains(it) } && v.startsWith("http") -> {
                    logo = v
                }

                // Group Extraction
                GROUP_HINTS.any { k.contains(it) } -> {
                    group = v
                }

                // DRM Extraction
                k.contains("license_type") || k.contains("drm_type") || k.contains("drm_scheme") -> {
                    drmScheme = v.lowercase().let { 
                        if (it.contains("widevine")) "widevine" 
                        else if (it.contains("clearkey")) "clearkey" 
                        else it 
                    }
                }
                k.contains("license_key") || k.contains("license_url") || k == "clearkey" || k.contains("drm_key") || k.contains("drm_license") -> {
                    drmLicense = v
                }
                
                // Metadata Extraction
                k == "lang" || k == "language" -> language = v
                k == "country" -> country = v
                k == "resolution" || k == "quality" -> resolution = v
                k == "bitrate" -> bitrate = v
                k == "frame_rate" || k == "fps" -> frameRate = v
                k == "codec" || k == "video_codec" -> videoCodec = v

                // Header Extraction
                k == "user-agent" || k == "ua" -> headers["User-Agent"] = v
                k == "referer" || k == "referrer" -> headers["Referer"] = v
                k == "cookie" -> headers["Cookie"] = v
                k == "origin" -> headers["Origin"] = v
                k == "authorization" || k == "auth" -> headers["Authorization"] = v
                k == "token" || k == "access_token" -> headers["Token"] = v
                
                // Behavioral Discovery
                BEHAVIOR_HINTS.any { k.contains(it) } -> {
                    if (k.contains("audio") || k.contains("radio")) isAudioOnly = v.toBoolean()
                    if (k.contains("embed") || k.contains("webview")) isWebViewEmbed = v.toBoolean()
                }
                
                // Nested Headers Object
                k == "headers" && value.isJsonObject -> {
                    value.asJsonObject.entrySet().forEach { (hk, hv) ->
                        if (hv.isJsonPrimitive) headers[hk] = hv.asString
                    }
                }
            }
        }

        if (uris.isEmpty()) return null
        
        // Final Refinements
        val finalName = name ?: "Channel $index"
        val finalGroup = group ?: context.groupHierarchy.lastOrNull() ?: "General"
        
        // Auto-detect DRM scheme if license exists but scheme is missing
        if (drmLicense != null && drmScheme == null) {
            val primaryUrl = uris.first().lowercase()
            drmScheme = when {
                primaryUrl.contains(".mpd") -> "widevine"
                primaryUrl.contains(".m3u8") -> "clearkey"
                else -> null
            }
        }

        return TV(
            id = index,
            apiId = "h_${finalName.hashCode()}",
            name = finalName,
            title = finalName,
            logo = logo ?: "",
            uris = ArrayList(uris),
            headers = if (headers.isNotEmpty()) HashMap(headers) else null,
            group = finalGroup,
            type = Type.STREAM,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            language = language,
            country = country,
            resolution = resolution,
            bitrate = bitrate,
            frameRate = frameRate,
            videoCodec = videoCodec,
            genre = group ?: context.groupHierarchy.lastOrNull(),
            isAudioOnly = isAudioOnly,
            isWebViewEmbed = isWebViewEmbed,
            audioFormats = setOf(),
            compatibleDevices = setOf("androidtv", "mobile"),
            child = emptyList()
        )
    }
}
