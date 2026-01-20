package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    private const val TAG = "M3UParser"
    // Compile Regex once for performance
    private val PROP_REGEX = Regex("([a-zA-Z0-9\\-_]+)=(?:\"([^\"]*)\"|([^, ]+))")

    fun parse(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var line: String?

        var currentName: String = ""
        var currentLogo: String = ""
        var currentGroup: String = ""
        var currentTvgId: String = ""
        var currentHeaders = mutableMapOf<String, String>()
        var currentDrmScheme: String? = null
        var currentDrmLicense: String? = null

        var currentCatchupType: String? = null
        var currentCatchupDays: String? = null
        var currentCatchupSource: String? = null
        var channelCount = 0

        val currentUris = mutableListOf<String>()
        val globalHeaders = mutableMapOf<String, String>()

        // Helper to save current channel
        fun saveAndReset() {
            if (currentUris.isNotEmpty()) {
                // Merge global headers with current headers (current takes precedence)
                val finalHeaders = if (globalHeaders.isNotEmpty()) {
                    val merged = globalHeaders.toMutableMap()
                    merged.putAll(currentHeaders)
                    merged
                } else currentHeaders

                channels.add(
                    createTV(
                        channelCount++,
                        currentTvgId,
                        currentName,
                        currentLogo,
                        currentGroup,
                        currentUris,
                        finalHeaders,
                        currentDrmScheme,
                        currentDrmLicense,
                        currentCatchupType,
                        currentCatchupDays,
                        currentCatchupSource
                    )
                )
                // Reset State
                currentName = ""
                currentLogo = ""
                currentGroup = ""
                currentTvgId = ""
                currentHeaders = mutableMapOf()
                currentDrmScheme = null
                currentDrmLicense = null
                currentCatchupType = null
                currentCatchupDays = null
                currentCatchupSource = null
                currentUris.clear()
            }
        }

        var isM3U = false

        while (reader.readLine().also { line = it } != null) {
            try {
                val trimmedLine = line?.trim() ?: continue
                if (trimmedLine.isEmpty()) continue
    
                if (trimmedLine.startsWith("#EXTM3U")) {
                    isM3U = true
                    // Extract global properties from #EXTM3U tag
                    val matches = PROP_REGEX.findAll(trimmedLine)
                    for (match in matches) {
                        val key = match.groupValues[1].lowercase()
                        val value = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else match.groupValues[3]
                        
                        when (key) {
                            "user-agent", "user_agent", "http-user-agent" -> globalHeaders["User-Agent"] = value
                            "referer", "referrer", "http-referer" -> globalHeaders["Referer"] = value
                            "cookie", "cookies" -> globalHeaders["Cookie"] = value
                            "origin", "http-origin" -> globalHeaders["Origin"] = value
                        }
                    }
                    continue
                }
    
                if (trimmedLine.startsWith("#EXTINF:")) {
                    // Should save previous channel if exists
                    saveAndReset()
    
                    // Extract Name (everything after the last comma)
                    val lastCommaIndex = trimmedLine.lastIndexOf(',')
                    if (lastCommaIndex != -1) {
                        currentName = trimmedLine.substring(lastCommaIndex + 1).trim()
                    } else {
                        currentName = "Channel ${channelCount + 1}"
                    }
    
                    // Extract Properties
                    val propertiesPart = if (lastCommaIndex != -1) trimmedLine.substring(0, lastCommaIndex) else trimmedLine
                    
                    val matches = PROP_REGEX.findAll(propertiesPart)
                    for (match in matches) {
                        val key = match.groupValues[1].lowercase() 
                        // Value is in group 2 (quoted content) or group 3 (unquoted content)
                        val value = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else match.groupValues[3]
                        
                        when (key) {
                            "tvg-id", "tvg_id", "channel-id", "channel_id" -> currentTvgId = value
                            "tvg-logo", "tvg_logo", "logo", "icon" -> currentLogo = value
                            "group-title", "group_title", "group" -> currentGroup = value
                            "tvg-name", "tvg_name", "channel-name" -> if (currentName.isEmpty() || currentName.startsWith("Channel")) currentName = value 
                            
                            // Catchup
                            "catchup", "catchup-type", "catchup_type" -> currentCatchupType = value
                            "catchup-days", "catchup_days", "timeshift" -> currentCatchupDays = value
                            "catchup-source", "catchup_source" -> currentCatchupSource = value
                            
                            // Headers (Extended)
                            "user-agent", "user_agent", "http-user-agent" -> currentHeaders["User-Agent"] = value
                            "referer", "referrer", "http-referer" -> currentHeaders["Referer"] = value
                            "cookie", "cookies", "http-cookie" -> currentHeaders["Cookie"] = value
                            "origin", "http-origin" -> currentHeaders["Origin"] = value
                            
                            // DRM
                            "license-key", "license_key", "license-url", "license_url" -> currentDrmLicense = value
                            "license-type", "license_type", "drm-scheme" -> currentDrmScheme = value
                        }
                    }
    
                } else if (trimmedLine.startsWith("#EXTHTTP:") || trimmedLine.startsWith("# EXTHTTP:")) {
                      // Flush previous if URIs exist (meaning this tag belongs to a NEW block)
                      saveAndReset()
                      
                      // Parse JSON headers
                      val jsonStr = trimmedLine.substringAfter("HTTP:").trim()
                      try {
                          val jsonObject = org.json.JSONObject(jsonStr)
                          val keys = jsonObject.keys()
                          while (keys.hasNext()) {
                              val key = keys.next()
                              val value = jsonObject.getString(key)
                              currentHeaders[key] = value
                          }
                      } catch (e: Exception) {
                          Log.e(TAG, "Failed to parse EXTHTTP JSON: $jsonStr", e)
                      }
    
                } else if (trimmedLine.startsWith("#KODIPROP:")) {
                    // If we have URIs, this KODIPROP belongs to the NEXT channel, so save current
                    saveAndReset()
    
                    // #KODIPROP:inputstream.adaptive.license_type=...
                    val parts = trimmedLine.substringAfter("#KODIPROP:").split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        
                        when (key) {
                            "inputstream.adaptive.license_type" -> {
                                currentDrmScheme = when(value) {
                                    "com.widevine.alpha" -> "widevine"
                                    "com.microsoft.playready" -> "playready"
                                    "org.w3.clearkey" -> "clearkey" 
                                    else -> value
                                }
                            }
                            "inputstream.adaptive.license_key" -> {
                                currentDrmLicense = value.split("|")[0] 
                            }
                        }
                    }
                } else if (trimmedLine.startsWith("#EXTVLCOPT:")) {
                    // Same logic: If URIs present, this belongs to NEXT channel
                    saveAndReset()
    
                    val parts = trimmedLine.substringAfter("#EXTVLCOPT:").split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        when(key.lowercase()) {
                            "http-user-agent" -> currentHeaders["User-Agent"] = value
                            "http-referrer", "http-referer" -> currentHeaders["Referer"] = value
                            "http-origin" -> currentHeaders["Origin"] = value
                            "http-cookie" -> currentHeaders["Cookie"] = value
                        }
                    }
                } else if (!trimmedLine.startsWith("#")) {
                    // Verify it's a URL
                    if (trimmedLine.contains("://")) {
                         var finalUrl = trimmedLine
                         
                         // Handle pipe headers
                         if (trimmedLine.contains("|")) {
                             val urlParts = trimmedLine.split("|", limit = 2)
                             finalUrl = urlParts[0]
                             val headersPart = urlParts[1]
                             
                             val headerPairs = headersPart.split("&")
                             for (pair in headerPairs) {
                                 val kv = pair.split("=", limit = 2)
                                 if (kv.size == 2) {
                                     when(kv[0]) {
                                         "User-Agent" -> currentHeaders["User-Agent"] = kv[1]
                                         "Referer" -> currentHeaders["Referer"] = kv[1]
                                         "Cookie" -> currentHeaders["Cookie"] = kv[1]
                                         else -> currentHeaders[kv[0]] = kv[1]
                                     }
                                 }
                             }
                         }
                         currentUris.add(finalUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing line: $line", e)
            }
        }

        // Add last channel
        saveAndReset()

        if (channels.isEmpty() && !isM3U) {
             // If nothing parsed and no EXTM3U found, maybe it was a simple list of URLs?
             // SimpleListParser might have been better, but let's try one last fallback
             Log.d(TAG, "No channels parsed, checking for plain URLs")
             val lines = content.lines()
             for ((index, l) in lines.withIndex()) {
                 val tl = l.trim()
                 if (tl.startsWith("http")) {
                     channels.add(createTV(index, "", "", "", "", listOf(tl), mapOf(), null, null, null, null, null))
                 }
             }
        }

        Log.i(TAG, "Parsed ${channels.size} channels from M3U")
        return channels
    }

    private fun createTV(
        id: Int,
        apiId: String,
        name: String,
        logo: String,
        group: String,
        uris: List<String>,
        headers: Map<String, String>,
        drmScheme: String?,
        drmLicense: String?,
        catchupType: String?,
        catchupDays: String?,
        catchupSource: String?
    ): TV {
        // M3U entries are almost exclusively streams or nested playlists. Default to STREAM.
        val type = Type.STREAM
        
        var finalName = name
        if (finalName.isEmpty() || finalName.startsWith("Channel")) {
            // Try to extract name from URL if possible
            val firstUri = uris.firstOrNull() ?: ""
            if (firstUri.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse(firstUri)
                    val pathSegments = uri.pathSegments
                    if (!pathSegments.isNullOrEmpty()) {
                         var pIdx = pathSegments.lastIndex
                         var candidate = pathSegments[pIdx]

                         // 1. Skip generic filenames
                         if (candidate.equals("index.mpd", ignoreCase = true) || 
                             candidate.equals("master.m3u8", ignoreCase = true) ||
                             candidate.equals("manifest.mpd", ignoreCase = true) ||
                             candidate.startsWith("index", ignoreCase = true) ||
                             candidate.startsWith("playlist", ignoreCase = true)) {
                             pIdx--
                         }

                         if (pIdx >= 0) {
                             candidate = pathSegments[pIdx]
                             // 2. Skip UUID/Hash segments (common in Star/Hotstar: 32 chars hex)
                             if (candidate.length > 20 && candidate.matches(Regex("[a-fA-F0-9]+"))) {
                                  pIdx--
                             }
                         }

                         if (pIdx >= 0) {
                             // Clean up the name
                             var params = pathSegments[pIdx]
                             // Remove trailing numeric IDs often attached (e.g., -1540057075)
                             params = params.replace(Regex("[-_]\\d{8,}$"), "")
                             finalName = params.replace('_', ' ').replace('-', ' ').trim()
                         }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract name from URI", e)
                }
            }
        }
        
        if (finalName.isEmpty()) {
            finalName = "Channel ${id + 1}"
        }

        return TV(
            id = id,
            apiId = apiId,
            name = finalName,
            title = finalName,
            description = null,
            logo = logo,
            image = null,
            uris = ArrayList(uris), // Copy list
            headers = if (headers.isNotEmpty()) HashMap(headers) else null,
            group = if (group.isNotEmpty()) group else "Uncategorized",
            type = type,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,

            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            child = listOf()
        )
    }
}
