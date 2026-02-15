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
                while (jsonReader.hasNext()) {
                    val name = jsonReader.nextName()
                    val token = jsonReader.peek()
                    if (token == JsonToken.BEGIN_ARRAY) {
                        // Heuristic: If key looks like a list, parse it
                        if (name.contains("channel") || name.contains("list") || name.contains("streams")) {
                             parseArray(jsonReader, list)
                        } else {
                             jsonReader.skipValue()
                        }
                    } else {
                        jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { jsonReader.close() } catch (e: Exception) {}
        }
        return list
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
        
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val token = reader.peek()
            
            if (token == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            
            // STRICT MATCHING (Fastest) -> Fallback to loose matching
            when (key) {
                "url", "stream_url", "play_url", "m3u8_url", "uri", "link", "file" -> url = reader.nextString()
                "name", "title", "channel_name", "station" -> name = reader.nextString()
                "group", "category", "group_title", "genre" -> group = reader.nextString()
                "logo", "icon", "image", "thumbnail", "tvg-logo", "logo_url" -> logo = reader.nextString()
                "id", "channel_id", "tvg-id" -> idStr = reader.nextString()
                "headers", "http_headers" -> {
                    // Special handling for headers object
                    if (token == JsonToken.BEGIN_OBJECT) {
                         if (headers == null) headers = mutableMapOf()
                         reader.beginObject()
                         while (reader.hasNext()) {
                             val hKey = reader.nextName()
                             val hVal = reader.nextString()
                             headers[normalizeHeaderKey(hKey)] = hVal
                         }
                         reader.endObject()
                    } else {
                        reader.skipValue()
                    }
                }
                "license_key", "drm_license", "drm_url", "license_url", "key" -> drmLicense = reader.nextString()
                "drm_scheme", "drm_type" -> drmScheme = reader.nextString()
                "user_agent", "user-agent", "ua" -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["User-Agent"] = reader.nextString()
                }
                "referer", "referrer" -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["Referer"] = reader.nextString()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (url.isNullOrEmpty()) return null

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
        
        // Simple Type Detection
        val type = if (url.contains(".m3u8")) com.codesrahul.exclusivetv.models.Type.HLS else com.codesrahul.exclusivetv.models.Type.STREAM

        return TV(
            id = finalId,
            apiId = apiId,
            name = finalName,
            title = finalName,
            description = "",
            logo = logo ?: "",
            image = "",
            uris = arrayListOf(url),
            headers = headers,
            group = finalGroup,
            type = type,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = null,
            catchupDays = null,
            catchupSource = null,
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
}
