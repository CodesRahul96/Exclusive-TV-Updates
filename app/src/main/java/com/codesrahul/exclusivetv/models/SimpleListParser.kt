package com.codesrahul.exclusivetv.models

import android.util.Log
import java.io.BufferedReader
import java.io.StringReader

object SimpleListParser {
    private const val TAG = "SimpleListParser"

    fun parse(content: String): List<TV> {
        val list = mutableListOf<TV>()
        val reader = BufferedReader(StringReader(content))
        var line: String? = reader.readLine()
        var count = 0

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("ftp://"))) {
                // Detect audio formats from URL
                val audioFormats = AudioFormatDetector.detectFromUrl(trimmed)
                val audioFormatNames = audioFormats.map { it.name }.toSet()
                
                // Determine compatible devices
                val compatibleDevices = mutableSetOf("firetv", "androidtv", "mobile", "web")
                audioFormats.forEach { format ->
                    if (format in setOf(AudioFormat.EAC3_JOC)) {
                        compatibleDevices.retainAll(setOf("firetv", "androidtv_11_plus", "smart_tv"))
                    }
                }
                
                list.add(
                    TV(
                        id = count,
                        apiId = count.toString(),
                        name = "ExclusiveTV ${count + 1}",
                        title = "ExclusiveTV ${count + 1}",
                        description = null,
                        logo = "",
                        image = null,
                        uris = arrayListOf(trimmed),
                        headers = null,
                        group = "",
                        type = Type.STREAM,
                        drmScheme = null,
                        drmLicenseUrl = null,
                        catchupType = null,
                        catchupDays = null,
                        catchupSource = null,
                        audioFormats = audioFormatNames,
                        compatibleDevices = compatibleDevices,
                        child = listOf()
                    )
                )
                count++
            }
            line = reader.readLine()
        }

        if (list.isNotEmpty()) {
        }
        return list
    }
}
