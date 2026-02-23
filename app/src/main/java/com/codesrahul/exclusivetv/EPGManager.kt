package com.codesrahul.exclusivetv

import android.content.Context
import android.util.Log
import android.util.Xml
import com.codesrahul.exclusivetv.models.EPGProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

object EPGManager {
    private const val TAG = "EPGManager"
    private const val CACHE_FILE = "epg_cache.xml.gz"
    private const val DEFAULT_EPG_URL = "https://tsepg.cf/epg.xml.gz"

    private val REGEX_BRACKETS = Regex("\\(.*?\\)")
    private val REGEX_PREFIX_D = Regex("^d ")
    private val REGEX_PREFIX_DD = Regex("^dd ")
    private val REGEX_NON_ALPHANUM = Regex("[^a-z0-9]")

    private var epgData = mutableMapOf<String, MutableList<EPGProgram>>()
    private var epgDataById = mutableMapOf<String, MutableList<EPGProgram>>()
    private val normalizedCache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    var epgStatus = "Not Loaded"
        private set

    private fun getCacheFile(context: Context): File {
        return File(context.cacheDir, CACHE_FILE)
    }

    private val epgUrl: String
        get() = SP.epg.takeIf { !it.isNullOrEmpty() } ?: SP.remoteEpgUrl.takeIf { !it.isNullOrEmpty() } ?: DEFAULT_EPG_URL

    fun init(context: Context) {
        if (epgData.isNotEmpty()) return
        fetchEPG(context)
    }

    fun fetchEPG(context: Context, force: Boolean = false) {
        if (!SP.epgEnabled) return
        
        CoroutineScope(Dispatchers.IO).launch {
            fetchEPGInternal(context, force)
        }
    }

    suspend fun fetchEPG(force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val ctx = MyTVApplication.getInstance()
            if (SP.epgEnabled) {
                fetchEPGInternal(ctx, force)
            }
        }
    }

    private suspend fun fetchEPGInternal(context: Context, force: Boolean) {
        mutex.withLock {
            try {
                val file = getCacheFile(context)
                val now = Utils.getDateTimestamp() * 1000L
                val client = SecureHttpClient.client
                
                if (force || !file.exists() || (now - file.lastModified() > 12 * 3600_000L)) {
                    epgStatus = "Downloading..."
                    Log.d(TAG, "Fetching EPG from: $epgUrl")
                    val request = Request.Builder().url(epgUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }

                if (file.exists()) {
                    epgStatus = "Parsing..."
                    Log.d(TAG, "Parsing EPG file: ${file.absolutePath}")
                    val count = FileInputStream(file).use { fis ->
                        GZIPInputStream(fis).use { gis ->
                            parseXML(gis)
                        }
                    }
                    epgStatus = "Loaded progs for $count channels"
                    Log.d(TAG, "Parsing Complete! $epgStatus")
                }
                Unit
            } catch (e: Exception) {
                epgStatus = "Error: ${e.message}"
                Log.e(TAG, "EPG Error: ${e.message}", e)
            }
            Unit
        }
    }

    private fun parseXML(inputStream: InputStream): Int {
        val formats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        )

        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        val channelIdToNames = mutableMapOf<String, MutableSet<String>>()
        val newData = mutableMapOf<String, MutableList<EPGProgram>>()
        val newDataById = mutableMapOf<String, MutableList<EPGProgram>>()
        
        val now = Utils.getDateTimestamp() * 1000L
        
        var eventType = parser.eventType
        var currentChannelId: String? = null
        var currentProgram: EPGProgram? = null
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "channel" -> {
                            val id = parser.getAttributeValue(null, "id")?.intern()
                            if (id != null) {
                                currentChannelId = id
                                channelIdToNames.getOrPut(id) { mutableSetOf() }.add(id)
                            }
                        }
                        "display-name" -> {
                            if (currentChannelId != null) {
                                try {
                                    val dn = parser.nextText()
                                    if (dn != null) channelIdToNames[currentChannelId!!]?.add(dn)
                                } catch (e: Exception) {}
                            }
                        }
                        "programme" -> {
                            val cid = parser.getAttributeValue(null, "channel")?.intern()
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            
                            val start = parseDate(startStr, formats)
                            val stop = parseDate(stopStr, formats)
                            
                            if (cid != null && start != null && stop != null) {
                                val now = Utils.getDateTimestamp() * 1000L
                                if (stop < (now - 24 * 3600_000L)) {
                                    currentProgram = null
                                } else {
                                    currentProgram = EPGProgram("", start, stop, "")
                                    currentChannelId = cid
                                }
                            }
                        }
                        "title" -> {
                            if (currentProgram != null) {
                                try { currentProgram!!.title = parser.nextText() ?: "" } catch (e: Exception) {}
                            }
                        }
                        "desc" -> {
                            if (currentProgram != null) {
                                try {
                                    var d = parser.nextText() ?: ""
                                    if (d.length > 200) d = d.substring(0, 200) + "..."
                                    currentProgram!!.description = d
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "programme" -> {
                            if (currentProgram != null && currentChannelId != null) {
                                if (currentProgram!!.title.isNotEmpty()) {
                                    newDataById.getOrPut(currentChannelId!!) { mutableListOf() }.add(currentProgram!!)
                                    
                                    val names = channelIdToNames[currentChannelId!!] ?: setOf(currentChannelId!!)
                                    for (n in names) {
                                        val norm = normalizeName(n)
                                        newData.getOrPut(norm) { mutableListOf() }.add(currentProgram!!)
                                    }
                                }
                            }
                            currentProgram = null
                            currentChannelId = null
                        }
                        "channel" -> {
                            currentChannelId = null
                        }
                    }
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { 
                Log.e(TAG, "Parser error: ${e.message}")
                XmlPullParser.END_DOCUMENT 
            }
        }

        newData.forEach { (_, progs) -> progs.sortBy { it.start } }
        newDataById.forEach { (_, progs) -> progs.sortBy { it.start } }
        
        val totalProgs = newDataById.values.sumOf { it.size }
        Log.d(TAG, "Parsed ${channelIdToNames.size} channels and $totalProgs programmes")
        
        epgData = newData
        epgDataById = newDataById
        
        return channelIdToNames.size
    }

    fun getCurrentProgram(channelName: String, channelApiId: String = ""): EPGProgram? {
        if (!SP.epgEnabled) return null
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        
        if (channelApiId.isNotEmpty()) {
            epgDataById[channelApiId]?.find { now in it.start until it.stop }?.let { return it }
        }
        
        val normalized = normalizeName(channelName)
        epgData[normalized]?.find { now in it.start until it.stop }?.let { return it }
        
        // FUZZY FALLBACK: Try finding a key that contains our normalized name, or vice versa
        val fuzzyMatch = epgData.keys.find { key ->
            normalized.length >= 4 && (key.contains(normalized) || normalized.contains(key))
        }
        
        if (fuzzyMatch != null) {
            epgData[fuzzyMatch]?.find { now in it.start until it.stop }?.let { return it }
        }

        return null
    }

    fun getUpcomingProgram(channelName: String, channelApiId: String = ""): EPGProgram? {
        if (!SP.epgEnabled) return null
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        
        if (channelApiId.isNotEmpty()) {
            epgDataById[channelApiId]?.find { it.start > now }?.let { return it }
        }

        val normalized = normalizeName(channelName)
        return epgData[normalized]?.find { it.start > now }
    }

    fun getProgramsForChannel(channelName: String, channelApiId: String = ""): List<EPGProgram> {
        if (channelApiId.isNotEmpty()) {
            epgDataById[channelApiId]?.let { return it }
        }
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

    private fun normalizeName(name: String): String {
        return normalizedCache.getOrPut(name) {
            name.lowercase(Locale.US)
                .replace(REGEX_BRACKETS, "") // Remove bracketed content
                .replace("dd ", "")
                .replace("hd", "")
                .replace("sd", "")
                .replace("tv", "")
                .replace("channel", "")
                .replace("india", "")
                .replace(REGEX_PREFIX_D, "") // Remove "d " prefix
                .replace(REGEX_PREFIX_DD, "") // Remove "dd " prefix
                .replace(REGEX_NON_ALPHANUM, "")
                .trim()
                .intern()
        }
    }

    fun clear() {
        epgData.clear()
        epgDataById.clear()
        normalizedCache.clear()
        epgStatus = "Cleared"
    }
}
