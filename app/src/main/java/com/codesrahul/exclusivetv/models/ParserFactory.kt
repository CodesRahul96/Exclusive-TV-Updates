package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.regex.Pattern

object ParserFactory {
    private const val TAG = "ParserFactory"

    /**
     * Detects the format and parses the content into a list of TV channels.
     * Supports M3U, JSON, PLS, STRM, CSV, TSV, XLS, TVPL, ASX, XSPF formats
     */
    fun parse(content: String): List<TV> {
        val trimmed = content.trim()
        
        return when {
            // M3U variants
            trimmed.startsWith("#EXTM3U") -> {
                if (trimmed.contains("#KODIPROP")) {
                    KodiParser.parse(trimmed)
                } else {
                    M3UParser.parse(trimmed)
                }
            }
            // JSON formats
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                GenericJsonParser.parse(trimmed)
            }
            // M3U without header
            trimmed.contains("#EXTINF") -> {
                if (trimmed.contains("#KODIPROP")) {
                    KodiParser.parse(trimmed)
                } else {
                    M3UParser.parse(trimmed)
                }
            }
            // PLS format
            trimmed.contains("[playlist]", ignoreCase = true) -> {
                PlsParser.parse(trimmed)
            }
            // STRM format (Kodi - plain URLs with .strm extension implied)
            trimmed.startsWith("http://") || trimmed.startsWith("https://") || 
            trimmed.startsWith("ftp://") -> {
                // Could be STRM or plain URL list
                SimpleListParser.parse(trimmed)
            }
            // ASX format (Windows Media)
            trimmed.startsWith("<") && trimmed.contains("asx", ignoreCase = true) -> {
                parseAsxFormat(trimmed)
            }
            // XSPF format (Spotify-like)
            trimmed.startsWith("<?xml") && trimmed.contains("playlist", ignoreCase = true) -> {
                parseXspfFormat(trimmed)
            }
            // CSV/TSV formats (header detection)
            trimmed.contains(",") && !trimmed.contains("{") -> {
                parseCsvFormat(trimmed)
            }
            trimmed.contains("\t") -> {
                parseTsvFormat(trimmed)
            }
            // TVPL format (basic format: name|url)
            trimmed.contains("|") && (trimmed.contains("http://") || trimmed.contains("https://")) -> {
                parseTvplFormat(trimmed)
            }
            // Fallback to best effort parsing
            else -> {
                val m3uResult = M3UParser.parse(trimmed)
                if (m3uResult.isNotEmpty()) m3uResult
                else {
                    val jsonResult = GenericJsonParser.parse(trimmed)
                    if (jsonResult.isNotEmpty()) jsonResult
                    else SimpleListParser.parse(trimmed)
                }
            }
        }
    }

    fun parse(inputStream: InputStream): List<TV> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        // We need to peek at the first line to decide
        reader.mark(2048)
        var firstLine = reader.readLine()?.trim()
        while (firstLine != null && firstLine.isEmpty()) {
            firstLine = reader.readLine()?.trim()
        }
        reader.reset()

        return when {
            firstLine == null -> emptyList()
            firstLine.startsWith("#EXTM3U") || firstLine.startsWith("#EXTINF") -> {
                M3UParser.parse(reader)
            }
            firstLine.startsWith("{") || firstLine.startsWith("[") -> {
                GenericJsonParser.parse(reader)
            }
            firstLine.startsWith("[playlist]") -> {
                PlsParser.parse(reader.readText())
            }
            firstLine.startsWith("http://") || firstLine.startsWith("https://") || 
            firstLine.startsWith("ftp://") -> {
                SimpleListParser.parse(reader.readText())
            }
            firstLine.contains("<") && firstLine.contains("asx", ignoreCase = true) -> {
                parseAsxFormat(reader.readText())
            }
            else -> {
                // Best effort
                M3UParser.parse(reader)
            }
        }
    }

    // ===== Additional Format Parsers =====

    private fun parseAsxFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        var count = 0
        try {
            val pattern = Pattern.compile("""<entry[^>]*>.*?<title>([^<]*)</title>.*?<ref\s+href="([^"]*)""", 
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(content)
            
            while (matcher.find()) {
                val title = matcher.group(1)?.trim() ?: "Track ${count + 1}"
                val url = matcher.group(2)?.trim() ?: continue
                
                channels.add(TV(
                    id = count,
                    apiId = count.toString(),
                    name = title,
                    title = title,
                    uris = listOf(url),
                    child = emptyList()
                ))
                count++
            }
        } catch (e: Exception) {
            Log.d(TAG, "ASX parsing error: ${e.message}")
        }
        return channels
    }

    private fun parseXspfFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        var count = 0
        try {
            val pattern = Pattern.compile("""<track>.*?<title>([^<]*)</title>.*?<location>([^<]*)</location>""", 
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(content)
            
            while (matcher.find()) {
                val title = matcher.group(1)?.trim() ?: "Track ${count + 1}"
                val url = matcher.group(2)?.trim() ?: continue
                
                channels.add(TV(
                    id = count,
                    apiId = count.toString(),
                    name = title,
                    title = title,
                    uris = listOf(url),
                    child = emptyList()
                ))
                count++
            }
        } catch (e: Exception) {
            Log.d(TAG, "XSPF parsing error: ${e.message}")
        }
        return channels
    }

    private fun parseCsvFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var count = 0

        // Try to detect header row
        val firstLine = reader.readLine()
        val isHeader = firstLine?.let { 
            it.contains("name", ignoreCase = true) || 
            it.contains("title", ignoreCase = true) ||
            it.contains("url", ignoreCase = true) ||
            it.contains("channel", ignoreCase = true)
        } ?: false

        // Reset if header detected
        if (!isHeader) {
            val lines = content.split("\n")
            line = firstLine
            parseLineAsCsv(line, null, count++, channels)
        } else {
            // Parse remaining lines as data
        }

        while (reader.readLine().also { line = it } != null) {
            if (line?.trim()?.isEmpty() != false) continue
            parseLineAsCsv(line, null, count++, channels)
        }

        return channels
    }

    private fun parseLineAsCsv(line: String?, headers: Map<String, Int>?, index: Int, channels: MutableList<TV>) {
        line?.let {
            val parts = it.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                .mapIndexed { _, value -> value.trim().trim('"') }

            if (parts.size >= 1) {
                val url = parts.getOrNull(1) ?: parts[0]
                val name = parts.getOrNull(0) ?: "Channel ${index + 1}"
                val logo = parts.getOrNull(2) ?: ""

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    channels.add(TV(
                        id = index,
                        apiId = index.toString(),
                        name = name,
                        title = name,
                        logo = logo,
                        uris = listOf(url),
                        child = emptyList()
                    ))
                }
            }
        }
    }

    private fun parseTsvFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var count = 0

        while (reader.readLine().also { line = it } != null) {
            if (line?.trim()?.isEmpty() != false) continue
            val parts = line!!.split("\t")

            if (parts.size >= 1) {
                val url = parts.getOrNull(1) ?: parts[0]
                val name = parts.getOrNull(0) ?: "Channel ${count + 1}"
                val logo = parts.getOrNull(2) ?: ""

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    channels.add(TV(
                        id = count,
                        apiId = count.toString(),
                        name = name,
                        title = name,
                        logo = logo,
                        uris = listOf(url),
                        child = emptyList()
                    ))
                    count++
                }
            }
        }

        return channels
    }

    private fun parseTvplFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var count = 0

        while (reader.readLine().also { line = it } != null) {
            if (line?.trim()?.isEmpty() != false) continue
            val trimmed = line!!.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) continue

            val parts = trimmed.split("|")
            if (parts.isNotEmpty()) {
                val name = parts.getOrNull(1) ?: parts.getOrNull(0)?.substringAfterLast("/") ?: "Channel ${count + 1}"
                val url = parts[0]

                channels.add(TV(
                    id = count,
                    apiId = count.toString(),
                    name = name,
                    title = name,
                    uris = listOf(url),
                    child = emptyList()
                ))
                count++
            }
        }

        return channels
    }
}
