package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    private const val TAG = "M3UParser"
    // Compile Regex once for performance
    // Matches: key="value" OR key=value
    private val PROP_REGEX = Regex("([a-zA-Z0-9\\-_]+)=(?:\"([^\"]*)\"|([^, ]+))")

    fun parse(reader: BufferedReader): List<TV> {
        val channels = mutableListOf<TV>()
        var line: String?

        // Current Channel State
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

                // Fallback ID if missing
                val finalId = if (currentTvgId.isNotEmpty()) currentTvgId else "id_${currentName.hashCode()}"

                channels.add(
                    createTV(
                        channelCount++,
                        finalId,
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
                // Do NOT reset group if it came from a stream-independent tag? 
                // However, M3U logic typically resets per item.
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
        val plainUrls = mutableListOf<String>()

        try {
            while (reader.readLine().also { line = it } != null) {
                try {
                    var trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty()) continue

                    // 1. Normalize tags with spaces (e.g., "# EXTINF" -> "#EXTINF")
                    if (trimmedLine.startsWith("# ")) {
                        val parts = trimmedLine.split(Regex("\\s+"), limit = 2)
                        if (parts.isNotEmpty()) {
                            // Reconstruct "#TAG rest"
                            trimmedLine = parts[0] + (if (parts.size > 1) " " + parts[1] else "")
                        }
                        // Force remove space after # just in case logic above missed simple "# TAG"
                        if (trimmedLine.startsWith("# ")) {
                            trimmedLine = "#" + trimmedLine.substring(1).trimStart()
                        }
                    }

                    if (trimmedLine.contains("#EXT-X-TARGETDURATION") || 
                        trimmedLine.contains("#EXT-X-STREAM-INF") || 
                        trimmedLine.contains("#EXT-X-MEDIA-SEQUENCE")) {
                        return emptyList()
                    }

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
                                "x-forwarded-for" -> globalHeaders["X-Forwarded-For"] = value
                            }
                        }
                        continue
                    }

                    if (trimmedLine.startsWith("#EXTINF:")) {
                        // Save previous if exists
                        saveAndReset()

                        // Extract Name (everything after the last comma)
                        val lastCommaIndex = trimmedLine.lastIndexOf(',')
                        if (lastCommaIndex != -1) {
                            val rawName = trimmedLine.substring(lastCommaIndex + 1).trim()
                            if (rawName.isNotEmpty()) currentName = rawName
                        }

                        if (currentName.isEmpty()) {
                            currentName = "ExclusiveTV ${channelCount + 1}"
                        }

                        // Extract Properties
                        val propertiesPart = if (lastCommaIndex != -1) trimmedLine.substring(0, lastCommaIndex) else trimmedLine

                        val matches = PROP_REGEX.findAll(propertiesPart)
                        for (match in matches) {
                            val key = match.groupValues[1].lowercase()
                            val value = if (match.groupValues[2].isNotEmpty()) match.groupValues[2] else match.groupValues[3]

                            when (key) {
                                // ID
                                "tvg-id", "tvg_id", "channel-id", "channel_id", "id" -> currentTvgId = value

                                // Logo
                                "tvg-logo", "tvg_logo", "logo", "icon", "thumb", "image" -> currentLogo = value

                                // Group
                                "group-title", "group_title", "group", "category", "genre" -> currentGroup = value

                                // Name aliases (override comma name if present)
                                "tvg-name", "tvg_name", "channel-name", "name", "title" -> {
                                    if (value.isNotEmpty()) currentName = value
                                }

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
                                "license-key", "license_key", "license-url", "license_url", "clearkey", "key" -> {
                                    if (value.contains("|")) {
                                        val parts = value.split("|")
                                        currentDrmLicense = parts[0]
                                        if (parts.size > 1) {
                                            val headerParts = parts[1].split("&")
                                            for (h in headerParts) {
                                                val kv = h.split("=", limit = 2)
                                                if (kv.size == 2) {
                                                    currentHeaders[kv[0]] = kv[1]
                                                }
                                            }
                                        }
                                    } else {
                                        currentDrmLicense = value
                                    }
                                }
                                "license-type", "license_type", "drm-scheme", "drm" -> currentDrmScheme = value
                            }
                        }

                    } else if (trimmedLine.startsWith("#EXTGRP:")) {
                        // Group tag often follows EXTINF
                        val group = trimmedLine.substringAfter("#EXTGRP:").trim()
                        if (group.isNotEmpty()) currentGroup = group

                    } else if (trimmedLine.startsWith("#EXTHTTP:") || trimmedLine.startsWith("# EXTHTTP:")) {
                        // Flush previous if URIs exist (meaning this might belong to a new block, though usually headers precede URI)
                        if (currentUris.isNotEmpty()) saveAndReset()

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
                        }

                    } else if (trimmedLine.startsWith("#KODIPROP:")) {
                        // KODIPROP usually precedes the URL
                        if (currentUris.isNotEmpty()) saveAndReset()

                        val parts = trimmedLine.substringAfter("#KODIPROP:").split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()

                            when (key) {
                                "inputstream.adaptive.license_type" -> {
                                    currentDrmScheme = when (value) {
                                        "com.widevine.alpha" -> "widevine"
                                        "com.microsoft.playready" -> "playready"
                                        "org.w3.clearkey" -> "clearkey"
                                        else -> value
                                    }
                                }
                                "inputstream.adaptive.license_key" -> {
                                    if (value.contains("|")) {
                                        val parts = value.split("|")
                                        currentDrmLicense = parts[0]
                                        if (parts.size > 1) {
                                            val headerParts = parts[1].split("&")
                                            for (h in headerParts) {
                                                val kv = h.split("=", limit = 2)
                                                if (kv.size == 2) {
                                                    currentHeaders[kv[0]] = kv[1]
                                                }
                                            }
                                        }
                                    } else {
                                        currentDrmLicense = value
                                    }
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
                            }
                        }
                    } else if (trimmedLine.startsWith("#EXTVLCOPT:")) {
                        if (currentUris.isNotEmpty()) saveAndReset()

                        val parts = trimmedLine.substringAfter("#EXTVLCOPT:").split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim().lowercase()
                            val value = parts[1].trim()
                            when (key) {
                                "http-user-agent", "user-agent" -> currentHeaders["User-Agent"] = value
                                "http-referrer", "http-referer", "referrer", "referer" -> currentHeaders["Referer"] = value
                                "http-origin", "origin" -> currentHeaders["Origin"] = value
                                "http-cookie", "cookie" -> currentHeaders["Cookie"] = value
                            }
                        }
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
                                        when (kv[0]) {
                                            "User-Agent" -> currentHeaders["User-Agent"] = kv[1]
                                            "Referer" -> currentHeaders["Referer"] = kv[1]
                                            "Cookie" -> currentHeaders["Cookie"] = kv[1]
                                            else -> currentHeaders[kv[0]] = kv[1]
                                        }
                                    }
                                }
                            }
                            currentUris.add(finalUrl)
                            
                            // Keep track of all URLs for fallback simple parser
                            if (!isM3U) plainUrls.add(finalUrl)

                            if (currentName.isEmpty() || currentName.startsWith("Channel")) {
                                val extracted = extractNameFromUrl(finalUrl)
                                if (extracted.isNotEmpty()) currentName = extracted
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

        // Add last channel
        saveAndReset()

        // Fallback for plain lists (no M3U headers found but URLs present)
        if (channels.isEmpty() && !isM3U && plainUrls.isNotEmpty()) {
            for ((index, l) in plainUrls.withIndex()) {
                channels.add(createTV(index, "", "", "", "", listOf(l), mapOf(), null, null, null, null, null))
            }
        }

        return channels
    }

    // Overload for String compatibility (wrapping)
    fun parse(content: String): List<TV> {
        return parse(BufferedReader(StringReader(content)))
    }
    
    // Extracted name helper
    private fun extractNameFromUrl(url: String): String {
        try {
            // Fast Path: String manipulation to avoid expensive Uri.parse for thousands of items
            val queryStart = url.indexOf('?')
            val pathPart = if (queryStart != -1) url.substring(0, queryStart) else url
            val segments = pathPart.split('/')
            
            if (segments.isEmpty()) return ""
            
            var pIdx = segments.lastIndex
            while (pIdx >= 0 && (segments[pIdx].isEmpty() || segments[pIdx].contains("."))) {
                val s = segments[pIdx].lowercase()
                if (s.endsWith(".m3u8") || s.endsWith(".mpd") || s.endsWith(".ts") || s.endsWith(".m4s")) {
                     val name = s.substringBeforeLast('.')
                     if (name != "index" && name != "master" && name != "manifest" && name != "playlist" && name != "chunk") {
                         // If it's a meaningful filename, use it
                         return name.replace('_', ' ').replace('-', ' ').trim()
                     }
                }
                pIdx--
            }
            
            if (pIdx >= 0) {
                 var name = segments[pIdx]
                 // Skip hash-like hex IDs
                 if (name.length > 20 && name.matches(Regex("[a-fA-F0-9]+"))) {
                     pIdx--
                     if (pIdx >= 0) name = segments[pIdx]
                 }
                 
                 // Strip common segment/timestamp suffixes (e.g., _12345678)
                 name = name.replace(Regex("[-_]\\d{5,}$"), "")
                 return name.replace('_', ' ').replace('-', ' ').trim()
            }
        } catch (e: Exception) {
        }
        return ""
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
        // Default name if still empty
        val finalName = if (name.isEmpty()) "ExclusiveTV ${id + 1}" else name
        val finalGroup = group

        return TV(
            id = id,
            apiId = apiId,
            name = finalName,
            title = finalName,
            description = null,
            logo = logo,
            image = null,
            uris = ArrayList(uris),
            headers = if (headers.isNotEmpty()) HashMap(headers) else null,
            group = finalGroup,
            type = Type.STREAM,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicense,
            catchupType = catchupType,
            catchupDays = catchupDays,
            catchupSource = catchupSource,
            child = listOf()
        )
    }
}
