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
            val key = reader.nextName()
            val token = reader.peek()
            
            if (token == JsonToken.NULL) {
                reader.nextNull()
                continue
            }
            
            // STRICT MATCHING (Fastest) -> Fallback to loose matching
            when (key) {
                "url", "stream_url", "play_url", "m3u8_url", "uri", "link", "file" -> {
                    val singleUrl = reader.nextString()
                    if (singleUrl.isNotEmpty()) uris.add(singleUrl)
                }
                "uris", "urls", "streams" -> {
                    if (token == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val nextUrl = reader.nextString()
                            if (nextUrl.isNotEmpty()) uris.add(nextUrl)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "name", "title", "channel_name", "station" -> name = reader.nextString()
                "group", "category", "group_title", "genre" -> group = reader.nextString()
                "logo", "icon", "image", "thumbnail", "tvg-logo", "logo_url" -> logo = reader.nextString()
                "id", "channel_id", "tvg-id" -> idStr = reader.nextString()
                "internal_id" -> {
                     // Prioritize internal ID for restoration consistency
                     try {
                         idStr = reader.nextInt().toString()
                     } catch (e: Exception) {
                         idStr = reader.nextString() // Fallback if stored as string
                     }
                }
                "type" -> {
                    val typeStr = reader.nextString()
                    // Map string to enum manually to avoid crashes
                    type = when (typeStr.uppercase()) {
                        "WEB" -> com.codesrahul.exclusivetv.models.Type.WEB
                        "HLS" -> com.codesrahul.exclusivetv.models.Type.HLS
                        "STREAM" -> com.codesrahul.exclusivetv.models.Type.STREAM
                        else -> null 
                    }
                }
                "catchup_type", "catchup-type" -> catchupType = reader.nextString()
                "catchup_days", "catchup-days" -> catchupDays = reader.nextString()
                "catchup_source", "catchup-source" -> catchupSource = reader.nextString()
                
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
                "license_key", "drm_license", "drm_url", "license_url", "key", "drm_license_url" -> drmLicense = reader.nextString()
                "drm_scheme", "drm_type" -> drmScheme = reader.nextString()
                "user_agent", "user-agent", "ua" -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["User-Agent"] = reader.nextString()
                }
                "referer", "referrer" -> {
                    if (headers == null) headers = mutableMapOf()
                    headers["Referer"] = reader.nextString()
                }
                else -> {
                    // HEURISTIC FALLBACK: If we haven't found strict high-confidence keys yet,
                    // check if this unknown field looks like a URL or Name.
                    if (token == JsonToken.STRING) {
                        val value = reader.nextString()
                        if (url == null && (value.startsWith("http") || value.startsWith("rtmp"))) {
                            url = value // Found a potential stream link
                        } else if (name == null && value.length < 60 && !value.startsWith("http") && !value.startsWith("{")) {
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
        val finalType = type ?: if (primaryUrl.contains(".m3u8")) com.codesrahul.exclusivetv.models.Type.HLS else com.codesrahul.exclusivetv.models.Type.STREAM

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
}
