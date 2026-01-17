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
    private const val DEFAULT_EPG_URL = "https://avkb.short.gy/epg.xml.gz"
    
    private val epgUrl: String
        get() = SP.epg.takeIf { !it.isNullOrEmpty() } ?: DEFAULT_EPG_URL
    
    private var epgData = mutableMapOf<String, MutableList<EPGProgram>>()
    private val normalizedCache = mutableMapOf<String, String>()
    private var cacheFile: File? = null

    fun init(context: android.content.Context) {
        cacheFile = File(context.cacheDir, "epg_cache.xml.gz")
    }

    private fun normalizeName(name: String): String {
        return normalizedCache.getOrPut(name) {
            name.lowercase(Locale.ROOT)
                .replace(Regex("\\(.*?\\)"), "") // Remove content in brackets (e.g. Jio, HD, TS) - Keep this for noise reduction
                // Removed HD/SD stripping to preventing collisions between Star Gold and Star Gold HD
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
                val request = Request.Builder().url(epgUrl).build()
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

    private var epgDataById = mutableMapOf<String, MutableList<EPGProgram>>() // New map for ID lookup

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
                            val nextType = try { parser.next() } catch (e: Exception) { break }
                            if (nextType == XmlPullParser.END_DOCUMENT) break
                            
                            if (nextType == XmlPullParser.START_TAG) {
                                depth++
                                if (parser.name == "display-name") {
                                    val dn = try { parser.nextText() } catch (e: Exception) { "" }
                                    if (dn.isNotEmpty()) channelIdToNames[id]?.add(dn)
                                    depth-- // nextText consumes END_TAG
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
                        var innerEvent = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
                        while (innerEvent != XmlPullParser.END_DOCUMENT) {
                            if (innerEvent == XmlPullParser.END_TAG && parser.name == "programme") break
                            if (innerEvent == XmlPullParser.START_TAG) {
                                if (parser.name == "title") title = try { parser.nextText() } catch (e: Exception) { "" }
                                else if (parser.name == "desc") desc = try { parser.nextText() } catch (e: Exception) { "" }
                            }
                            innerEvent = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
                        }
                        if (title.isNotEmpty()) {
                            rawPrograms.add(channelId to EPGProgram(title, start, stop, desc))
                        }
                    }
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        
        val newData = mutableMapOf<String, MutableList<EPGProgram>>()
        val newDataById = mutableMapOf<String, MutableList<EPGProgram>>() // Populate ID map

        for ((id, prog) in rawPrograms) {
            // Populate ID map
            newDataById.getOrPut(id) { mutableListOf() }.add(prog)
            
            // Populate Name map
            val names = channelIdToNames[id] ?: setOf(id)
            for (n in names) {
                val norm = normalizeName(n)
                newData.getOrPut(norm) { mutableListOf() }.add(prog)
            }
        }

        newData.forEach { (_, progs) -> progs.sortBy { it.start } }
        newDataById.forEach { (_, progs) -> progs.sortBy { it.start } }
        
        epgData = newData
        epgDataById = newDataById
        
        return channelIdToNames.size
    }

    fun getCurrentProgram(channelName: String, channelApiId: String = ""): EPGProgram? {
        val now = System.currentTimeMillis()
        
        // Try ID match first if available
        if (channelApiId.isNotEmpty()) {
            val progsById = epgDataById[channelApiId]
            if (progsById != null) {
                return progsById.find { now in it.start until it.stop }
            }
        }
        
        // Fallback to Name match
        val normalized = normalizeName(channelName)
        val programs = epgData[normalized] ?: return null
        return programs.find { now in it.start until it.stop }
    }

    fun getUpcomingProgram(channelName: String, channelApiId: String = ""): EPGProgram? {
        val now = System.currentTimeMillis()
        
        // Try ID match first
        if (channelApiId.isNotEmpty()) {
             val progsById = epgDataById[channelApiId]
             if (progsById != null) {
                 return progsById.find { it.start > now }
             }
        }

        // Fallback to Name match
        val normalized = normalizeName(channelName)
        val programs = epgData[normalized] ?: return null
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
