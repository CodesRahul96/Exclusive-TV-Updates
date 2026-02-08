package com.codesrahul.exclusivetv.models

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GenericJsonParser {
    private const val TAG = "GenericJsonParser"

    fun parse(reader: java.io.Reader): List<TV> {
        val list = mutableListOf<TV>()
        try {
            val element = JsonParser.parseReader(reader)
            findAndProcessArrays(element, list)
        } catch (e: Exception) {
        }
        return list
    }

    // Overload for String compatibility
    fun parse(jsonString: String): List<TV> {
        return parse(java.io.StringReader(jsonString))
    }

    private fun findAndProcessArrays(element: com.google.gson.JsonElement, list: MutableList<TV>, depth: Int = 0) {
        if (depth > 5) return // Prevent too deep recursion

        when {
            element.isJsonArray -> {
                val array = element.asJsonArray
                // Check if this array looks like a channel list (at least one object with a URL)
                val looksLikeChannels = array.any { 
                    it.isJsonObject && (
                        hasAnyKey(it.asJsonObject, "url", "link", "stream", "uri", "source", "file", "m3u8_url", "mpd_url", "play_url")
                    )
                }

                if (looksLikeChannels) {
                    processArray(array, list)
                } else {
                    // Dive deeper into array elements
                    array.forEach { findAndProcessArrays(it, list, depth + 1) }
                }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                // Special case: if object ITSELF looks like a single channel
                if (hasAnyKey(obj, "url", "link", "stream", "uri", "source", "file", "m3u8_url")) {
                    val tv = parseSingleObject(obj, list.size)
                    if (tv != null) list.add(tv)
                } else {
                    // Search all keys
                    for (key in obj.keySet()) {
                        findAndProcessArrays(obj.get(key), list, depth + 1)
                    }
                }
            }
        }
    }
    
    private fun hasAnyKey(obj: JsonObject, vararg keys: String): Boolean {
        return keys.any { findString(obj, it) != null }
    }

    private fun processArray(array: JsonArray, list: MutableList<TV>) {
        for (item in array) {
            if (item.isJsonObject) {
                val obj = item.asJsonObject
                val tv = parseSingleObject(obj, list.size)
                if (tv != null) {
                    list.add(tv)
                }
            }
        }
    }

    fun parseSingleObject(obj: JsonObject, index: Int): TV? {
        // Heuristic Field Mapping
        
        // 1. Find URL (Strict - must exist)
        // Prioritize m3u8_url and play_url for streaming stability
        val url = findString(obj, "m3u8_url", "play_url", "url", "Url", "stream_url", "uri", "file", "src", "link", "stream", "mpd_url", "video_url", "address", "location", "media_url", "hls_url", "dash_url", "rtsp_url", "source", "content_url")
        if (url.isNullOrBlank()) return null

        // 2. Find Name & Extract embedded attributes
        var rawName = findString(obj, "name", "title", "channel_name", "display_name", "caption", "station", "tv_name", "channel", "label", "tvg-name", "tvg_name", "ch_name") ?: "ExclusiveTV $index"
        
        var embeddedGroup: String? = null
        var embeddedLogo: String? = null
        val cleanedName: String

        // Handle M3U-style attributes embedded in JSON name field (found in some dynamic sources)
        if (rawName.contains("group-title=\"")) {
            embeddedGroup = rawName.substringAfter("group-title=\"").substringBefore("\"")
            if (rawName.contains("tvg-logo=\"")) {
                embeddedLogo = rawName.substringAfter("tvg-logo=\"").substringBefore("\"")
            }
            cleanedName = rawName.substringAfterLast("\",").substringAfterLast(",").trim()
        } else if (rawName.contains(",|")) {
             // Handle "|KU| Channel Name" or similar delimiters
             cleanedName = rawName.substringAfterLast("|").trim()
        } else {
            cleanedName = rawName.trim()
        }

        // 3. Find Logo (Prefer embedded if found in name cleanup)
        val logo = embeddedLogo ?: findString(obj, "logo", "Logo", "LOGO", "icon", "image", "thumb", "thumbnail", "stream_icon", "channel_logo", "logo_url", "poster", "tvg-logo", "banner", "tvg_logo", "img", "cover", "picture") ?: ""

        // 4. Find Group (Prefer embedded if found in name cleanup)
        val group = embeddedGroup ?: findString(obj, "group", "category", "genre", "category_name", "group_title", "group-title", "cat_name", "category_id", "folder", "playlist", "section", "group_name") ?: "Uncategorized"

        // 5. Find DRM
        val drmLicense = findString(obj, "license_key", "drm_url", "license", "clearkey", "key", "license_url", "license_src", "drm_license", "kodi_prop_license_key")
        var drmScheme = findString(obj, "drm_scheme", "drm_type", "license_type", "license_mode", "scheme", "drm_system")
        
        // Try to parse nested DRM object commonly used
        if (drmScheme == null && obj.has("drm") && obj.get("drm").isJsonObject) {
            val drmObj = obj.getAsJsonObject("drm")
            drmScheme = findString(drmObj, "type", "scheme", "system")
        }
        
        var finalDrmLicense = drmLicense
        if (finalDrmLicense == null && obj.has("drm") && obj.get("drm").isJsonObject) {
             val drmObj = obj.getAsJsonObject("drm")
             finalDrmLicense = findString(drmObj, "key", "license", "url", "license_url")
        }

        if (drmScheme == null && finalDrmLicense != null) {
            // Auto detect from license string or license URL
            when {
                finalDrmLicense.contains("clearkey", ignoreCase = true) -> drmScheme = "clearkey"
                finalDrmLicense.contains("widevine", ignoreCase = true) || finalDrmLicense.contains("wv", ignoreCase = true) -> drmScheme = "widevine"
                finalDrmLicense.contains("playready", ignoreCase = true) || finalDrmLicense.contains("pr", ignoreCase = true) -> drmScheme = "playready"
                finalDrmLicense.contains("keyid=", ignoreCase = true) && finalDrmLicense.contains("key=", ignoreCase = true) -> drmScheme = "clearkey"
                finalDrmLicense.matches(Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")) -> drmScheme = "clearkey"
            }
        }
        
        if (finalDrmLicense != null && drmScheme == null && url.contains(".mpd", ignoreCase = true)) {
             drmScheme = "widevine"
        }

        // 6. Find ID (Optional, handle UUIDs or IDs)
        val idStr = findString(obj, "id", "channel_id", "tvg-id", "tvg_id", "ch_id", "unique_id", "stream_id", "uuid")
        val finalId = idStr?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: index
        val apiId = idStr ?: finalId.toString()
        
        // 7. Find Catchup Info
        val catchupType = findString(obj, "catchup", "catchup-type", "catchup_type", "catchup_mode", "timeshift_type")
        val catchupDays = findString(obj, "catchup-days", "catchup_days", "dvr_days", "timeshift_days")
        val catchupSource = findString(obj, "catchup-source", "catchup_source", "timeshift_source", "catchup_url")

        // Determine Type
        val typeStr = findString(obj, "type", "stream_type", "content_type", "protocol")
        var type = when {
            typeStr.equals("hls", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true) -> Type.HLS
            typeStr.equals("dash", ignoreCase = true) || url.contains(".mpd", ignoreCase = true) -> Type.STREAM
            typeStr.equals("web", ignoreCase = true) || typeStr.equals("embed", ignoreCase = true) -> Type.WEB
            typeStr.equals("rtsp", ignoreCase = true) || url.startsWith("rtsp://", ignoreCase = true) -> Type.STREAM
            typeStr.equals("rtmp", ignoreCase = true) || url.startsWith("rtmp://", ignoreCase = true) -> Type.STREAM
            else -> Type.STREAM
        }
        
        if (type != Type.WEB && (url.contains("youtube.com") || url.contains("youtu.be") || url.contains("facebook.com") || url.contains("twitch.tv"))) {
            type = Type.WEB
        }

        return TV(
            id = finalId,
            apiId = apiId,
            name = cleanedName,
            title = cleanedName,
            description = findString(obj, "description", "desc", "plot", "info", "summary"),
            logo = logo,
            image = findString(obj, "image", "backdrop", "fanart", "background"),
            uris = arrayListOf(url),
            headers = parseHeaders(obj),
            group = group,
            type = type,
            drmScheme = drmScheme,
            drmLicenseUrl = finalDrmLicense,
            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            child = listOf()
        )
    }

    private fun parseHeaders(obj: JsonObject): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        
        // 1. Try "headers" object (or "http_headers", "request_headers")
        val headersKey = obj.keySet().firstOrNull { 
            it.equals("headers", ignoreCase = true) || it.equals("http_headers", ignoreCase = true) || 
            it.equals("request_headers", ignoreCase = true) || it.equals("stream_headers", ignoreCase = true)
        }
        
        if (headersKey != null && obj.get(headersKey).isJsonObject) {
            val headersObj = obj.getAsJsonObject(headersKey)
            for (key in headersObj.keySet()) {
                val value = headersObj.get(key)
                if (!value.isJsonNull) {
                    val normalizedKey = normalizeHeaderKey(key)
                    headers[normalizedKey] = value.asString
                }
            }
        }

        // 2. Try Top-Level Common Headers (snake_case or flat)
        findString(obj, "user-agent", "user_agent", "ua", "http_user_agent")?.let { headers["User-Agent"] = it }
        findString(obj, "referer", "referrer", "http-referer", "http_referrer")?.let { headers["Referer"] = it }
        findString(obj, "cookie", "cookies", "http_cookie")?.let { headers["Cookie"] = it }
        findString(obj, "origin", "http-origin", "http_origin")?.let { headers["Origin"] = it }
        findString(obj, "authorization", "auth", "token")?.let { headers["Authorization"] = it }

        return if (headers.isNotEmpty()) headers else null
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
            else -> key // Return original casing if unknown, or maybe capitalize first letter?
        }
    }

    private fun findString(obj: JsonObject, vararg keys: String): String? {
        // Fast path: direct match
        for (key in keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                val element = obj.get(key)
                return if (element.isJsonPrimitive) element.asString else element.toString()
            }
        }
        
        // Slow path: fuzzy match
        for (objKey in obj.keySet()) {
             val normalizedObjKey = objKey.replace("_", "").replace("-", "").lowercase()
             
             for (key in keys) {
                 val normalizedKey = key.replace("_", "").replace("-", "").lowercase()
                 if (normalizedKey == normalizedObjKey) {
                     val element = obj.get(objKey)
                     if (!element.isJsonNull) {
                         return if (element.isJsonPrimitive) element.asString else element.toString()
                     }
                 }
             }
        }
        return null
    }
}
