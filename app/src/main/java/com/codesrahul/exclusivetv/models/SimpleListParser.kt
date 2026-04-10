package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

/**
 * Smart Universal List Parser.
 * Handles plain text, CSV-style, and pipe-delimited stream lists.
 * Extracts names and logos heuristically when standard tags are missing.
 */
object SimpleListParser {
    private const val TAG = "SimpleListParser"

    fun parse(content: String): List<TV> {
        val list = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var lineString: String? = reader.readLine()
        var count = 0

        while (lineString != null) {
            val trimmed = lineString.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // Support multiple patterns:
                // 1. URL
                // 2. Name,URL or URL,Name
                // 3. Name|URL or URL|Name
                
                var url = ""
                var name = ""
                
                val delimiters = listOf("|", ",", ";", "\t")
                var foundMatch = false
                
                for (delim in delimiters) {
                    if (trimmed.contains(delim)) {
                        val parts = trimmed.split(delim, limit = 2)
                        val p1 = parts[0].trim()
                        val p2 = parts[1].trim()
                        
                        if (p1.contains("://")) {
                            url = p1
                            name = p2
                            foundMatch = true
                            break
                        } else if (p2.contains("://")) {
                            url = p2
                            name = p1
                            foundMatch = true
                            break
                        }
                    }
                }
                
                if (!foundMatch && trimmed.contains("://")) {
                    url = trimmed
                }

                if (url.isNotEmpty()) {
                    val finalName = if (name.isNotEmpty()) name else extractNameFromUrl(url, count)
                    list.add(
                        TV(
                            id = count,
                            apiId = "s_$count",
                            name = finalName,
                            title = finalName,
                            logo = "",
                            uris = arrayListOf(url),
                            headers = null,
                            group = "General",
                            type = Type.STREAM,
                            audioFormats = AudioFormatDetector.detectFromUrl(url).map { it.name }.toSet(),
                            compatibleDevices = setOf("androidtv", "mobile"),
                            child = emptyList()
                        )
                    )
                    count++
                }
            }
            lineString = reader.readLine()
        }
        return list
    }

    private fun extractNameFromUrl(url: String, index: Int): String {
        return try {
            val end = if (url.contains("?")) url.indexOf("?") else url.length
            val path = url.substring(0, end)
            val name = path.substringAfterLast("/").substringBeforeLast(".")
            if (name.length > 2) name.replace("_", " ").replace("-", " ") else "Channel ${index + 1}"
        } catch (e: Exception) {
            "Channel ${index + 1}"
        }
    }
}
