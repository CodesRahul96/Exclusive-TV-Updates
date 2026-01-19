package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object PlsParser {
    private const val TAG = "PlsParser"

    fun parse(content: String): List<TV> {
        val list = mutableListOf<TV>()
        if (!content.contains("[playlist]", ignoreCase = true)) {
            return list
        }

        val reader = BufferedReader(StringReader(content))
        val entries = mutableMapOf<Int, PlsEntry>()

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("[")) {
                     val parts = trimmed.split("=", limit = 2)
                     if (parts.size == 2) {
                         val key = parts[0].lowercase()
                         val value = parts[1]
                         
                         if (key.startsWith("file")) {
                             val index = key.substring(4).toIntOrNull()
                             if (index != null) {
                                 getEntry(entries, index).url = value
                             }
                         } else if (key.startsWith("title")) {
                            val index = key.substring(5).toIntOrNull()
                            if (index != null) {
                                getEntry(entries, index).title = value
                            }
                         }
                     }
                }
                line = reader.readLine()
            }
        
            // Convert to TV list
            entries.values.forEachIndexed { i, entry ->
                if (entry.url.isNotEmpty()) {
                val type = Type.STREAM

                    list.add(TV(
                        id = i,
                        apiId = i.toString(),
                        name = entry.title.ifEmpty { "Track ${i+1}" },
                        title = entry.title.ifEmpty { "Track ${i+1}" },
                        description = null,
                        logo = "",
                        image = null,
                        uris = arrayListOf(entry.url),
                        headers = null,
                        group = "Playlist",
                        type = type,
                        drmScheme = null,
                        drmLicenseUrl = null,
                        catchupType = null,
                        catchupDays = null,
                        catchupSource = null, 
                        child = listOf()
                    ))
                }
            }
            
            Log.i(TAG, "Parsed PLS: ${list.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsng PLS", e)
        }
        
        return list
    }

    private fun getEntry(map: MutableMap<Int, PlsEntry>, index: Int): PlsEntry {
        return map.getOrPut(index) { PlsEntry() }
    }

    private data class PlsEntry(
        var url: String = "",
        var title: String = ""
    )
}
