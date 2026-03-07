package com.codesrahul.exclusivetv.models

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringReader

object GenericJsonParser {
    private const val TAG = "GenericJsonParser"

    private val URL_KEYS = setOf(
        "url", "stream_url", "play_url", "m3u8_url", "mpd_url", "uri", "link", "file", 
        "stream", "m3u_link", "m3u8_link", "mpd_link", "stream_link"
    )
    private val NAME_KEYS = setOf("name", "title", "channel_name", "station", "tvg-name")
    private val LOGO_KEYS = setOf("logo", "icon", "image", "thumbnail", "tvg-logo", "logo_url")
    private val GROUP_KEYS = setOf(
        "group", "category", "group_title", "groupTitle", "group-title", "channel_group", 
        "channel-group", "channel_group_title", "channel-group-title", "channel_group_name", 
        "channel-group-name", "channel_category", "channel-category", "channel_category_title", 
        "channel-category-title", "channel_category_name", "channel-category-name", "genre"
    )
    private val ID_KEYS = setOf("id", "channel_id", "tvg-id", "internal_id")
    private val HEADER_KEYS = setOf("headers", "http_headers")
    private val DRM_LICENSE_KEYS = setOf("license_key", "drm_license", "drm_url", "license_url", "key", "drm_license_url", "drmLicense")
    private val DRM_SCHEME_KEYS = setOf("drm_scheme", "drm_type", "drmScheme")
    private val USER_AGENT_KEYS = setOf("user_agent", "user-agent", "ua")
    private val REFERER_KEYS = setOf("referer", "referrer")

    // FAST PATH: Optimized streaming parser for large lists
    fun parse(reader: Reader): List<TV> {
        val list = mutableListOf<TV>()
        val jsonReader = JsonReader(reader)
        jsonReader.isLenient = true

        try {
            val peek = jsonReader.peek()
            if (peek == JsonToken.BEGIN_ARRAY) {
                parseArray(jsonReader, list)
            } else if (peek == JsonToken.BEGIN_OBJECT) {
                // Check if root object has a known array field "channels", "list", etc.
                jsonReader.beginObject()
                // RECURSIVE SEARCH: We now delegate to a helper that can go deep
                parseObjectRecursive(jsonReader, list)
                jsonReader.endObject()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { jsonReader.close() } catch (e: Exception) {}
        }
        return list
    }

    // New Recursive Helper
    private fun parseObjectRecursive(reader: JsonReader, list: MutableList<TV>) {
        while (reader.hasNext()) {
             val name = reader.nextName()
             val token = reader.peek()
             
             if (token == JsonToken.BEGIN_ARRAY) {
                 // EXPANDED KEY LIST
                 if (name.contains("channel") || name.contains("list") || name.contains("streams") ||
                     name == "data" || name == "result" || name == "cats" || name == "categories" || name == "msg") {
                      parseArray(reader, list)
                 } else {
                      // Fallback: Check if it's an unnamed array of objects? 
                      // For now, let's skip unknown arrays to avoid junk
                      reader.skipValue()
                 }
             } else if (token == JsonToken.BEGIN_OBJECT) {
                 // RECURSION: If we find an object (e.g. "data": { ... }), go inside!
                 reader.beginObject()
                 parseObjectRecursive(reader, list) // Go deeper
                 reader.endObject()
             } else {
                 reader.skipValue()
             }
        }
    }

    // Overload for String compatibility
    fun parse(jsonString: String): List<TV> {
        return parse(StringReader(jsonString))
    }

    private fun parseArray(reader: JsonReader, list: MutableList<TV>) {
        reader.beginArray()
        var index = list.size
        while (reader.hasNext()) {
            try {
                val token = reader.peek()
                if (token == JsonToken.BEGIN_OBJECT) {
                    val tv = parseSingleObjectStreaming(reader, index)
                    if (tv != null) {
                         list.add(tv)
                         index++
                    }
                } else if (token == JsonToken.BEGIN_ARRAY) {
                     // Nested array? Flatten it.
                     parseArray(reader, list) // Recursion
                } else {
                    reader.skipValue()
                }
            } catch (e: Exception) {
                // If one item fails, skip it and continue
                try { reader.skipValue() } catch (e2: Exception) {}
            }
        }
        reader.endArray()
    }

    // Optimized Single Object Parser - Reads fields directly without DOM
    private fun parseSingleObjectStreaming(reader: JsonReader, index: Int): TV? {
        var url: String? = null
        var name: String? = null
        var group: String? = null
        var logo: String? = null
        var drmLicense: String? = null
        var drmScheme: String? = null
        var headers: MutableMap<String, String>? = null
        var idStr: String? = null
        
        var type: com.codesrahul.exclusivetv.models.Type? = null
        var catchupType: String? = null
        var catchupDays: String? = null
        var catchupSource: String? = null
        val uris = mutableListOf<String>()
        
        reader.beginObject()
        while (reader.hasNext()) {
            val rawKey = reader.nextName()
            val key = rawKey.lowercase()
            val token = reader.peek()
            
            if (token == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            
            when {
                URL_KEYS.contains(key) -> {
                    var singleUrl = reader.nextString()
                    singleUrl = decodeBase64IfFound(singleUrl)
                    if (singleUrl.isNotEmpty() && !singleUrl.contains("://")) {
                        val skey = com.codesrahul.exclusivetv.SecretManager.getAppKey()
                        val decrypted = com.codesrahul.exclusivetv.SecurityUtil.decryptChannelData(singleUrl, skey)
                        if (decrypted.contains("://")) singleUrl = decrypted
                    }
                    if (singleUrl.isNotEmpty()) uris.add(singleUrl)
                }
                key == "uris" || key == "urls" || key == "streams" || key == "stream_list" -> {
                    if (token == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val innerToken = reader.peek()
                            if (innerToken == JsonToken.STRING) {
                                var nextUrl = reader.nextString()
                                nextUrl = decodeBase64IfFound(nextUrl)
                                if (nextUrl.isNotEmpty() && !nextUrl.contains("://")) {
                                    val skey = com.codesrahul.exclusivetv.SecretManager.getAppKey()
                                    val decrypted = com.codesrahul.exclusivetv.SecurityUtil.decryptChannelData(nextUrl, skey)
                                    if (decrypted.contains("://")) nextUrl = decrypted
                                }
                                if (nextUrl.isNotEmpty()) uris.add(nextUrl)
                            } else if (innerToken == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val innerKey = reader.nextName().lowercase()
                                    if (URL_KEYS.contains(innerKey)) {
                                        var innerUrl = reader.nextString()
                                        innerUrl = decodeBase64IfFound(innerUrl)
                                        if (innerUrl.startsWith("http")) uris.add(innerUrl)
                                    } else reader.skipValue()
                                }
                                reader.endObject()
                            } else reader.skipValue()
                        }
                        reader.endArray()
                    } else reader.skipValue()
                }
                NAME_KEYS.contains(key) -> name = reader.nextString()
                GROUP_KEYS.contains(key) -> group = reader.nextString()
                LOGO_KEYS.contains(key) -> logo = reader.nextString()
                ID_KEYS.contains(key) -> {
                    if (key == "internal_id" || token == JsonToken.NUMBER) {
                        try { idStr = reader.nextInt().toString() } catch (e: Exception) { idStr = reader.nextString() }
                    } else idStr = reader.nextString()
                }
                key == "type" -> {
                    val typeStr = reader.nextString()
                    type = when (typeStr.uppercase()) {
                        "WEB" -> com.codesrahul.exclusivetv.models.Type.WEB
                        "HLS" -> com.codesrahul.exclusivetv.models.Type.HLS
                        "DASH", "MPD" -> com.codesrahul.exclusivetv.models.Type.STREAM
                        "STREAM" -> com.codesrahul.exclusivetv.models.Type.STREAM
                        else -> null 
                    }
                }
                key.startsWith("catchup") -> {
                    when (key) {
                        "catchup_type", "catchup-type" -> catchupType = reader.nextString()
                        "catchup_days", "catchup-days" -> catchupType = reader.nextString()
                        "catchup_source", "catchup-source" -> catchupSource = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                HEADER_KEYS.contains(key) -> {
                    if (token == JsonToken.BEGIN_OBJECT) {
                        if (headers == null) headers = mutableMapOf()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val hKey = reader.nextName()
                            val hVal = reader.nextString()
                            headers[normalizeHeaderKey(hKey)] = hVal
                        }
                        reader.endObject()
                    } else reader.skipValue()
                }
                DRM_LICENSE_KEYS.contains(key) -> drmLicense = reader.nextString()
                DRM_SCHEME_KEYS.contains(key) -> drmScheme = reader.nextString()
                USER_AGENT_KEYS.contains(key) -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["User-Agent"] = reader.nextString()
                }
                REFERER_KEYS.contains(key) -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["Referer"] = reader.nextString()
                }
                key == "cookie" -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["Cookie"] = reader.nextString()
                }
                else -> {
                    // HEURISTIC FALLBACK: If we haven't found strict high-confidence keys yet,
                    // check if this unknown field looks like a URL or Name.
                    if (token == JsonToken.STRING) {
                        var value = reader.nextString()
                        
                        // Try decrypting unknown text just in case it's a raw encrypted stream url 
                        // missing a proper JSON key
                        if (url == null && !value.contains("://") && value.length > 20 && !value.startsWith("{")) {
                             val key = com.codesrahul.exclusivetv.SecretManager.getAppKey()
                             val decrypted = com.codesrahul.exclusivetv.SecurityUtil.decryptChannelData(value, key)
                             if (decrypted.contains("://")) {
                                 value = decrypted
                             }
                        }

                        if (url == null && (value.startsWith("http") || value.startsWith("rtmp"))) {
                            url = value // Found a potential stream link
                        } else if (url == null) {
                            // REGEX FALLBACK: Brute force search for URL in unknown string
                            val urlRegex = Regex("https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
                            val match = urlRegex.find(value)
                            if (match != null) {
                                url = match.value
                            }
                        }

                        if (url != null && uris.isEmpty()) {
                             uris.add(url!!)
                        }
                        
                        if (name == null && value.length < 60 && !value.startsWith("http") && !value.startsWith("{")) {
                             // Assuming reasonable name length and not JSON or URL
                             name = value
                        }
                    } else {
                        reader.skipValue()
                    }
                }
            }
        }
        reader.endObject()

        if (uris.isEmpty()) return null
        val primaryUrl = uris[0]

        val finalId = idStr?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: index
        val apiId = idStr ?: finalId.toString()
        val finalName = (name ?: "Channel $index").trim()
        val finalGroup = group ?: ""
        
        // Auto-detect DRM if not explicit
        if (drmScheme == null && drmLicense != null) {
            when {
                drmLicense.contains("clearkey", ignoreCase = true) -> drmScheme = "clearkey"
                drmLicense.contains("widevine", ignoreCase = true) -> drmScheme = "widevine"
                drmLicense.contains("playready", ignoreCase = true) -> drmScheme = "playready"
                drmLicense.length > 50 && drmLicense.contains(":") -> drmScheme = "clearkey"
            }
        }
        
        // Final Type Logic: Use parsed type if available, else fallback to detection
        val finalType = type ?: when {
            primaryUrl.contains(".m3u8") -> com.codesrahul.exclusivetv.models.Type.HLS
            primaryUrl.contains(".mpd") -> com.codesrahul.exclusivetv.models.Type.STREAM
            else -> com.codesrahul.exclusivetv.models.Type.STREAM
        }

        return TV(
            id = finalId,
            apiId = apiId,
            name = finalName,
            title = finalName,
            description = "",
            logo = logo ?: "",
            image = "",
            uris = uris,
            headers = headers,
            group = finalGroup,
            type = finalType,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            child = listOf()
        )
    }

    // LEGACY: Keep this for compatibility if explicit DOM parsing is needed elsewhere
    // But it is no longer used for the main channel loops
    fun parseSingleObject(obj: JsonObject, index: Int): TV? {
         // Re-implement simplified version if strictly needed by other classes
         // For now, redirecting to string parser is safer if someone passes a JsonObject
         return null 
    }
    
    private fun normalizeHeaderKey(key: String): String {
        return when (key.lowercase().replace("_", "-")) {
            "cookie" -> "Cookie"
            "user-agent" -> "User-Agent"
            "referer", "referrer" -> "Referer"
            "origin" -> "Origin"
            "authorization" -> "Authorization"
            "content-type" -> "Content-Type"
            "accept" -> "Accept"
            else -> key 
        }
    }

    private fun decodeBase64IfFound(input: String): String {
        if (input.length > 20 && !input.contains(" ") && !input.contains("://") && input.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            try {
                val decoded = String(android.util.Base64.decode(input, android.util.Base64.DEFAULT))
                if (decoded.contains("://") || decoded.startsWith("http")) {
                    return decoded
                }
            } catch (e: Exception) {
                // Not base64 or failed to decode
            }
        }
        return input
    }
}
