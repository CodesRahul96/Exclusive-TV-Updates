package com.codesrahul.exclusivetv

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.util.*
import java.util.zip.GZIPInputStream
import java.text.SimpleDateFormat

import com.codesrahul.exclusivetv.models.EPGProgram

object EPGManager {
    private const val TAG = "EPGManager"
    private const val EPG_URL = "https://avkb.short.gy/jioepg.xml.gz"
    
    private var epgData = mutableMapOf<String, MutableList<EPGProgram>>()
    private val normalizedCache = mutableMapOf<String, String>()
    private var cacheFile: File? = null

    fun init(context: android.content.Context) {
        cacheFile = File(context.cacheDir, "epg_cache.xml.gz")
    }

    private fun normalizeName(name: String): String {
        return normalizedCache.getOrPut(name) {
            name.lowercase(Locale.ROOT)
                .replace(Regex("\\b(hd|fhd|uhd|4k)\\b"), "")
                .replace(Regex("[^a-z0-9]"), "")
                .trim()
        }
    }
    
    private val client = OkHttpClient()
    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    )
    
    var epgStatus = "Not Loaded"

    suspend fun fetchEPG(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile ?: return@withContext
            val now = System.currentTimeMillis()
            
            if (force || !file.exists() || (now - file.lastModified() > 12 * 3600_000L)) {
                epgStatus = "Downloading..."
                val request = Request.Builder().url(EPG_URL).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body() ?: return@use
                        FileOutputStream(file).use { out ->
                            body.byteStream().copyTo(out)
                        }
                    }
                }
            }

            if (file.exists()) {
                epgStatus = "Parsing..."
                val count = FileInputStream(file).use { fis ->
                    GZIPInputStream(fis).use { gis ->
                        parseXML(gis)
                    }
                }
                epgStatus = "Loaded progs for $count channels"
            }
        } catch (e: Exception) {
            epgStatus = "Error: ${e.message}"
            Log.e(TAG, "EPG Error", e)
        }
    }

    private fun parseXML(inputStream: InputStream): Int {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        var eventType = parser.eventType
        val channelIdToNames = mutableMapOf<String, MutableSet<String>>()
        val rawPrograms = mutableListOf<Pair<String, EPGProgram>>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName == "channel") {
                    val id = parser.getAttributeValue(null, "id")
                    if (id != null) {
                        channelIdToNames.getOrPut(id) { mutableSetOf() }.add(id)
                        var depth = 1
                        while (depth > 0) {
                            val nextType = parser.next()
                            if (nextType == XmlPullParser.START_TAG) {
                                depth++
                                if (parser.name == "display-name") {
                                    val dn = parser.nextText()
                                    channelIdToNames[id]?.add(dn)
                                    depth--
                                }
                            } else if (nextType == XmlPullParser.END_TAG) {
                                depth--
                            }
                        }
                    }
                } else if (tagName == "programme") {
                    val channelId = parser.getAttributeValue(null, "channel")
                    val start = parseDate(parser.getAttributeValue(null, "start"))
                    val stop = parseDate(parser.getAttributeValue(null, "stop"))
                    
                    if (channelId != null && start != null && stop != null) {
                        var title = ""
                        var desc = ""
                        var innerEvent = parser.next()
                        while (innerEvent != XmlPullParser.END_DOCUMENT) {
                            if (innerEvent == XmlPullParser.END_TAG && parser.name == "programme") break
                            if (innerEvent == XmlPullParser.START_TAG) {
                                if (parser.name == "title") title = parser.nextText()
                                else if (parser.name == "desc") desc = parser.nextText()
                            }
                            innerEvent = parser.next()
                        }
                        rawPrograms.add(channelId to EPGProgram(title, start, stop, desc))
                    }
                }
            }
            eventType = parser.next()
        }
        
        val newData = mutableMapOf<String, MutableList<EPGProgram>>()
        for ((id, prog) in rawPrograms) {
            val names = channelIdToNames[id] ?: setOf(id)
            for (n in names) {
                val norm = normalizeName(n)
                newData.getOrPut(norm) { mutableListOf() }.add(prog)
            }
        }

        newData.forEach { (_, progs) -> progs.sortBy { it.start } }
        epgData = newData
        return channelIdToNames.size
    }

    fun getCurrentProgram(channelName: String): EPGProgram? {
        val normalized = normalizeName(channelName)
        val programs = epgData[normalized] ?: return null
        val now = System.currentTimeMillis()
        return programs.find { now in it.start until it.stop }
    }

    fun getUpcomingProgram(channelName: String): EPGProgram? {
        val normalized = normalizeName(channelName)
        val programs = epgData[normalized] ?: return null
        val now = System.currentTimeMillis()
        return programs.find { it.start > now }
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        val clean = dateStr.trim()
        for (format in dateFormats) {
            try { return format.parse(clean)?.time } catch (e: Exception) {}
        }
        return null
    }
}
