package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

object ParserFactory {
    private const val TAG = "ParserFactory"

    /**
     * Detects the format and parses the content into a list of TV channels.
     * Supports M3U, JSON, and fallback to plain list.
     */
    fun parse(content: String): List<TV> {
        val trimmed = content.trim()
        
        return when {
            trimmed.startsWith("#EXTM3U") -> {
                if (trimmed.contains("#KODIPROP")) {
                    KodiParser.parse(trimmed)
                } else {
                    M3UParser.parse(trimmed)
                }
            }
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                GenericJsonParser.parse(trimmed)
            }
            trimmed.contains("#EXTINF") -> {
                if (trimmed.contains("#KODIPROP")) {
                    KodiParser.parse(trimmed)
                } else {
                    M3UParser.parse(trimmed)
                }
            }
            else -> {
                // FALLBACK: Could be a plain list of URLs or unknown JSON
                val m3uResult = M3UParser.parse(trimmed)
                if (m3uResult.isNotEmpty()) m3uResult
                else GenericJsonParser.parse(trimmed)
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
            else -> {
                // Best effort
                M3UParser.parse(reader)
            }
        }
    }
}
