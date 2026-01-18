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
        val name = findString(obj, "name", "title", "channel_name", "caption", "station") ?: "Channel $index"
        
        // 2. Find URL
        val url = findString(obj, "url", "stream_url", "uri", "file", "src", "link", "stream", "play_url")
        if (url.isNullOrBlank()) return null // URL is mandatory

        // 3. Find Logo
        val logo = findString(obj, "logo", "icon", "image", "thumb", "stream_icon", "channel_logo") ?: ""

        // 4. Find Group
        val group = findString(obj, "group", "category", "genre", "category_name") ?: "Uncategorized"

        // 5. Find DRM
        val drmLicense = findString(obj, "license_key", "drm_url", "license", "clearkey", "key")
        val drmScheme = if (drmLicense != null) {
            // Auto detect
            when {
                drmLicense.contains("clearkey") -> "clearkey"
                drmLicense.contains("widevine") -> "widevine"
                drmLicense.contains("playready") -> "playready"
                else -> null
            }
        } else null

        // Determine Type
        val type = if (url.contains(".m3u8") || url.contains(".mpd") || url.contains(".ts") || url.contains(".php")) 
            Type.STREAM else Type.WEB

        return TV(
            id = index,
            apiId = index.toString(),
            name = name,
            title = name,
            description = null,
            logo = logo,
            image = null,
            uris = arrayListOf(url),
            headers = null, // TODO: Try to find headers object?
            group = group,
            type = type,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = null,
            catchupDays = null,
            catchupSource = null,
            child = listOf()
        )
    }

    private fun findString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                return obj.get(key).asString
            }
            // Case insensitive check
            for (objKey in obj.keySet()) {
                if (objKey.equals(key, ignoreCase = true) && !obj.get(objKey).isJsonNull) {
                    return obj.get(objKey).asString
                }
            }
        }
        return null
    }
}
