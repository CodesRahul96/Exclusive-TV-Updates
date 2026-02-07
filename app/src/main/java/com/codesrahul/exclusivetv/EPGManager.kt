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
    private const val DEFAULT_EPG_URL = "https://tsepg.cf/jio.xml.gz"
    
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
    
    private val client = SecureHttpClient.client

    // Removed ThreadLocal to prevent NoSuchMethodError on older APIs (API < 26)
    
    var epgStatus = "Not Loaded"

    suspend fun fetchEPG(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (SecurityUtil.isMaintenanceMode) return@withContext
        try {
            val file = cacheFile ?: return@withContext
            val now = System.currentTimeMillis()
            
            if (force || !file.exists() || (now - file.lastModified() > 12 * 3600_000L)) {
                epgStatus = "Downloading..."
                val request = Request.Builder().url(epgUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body ?: return@use
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
        }
    }

    private var epgDataById = mutableMapOf<String, MutableList<EPGProgram>>() // New map for ID lookup

    private fun parseXML(inputStream: InputStream): Int {
        val formats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        )

        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        var eventType = parser.eventType
        val channelIdToNames = mutableMapOf<String, MutableSet<String>>()
        
        val newData = mutableMapOf<String, MutableList<EPGProgram>>()
        val newDataById = mutableMapOf<String, MutableList<EPGProgram>>()
        val now = Utils.getDateTimestamp() * 1000L

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName == "channel") {
                    val id = parser.getAttributeValue(null, "id")
                    if (id != null) {
                        val internedId = id.intern()
                        channelIdToNames.getOrPut(internedId) { mutableSetOf() }.add(internedId)
                        var depth = 1
                        while (depth > 0) {
                            val nextType = try { parser.next() } catch (e: Exception) { break }
                            if (nextType == XmlPullParser.END_DOCUMENT) break
                            
                            if (nextType == XmlPullParser.START_TAG) {
                                depth++
                                if (parser.name == "display-name") {
                                    val dn = try { parser.nextText() } catch (e: Exception) { "" }
                                    if (dn.isNotEmpty()) channelIdToNames[internedId]?.add(dn)
                                    depth--
                                }
                            } else if (nextType == XmlPullParser.END_TAG) {
                                depth--
                            }
                        }
                    }
                } else if (tagName == "programme") {
                    val channelId = parser.getAttributeValue(null, "channel")?.intern()
                    val start = parseDate(parser.getAttributeValue(null, "start"), formats)
                    val stop = parseDate(parser.getAttributeValue(null, "stop"), formats)
                    
                    if (channelId != null && start != null && stop != null) {
                        // OPTIMIZATION: Discard programs that ended more than 1 hour ago
                        if (stop < (now - 3600_000L)) {
                            parser.next() // Skip content
                            eventType = parser.next()
                            continue
                        }

                        var title = ""
                        var desc = ""
                        var innerEvent = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
                        while (innerEvent != XmlPullParser.END_DOCUMENT) {
                            if (innerEvent == XmlPullParser.END_TAG && parser.name == "programme") break
                            if (innerEvent == XmlPullParser.START_TAG) {
                                if (parser.name == "title") title = try { parser.nextText() } catch (e: Exception) { "" }
                                else if (parser.name == "desc") {
                                    desc = try { parser.nextText() } catch (e: Exception) { "" }
                                    if (desc.length > 200) desc = desc.substring(0, 200) + "..."
                                }
                            }
                            innerEvent = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
                        }
                        
                        if (title.isNotEmpty()) {
                            val prog = EPGProgram(title, start, stop, desc)
                            
                            // Populate ID map directly
                            newDataById.getOrPut(channelId) { mutableListOf() }.add(prog)
                            
                            // Populate Name maps directly using names collected so far
                            // Note: If channel info follows programs (unlikely in XMLTV), this might miss some.
                            // But XMLTV usually lists channels first.
                            val names = channelIdToNames[channelId] ?: setOf(channelId)
                            for (n in names) {
                                val norm = normalizeName(n)
                                newData.getOrPut(norm) { mutableListOf() }.add(prog)
                            }
                        }
                    }
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        
        newData.forEach { (_, progs) -> progs.sortBy { it.start } }
        newDataById.forEach { (_, progs) -> progs.sortBy { it.start } }
        
        epgData = newData
        epgDataById = newDataById
        
        return channelIdToNames.size
    }

    fun getCurrentProgram(channelName: String, channelApiId: String = ""): EPGProgram? {
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        
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
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        
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

    fun getProgramsForChannel(channelName: String, channelApiId: String = ""): List<EPGProgram> {
        // Try ID match first
        if (channelApiId.isNotEmpty()) {
            val progsById = epgDataById[channelApiId]
            if (progsById != null) return progsById
        }
        
        // Fallback to Name match
        val normalized = normalizeName(channelName)
        return epgData[normalized] ?: emptyList()
    }

    private fun parseDate(dateStr: String?, formats: List<SimpleDateFormat>): Long? {
        if (dateStr == null) return null
        val clean = dateStr.trim()
        for (format in formats) {
            try { return format.parse(clean)?.time } catch (e: Exception) {}
        }
        return null
    }

    fun clear() {
        epgData.clear()
        epgDataById.clear()
        normalizedCache.clear()
        // Force GC hint? No, leave it to runtime.
        epgStatus = "Cleared"
    }
}
