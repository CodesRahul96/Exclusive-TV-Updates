package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    private const val TAG = "M3UParser"

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

        // Helper to save current channel
        fun saveAndReset() {
            if (currentUris.isNotEmpty()) {
                channels.add(
                    createTV(
                        channelCount++,
                        currentTvgId,
                        currentName,
                        currentLogo,
                        currentGroup,
                        currentUris,
                        currentHeaders,
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
                    continue
                }
    
                if (trimmedLine.startsWith("#EXTINF:")) {
                    // Should save previous channel if exists
                    saveAndReset()
    
                    // #EXTINF:-1 tvg-id="cnn" tvg-name="CNN USA" tvg-logo="http://..." group-title="News",CNN
                    
                    // Extract Name (everything after the last comma)
                    val lastCommaIndex = trimmedLine.lastIndexOf(',')
                    if (lastCommaIndex != -1) {
                        currentName = trimmedLine.substring(lastCommaIndex + 1).trim()
                    } else {
                        currentName = "Channel ${channelCount + 1}"
                    }
    
                    // Extract Properties
                    val propertiesPart = if (lastCommaIndex != -1) trimmedLine.substring(0, lastCommaIndex) else trimmedLine
                    
                    // Regex for key="value"
                    val propRegex = Regex("([a-zA-Z0-9\\-]+)=\"([^\"]*)\"")
                    val matches = propRegex.findAll(propertiesPart)
                    for (match in matches) {
                        val key = match.groupValues[1]
                        val value = match.groupValues[2]
                        
                        when (key) {
                            "tvg-id" -> currentTvgId = value
                            "tvg-logo" -> currentLogo = value
                            "group-title" -> currentGroup = value
                            "tvg-name" -> if (currentName.isEmpty() || currentName.startsWith("Channel")) currentName = value 
                            "catchup-type" -> currentCatchupType = value
                            "catchup-days" -> currentCatchupDays = value
                            "catchup-source" -> currentCatchupSource = value
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
        // Determine type based on URL extension or forced logic
        val type = if (uris.any { uri -> 
            val u = uri.lowercase()
            u.contains(".m3u8") || u.contains(".ts") || u.contains(".mpd") || u.contains(".php") 
        }) Type.STREAM else Type.WEB
        
        return TV(
            id = id,
            apiId = apiId,
            name = name,
            title = name,
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
