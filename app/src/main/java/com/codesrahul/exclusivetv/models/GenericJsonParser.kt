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
            
            if (element.isJsonArray) {
                val array = element.asJsonArray
                processArray(array, list)
            } else if (element.isJsonObject) {
                // Handle cases where the list is wrapped in a "channels" or "streams" key
                val obj = element.asJsonObject
                for (key in obj.keySet()) {
                    val field = obj.get(key)
                    if (field.isJsonArray) {
                        processArray(field.asJsonArray, list)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing generic JSON", e)
        }
        return list
    }

    private fun processArray(array: JsonArray, list: MutableList<TV>) {
        var count = 0
        for (item in array) {
            if (item.isJsonObject) {
                val obj = item.asJsonObject
                val tv = parseObject(obj, count++)
                if (tv != null) {
                    list.add(tv)
                }
            }
        }
    }

    private fun parseObject(obj: JsonObject, index: Int): TV? {
        // Heuristic Field Mapping
        
        // 1. Find Name
        val name = findString(obj, "name", "title", "channel_name", "display_name", "caption", "station", "tv_name", "channel", "label") ?: "Channel $index"
        
        // 2. Find URL
        // Added m3u8, mpd, ts, video, address, location variations
        val url = findString(obj, "url", "stream_url", "uri", "file", "src", "link", "stream", "play_url", "m3u8_url", "mpd_url", "video_url", "address", "location", "media_url", "hls_url", "dash_url")
        if (url.isNullOrBlank()) return null // URL is mandatory

        // 3. Find Logo
        val logo = findString(obj, "logo", "icon", "image", "thumb", "thumbnail", "stream_icon", "channel_logo", "logo_url", "poster", "tvg-logo", "banner") ?: ""

        // 4. Find Group
        // Added group-title, category_id, genre definitions
        val group = findString(obj, "group", "category", "genre", "category_name", "group_title", "group-title", "cat_name", "category_id") ?: "Uncategorized"

        // 5. Find DRM
        val drmLicense = findString(obj, "license_key", "drm_url", "license", "clearkey", "key", "license_url")
        var drmScheme = if (drmLicense != null) {
            // Auto detect
            when {
                drmLicense.contains("clearkey", ignoreCase = true) -> "clearkey"
                drmLicense.contains("widevine", ignoreCase = true) -> "widevine"
                drmLicense.contains("playready", ignoreCase = true) -> "playready"
                // Check for keyid/key params in URL (common for Direct ClearKey)
                drmLicense.contains("keyid=", ignoreCase = true) && drmLicense.contains("key=", ignoreCase = true) -> "clearkey"
                else -> null
            }
        } else null
        
        // If scheme is still null but we have a license URL, default to something reasonable based on stream?
        // Or leave null and let ExoPlayer/App try to detect.
        // Usually if license_url is present, it's Widevine or ClearKey.
        if (drmLicense != null && drmScheme == null) {
            // Fallback: If DASH (.mpd), assume Widevine if not explicit
            if (url.contains(".mpd", ignoreCase = true)) {
                 drmScheme = "widevine"
            }
        }

        // 6. Find ID (Optional, default to index)
        val idStr = findString(obj, "id", "channel_id", "tvg-id", "tvg_id", "ch_id")
        val finalId = idStr?.toIntOrNull() ?: index
        
        // 7. Find Catchup Info
        val catchupType = findString(obj, "catchup", "catchup-type", "catchup_type", "catchup_mode")
        val catchupDays = findString(obj, "catchup-days", "catchup_days", "dvr_days")
        val catchupSource = findString(obj, "catchup-source", "catchup_source", "timeshift")

        // Determine Type
        // Determine Type (Default to STREAM for universal support)
        val type = Type.STREAM

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
                    headers[key] = value.asString
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
