package com.codesrahul.exclusivetv.models

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GenericJsonParser {
    private const val TAG = "GenericJsonParser"

    fun parse(jsonString: String): List<TV> {
        val list = mutableListOf<TV>()
        try {
            val element = JsonParser.parseString(jsonString)
            findAndProcessArrays(element, list)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing generic JSON", e)
        }
        return list
    }

    private fun findAndProcessArrays(element: com.google.gson.JsonElement, list: MutableList<TV>, depth: Int = 0) {
        if (depth > 5) return // Prevent too deep recursion

        when {
            element.isJsonArray -> {
                val array = element.asJsonArray
                // Check if this array looks like a channel list (at least one object with a URL)
                val looksLikeChannels = array.any { 
                    it.isJsonObject && (
                        it.asJsonObject.has("url") || it.asJsonObject.has("link") || 
                        it.asJsonObject.has("stream") || it.asJsonObject.has("uri") ||
                        it.asJsonObject.has("m3u8_url") || it.asJsonObject.has("mpd_url")
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
                if (obj.has("url") || obj.has("link") || obj.has("m3u8_url")) {
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
        
        // 1. Find Name
        val name = findString(obj, "name", "title", "channel_name", "display_name", "caption", "station", "tv_name", "channel", "label", "tvg-name", "tvg_name") ?: "Channel $index"
        
        // 2. Find URL
        val url = findString(obj, "url", "stream_url", "uri", "file", "src", "link", "stream", "play_url", "m3u8_url", "mpd_url", "video_url", "address", "location", "media_url", "hls_url", "dash_url", "rtsp_url", "source")
        if (url.isNullOrBlank()) return null // URL is mandatory

        // 3. Find Logo
        val logo = findString(obj, "logo", "icon", "image", "thumb", "thumbnail", "stream_icon", "channel_logo", "logo_url", "poster", "tvg-logo", "banner", "tvg_logo", "img") ?: ""

        // 4. Find Group
        val group = findString(obj, "group", "category", "genre", "category_name", "group_title", "group-title", "cat_name", "category_id", "folder", "playlist", "section") ?: "Uncategorized"

        // 5. Find DRM
        val drmLicense = findString(obj, "license_key", "drm_url", "license", "clearkey", "key", "license_url", "license_src", "drm_license", "kodi_prop_license_key")
        var drmScheme = findString(obj, "drm_scheme", "drm_type", "license_type", "license_mode", "scheme")
        
        if (drmScheme == null && drmLicense != null) {
            // Auto detect from license string or license URL
            when {
                drmLicense.contains("clearkey", ignoreCase = true) -> drmScheme = "clearkey"
                drmLicense.contains("widevine", ignoreCase = true) || drmLicense.contains("wv", ignoreCase = true) -> drmScheme = "widevine"
                drmLicense.contains("playready", ignoreCase = true) || drmLicense.contains("pr", ignoreCase = true) -> drmScheme = "playready"
                drmLicense.contains("keyid=", ignoreCase = true) && drmLicense.contains("key=", ignoreCase = true) -> drmScheme = "clearkey"
                // Heuristic: If it looks like a hex key pair (32 chars : 32 chars)
                drmLicense.matches(Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")) -> drmScheme = "clearkey"
            }
        }
        
        // Fallback: If DASH (.mpd), assume Widevine if not explicit and has license
        if (drmLicense != null && drmScheme == null && url.contains(".mpd", ignoreCase = true)) {
             drmScheme = "widevine"
        }

        // 6. Find ID (Optional, default to index)
        val idStr = findString(obj, "id", "channel_id", "tvg-id", "tvg_id", "ch_id", "unique_id")
        val finalId = idStr?.toIntOrNull() ?: index
        
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
        
        // Final sanity check for WEB type
        if (type != Type.WEB && (url.contains("youtube.com") || url.contains("youtu.be") || url.contains("facebook.com") || url.contains("twitch.tv"))) {
            type = Type.WEB
        }

        return TV(
            id = finalId,
            apiId = finalId.toString(),
            name = name,
            title = name,
            description = null,
            logo = logo,
            image = null,
            uris = arrayListOf(url),
            headers = parseHeaders(obj),
            group = group,
            type = type,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            child = listOf()
        )
    }

    private fun parseHeaders(obj: JsonObject): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        
        // 1. Try "headers" object
        if (obj.has("headers") && obj.get("headers").isJsonObject) {
            val headersObj = obj.getAsJsonObject("headers")
            for (key in headersObj.keySet()) {
                val value = headersObj.get(key)
                if (!value.isJsonNull) {
                    // Normalize Key: cookie -> Cookie, user-agent -> User-Agent
                    val normalizedKey = when (key.lowercase()) {
                        "cookie" -> "Cookie"
                        "user-agent" -> "User-Agent"
                        "referer", "referrer" -> "Referer"
                        "origin" -> "Origin"
                        "authorization" -> "Authorization"
                        else -> key // Keep as is if not common
                    }
                    headers[normalizedKey] = value.asString
                }
            }
        }

        // 2. Try Top-Level Common Headers
        findString(obj, "user-agent", "user_agent", "ua")?.let { headers["User-Agent"] = it }
        findString(obj, "referer", "referrer", "http-referer")?.let { headers["Referer"] = it }
        findString(obj, "cookie", "cookies")?.let { headers["Cookie"] = it }
        findString(obj, "origin", "http-origin")?.let { headers["Origin"] = it }

        return if (headers.isNotEmpty()) headers else null
    }

    private fun findString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            // Direct Check
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                return obj.get(key).asString
            }
            
            // Case-Insensitive Check (Iterate keys once per call? optimization possible but list is small)
            // But strict keys loop is better for priority.
            // Let's iterate object keys only if not found directly
             for (objKey in obj.keySet()) {
                // Determine equality
                // We want to match "key" against "objKey"
                // e.g. looking for "channel_name", obj has "Channel_Name"
                if (objKey.equals(key, ignoreCase = true) && !obj.get(objKey).isJsonNull) {
                    val element = obj.get(objKey)
                    return if (element.isJsonPrimitive) element.asString else element.toString()
                }
                
                // Also handle "snake_case" vs "camelCase" mismatch?
                // e.g. looking for "user_agent", obj has "userAgent"
                val normalizedKey = key.replace("_", "").replace("-", "").lowercase()
                val normalizedObjKey = objKey.replace("_", "").replace("-", "").lowercase()
                if (normalizedKey == normalizedObjKey && !obj.get(objKey).isJsonNull) {
                     val element = obj.get(objKey)
                     return if (element.isJsonPrimitive) element.asString else element.toString()
                }
            }
        }
        return null
    }
}
