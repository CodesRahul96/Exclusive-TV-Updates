package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.regex.Pattern

/**
 * Universal Parser Factory.
 * Automatically identifies and routes playlist content to the appropriate specialized parser.
 * Upgraded to use the DeepHeuristicParser for JSON and enhanced M3UParser for standard streams.
 */
object ParserFactory {
    private const val TAG = "ParserFactory"

    /**
     * Detects the format and parses the content into a list of TV channels.
     * Supports M3U, JSON, PLS, STRM, CSV, TSV, ASX, XSPF, and TVPL formats.
     */
    fun parse(content: String): List<TV> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()
        
        return when {
            // M3U Variants (Standard, Kodi, Star)
            trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF") -> {
                M3UParser.parse(BufferedReader(StringReader(trimmed)))
            }
            // JSON Formats (Schema-Agnostic Heuristic)
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                DeepHeuristicParser.parse(trimmed)
            }
            // PLS format
            trimmed.contains("[playlist]", ignoreCase = true) -> {
                PlsParser.parse(trimmed)
            }
            // STRM format / Plain URL List
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                SimpleListParser.parse(trimmed)
            }
            // ASX format (Windows Media)
            trimmed.startsWith("<") && trimmed.contains("asx", ignoreCase = true) -> {
                parseAsxFormat(trimmed)
            }
            // XSPF format (XML Playlist)
            trimmed.startsWith("<?xml") && trimmed.contains("playlist", ignoreCase = true) -> {
                parseXspfFormat(trimmed)
            }
            // CSV/TSV formats
            trimmed.contains(",") && !trimmed.contains("{") -> {
                parseCsvFormat(trimmed)
            }
            // XMLTV format (Channel discovery from EPG)
            trimmed.startsWith("<") && (trimmed.contains("<channel") || trimmed.contains("<tv")) -> {
                parseXmlTvFormat(trimmed)
            }
            // Fallback: Use Deep Heuristic Search
            else -> {
                DeepHeuristicParser.parse(trimmed)
            }
        }
    }

    fun parse(inputStream: InputStream): List<TV> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        // Peek at the first character to decide
        reader.mark(1024)
        val firstChar = reader.read()
        reader.reset()

        return when (firstChar) {
            '#'.code -> M3UParser.parse(reader)
            '{'.code, '['.code -> DeepHeuristicParser.parse(reader)
            else -> {
                // Read and delegate to string-based detection
                parse(reader.readText())
            }
        }
    }

    // ===== Legacy XML/Text Parsers (Kept for maximum compatibility) =====

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
                channels.add(TV(id = count, apiId = "asx_$count", name = title, title = title, uris = listOf(url), child = emptyList()))
                count++
            }
        } catch (e: Exception) {}
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
                channels.add(TV(id = count, apiId = "xspf_$count", name = title, title = title, uris = listOf(url), child = emptyList()))
                count++
            }
        } catch (e: Exception) {}
        return channels
    }

    private fun parseCsvFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        var count = 0
        content.split("\n").forEach { line ->
            if (line.isBlank() || !line.contains("://")) return@forEach
            val parts = line.split(",")
            val url = parts.find { it.contains("://") }?.trim() ?: return@forEach
            val name = parts.getOrNull(0)?.trim() ?: "Channel ${count + 1}"
            channels.add(TV(id = count, apiId = "csv_$count", name = name, title = name, uris = listOf(url), child = emptyList()))
            count++
        }
        return channels
    }

    private fun parseXmlTvFormat(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        var count = 0
        try {
            // Simple regex to extract <channel id="xxx"><display-name>Name</display-name>...</channel>
            // and optionally <icon src="..."/>
            val pattern = Pattern.compile("<channel[^>]*id=\"([^\"]*)\"[^>]*>.*?<display-name[^>]*>([^<]*)</display-name>(?:.*?<icon[^>]*src=\"([^\"]*)\")?", 
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(content)
            
            while (matcher.find()) {
                val id = matcher.group(1) ?: ""
                val name = matcher.group(2)?.trim() ?: "Channel ${count+1}"
                val logo = matcher.group(3) ?: ""
                
                // If XMLTV, we don't have a URL, but we can assume the user might have provided a base URL 
                // or we can treat id as a potential stream link if it looks like a URL.
                // For now, we add as a "placeholder" that will be expanded recursively if possible.
                channels.add(TV(
                    id = count, 
                    apiId = "xml_$id", 
                    name = name, 
                    title = name, 
                    logo = logo,
                    uris = if (id.startsWith("http")) listOf(id) else emptyList(),
                    child = emptyList(),
                    group = "EPG Source"
                ))
                count++
            }
        } catch (e: Exception) {
            Log.e(TAG, "XMLTV Parsing Error", e)
        }
        return channels
    }
}
