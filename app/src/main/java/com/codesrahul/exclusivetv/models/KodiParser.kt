package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object KodiParser {
    private const val TAG = "KodiParser"
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

        while (reader.readLine().also { line = it } != null) {
            try {
                var trimmedLine = line?.trim() ?: continue
                if (trimmedLine.isEmpty()) continue

                // Normalize tags with spaces
                if (trimmedLine.startsWith("# ")) {
                    trimmedLine = "#" + trimmedLine.substring(1).trimStart()
                }

                if (trimmedLine.startsWith("#EXTM3U")) {
                    continue
                }

                else if (trimmedLine.startsWith("#EXTINF:")) {
                    // Should save previous channel if exists
                    saveAndReset()

                    // Extract Name (everything after the last comma)
                    val lastCommaIndex = trimmedLine.lastIndexOf(',')
                    if (lastCommaIndex != -1) {
                        currentName = trimmedLine.substring(lastCommaIndex + 1).trim()
                    } else {
                        currentName = "ExclusiveTV ${channelCount + 1}"
                    }

                    // Extract Properties
                    val propertiesPart = if (lastCommaIndex != -1) trimmedLine.substring(0, lastCommaIndex) else trimmedLine
                    
                    val matches = PROP_REGEX.findAll(propertiesPart)
                    for (match in matches) {
                        val key = match.groupValues[1].lowercase() 
                        val value = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else match.groupValues[3]
                        
                        when (key) {
                            "tvg-id", "tvg_id", "channel-id", "channel_id", "id" -> currentTvgId = value
                            "tvg-logo", "tvg_logo", "logo", "icon", "thumb", "image" -> currentLogo = value
                            "group-title", "group_title", "group", "category", "genre" -> currentGroup = value
                            "tvg-name", "tvg_name", "channel-name", "name", "title" -> if (value.isNotEmpty()) currentName = value 
                            
                            // Catchup
                            "catchup", "catchup-type", "catchup_type" -> currentCatchupType = value
                            "catchup-days", "catchup_days", "timeshift" -> currentCatchupDays = value
                            "catchup-source", "catchup_source" -> currentCatchupSource = value
                            
                            // Headers (Standard & Extended)
                            "user-agent", "user_agent", "http-user-agent" -> currentHeaders["User-Agent"] = value
                            "referer", "referrer", "http-referer" -> currentHeaders["Referer"] = value
                            "cookie", "cookies", "http-cookie" -> currentHeaders["Cookie"] = value
                            "origin", "http-origin" -> currentHeaders["Origin"] = value
                            
                            // DRM
                            "license-key", "license_key", "license-url", "license_url", "clearkey", "key" -> currentDrmLicense = value
                            "license-type", "license_type", "drm-scheme", "drm" -> currentDrmScheme = value
                        }
                    }

                } else if (trimmedLine.startsWith("#EXTHTTP:") || trimmedLine.startsWith("# EXTHTTP:")) {
                    // Flush previous if URIs exist (meaning this tag belongs to a NEW block)
                    if (currentUris.isNotEmpty()) saveAndReset()
                    
                    // Parse JSON headers
                    val jsonStr = trimmedLine.substringAfter("HTTP:").trim()
                    try {
                        val jsonObject = org.json.JSONObject(jsonStr)
                        
                        // Handle standard headers object if exists
                        val headers = jsonObject.optJSONObject("headers")
                        if (headers != null) {
                            val keys = headers.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = headers.getString(key)
                                currentHeaders[key] = value
                            }
                        } else {
                            // Flat JSON headers (some providers use "cookie" directly at root)
                            val keys = jsonObject.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                if (key != "payload" && key != "headers") {
                                    val value = jsonObject.getString(key)
                                    // Normalize key
                                    val normKey = when(key.lowercase()) {
                                        "cookie" -> "Cookie"
                                        "user-agent" -> "User-Agent"
                                        "referer" -> "Referer"
                                        "origin" -> "Origin"
                                        else -> key
                                    }
                                    currentHeaders[normKey] = value
                                }
                            }
                        }

                        // Special Payload for Star license_key if provided in JSON
                        val payload = jsonObject.optJSONObject("payload")
                        if (payload != null && payload.has("license_key")) {
                            currentDrmLicense = payload.getString("license_key")
                        }
                        
                    } catch (e: Exception) {
                    }

                } else if (trimmedLine.startsWith("#KODIPROP:")) {
                    // Property applies to the FOLLOWING channel (don't reset yet unless URIs exist)
                    if (currentUris.isNotEmpty()) {
                        saveAndReset()
                    }

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
                            "inputstream.adaptive.manifest_type" -> {
                                // e.g. "mpd", "hls", "ism" - implies STREAM type
                            }
                            "inputstream.adaptive.stream_headers" -> {
                                val headerPairs = value.split("&")
                                for (pair in headerPairs) {
                                    val kv = pair.split("=", limit = 2)
                                    if (kv.size == 2) {
                                        currentHeaders[kv[0]] = kv[1]
                                    }
                                }
                            }
                            "mimetype", "inputstream.adaptive.mimetype" -> {
                                // Hint
                            }
                        }
                    }
                } else if (trimmedLine.startsWith("#EXTVLCOPT:")) {
                    if (currentUris.isNotEmpty()) saveAndReset()

                    val parts = trimmedLine.substringAfter("#EXTVLCOPT:").split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().lowercase()
                        val value = parts[1].trim()
                        when(key) {
                            "http-user-agent", "user-agent" -> currentHeaders["User-Agent"] = value
                            "http-referrer", "http-referer", "referrer", "referer" -> currentHeaders["Referer"] = value
                            "http-origin", "origin" -> currentHeaders["Origin"] = value
                            "http-cookie", "cookie" -> currentHeaders["Cookie"] = value
                        }
                    }
                } else if (trimmedLine.startsWith("#EXTGRP:")) {
                    val group = trimmedLine.substringAfter("#EXTGRP:").trim()
                    if (group.isNotEmpty()) currentGroup = group

                } else if (!trimmedLine.startsWith("#")) {
                    // Verify it's a URL
                    if (trimmedLine.contains("://")) {
                         var finalUrl = trimmedLine
                         
                         // Handle pipe headers
                         if (trimmedLine.contains("|")) {
                             val urlParts = trimmedLine.split("|", limit = 2)
                             finalUrl = urlParts[0].trim()
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

                         // STAR NETWORK FIX: Generate name from URL if #EXTINF is missing
                         if (currentName.isEmpty()) {
                             val namePart = when {
                                 finalUrl.contains("/mp1/") -> finalUrl.substringAfter("/mp1/").substringBefore("/")
                                 finalUrl.contains("/mp2/") -> finalUrl.substringAfter("/mp2/").substringBefore("/")
                                 finalUrl.contains("/live/") -> {
                                     val segment = finalUrl.substringAfter("/live/").substringAfter("/")
                                     if (segment.contains("/")) segment.substringBefore("/") else segment.substringBefore(".")
                                 }
                                 else -> ""
                             }
                             
                             if (namePart.isNotEmpty()) {
                                 currentName = namePart.replace("-", " ").capitalizeWords()
                                 if (currentGroup.isEmpty()) currentGroup = "Star Live"
                             } else {
                                 currentName = "ExclusiveTV ${channelCount + 1}"
                             }
                         }

                         currentUris.add(finalUrl)
                    }
                }
            } catch (e: Exception) {
            }
        }

        // Add last channel
        saveAndReset()

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
        // Force stream type if DRM is present or extension matches, otherwise default to STREAM for M3U entries
        val type = Type.STREAM
        
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

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { 
        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString() } 
    }
}
