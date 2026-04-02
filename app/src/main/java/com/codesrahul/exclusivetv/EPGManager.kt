package com.codesrahul.exclusivetv

import android.content.Context
import android.util.Log
import android.util.Xml
import com.codesrahul.exclusivetv.models.EPGProgram
import kotlinx.coroutines.*
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
    private val DEFAULT_EPG_SOURCES = listOf(
        "https://avkb.short.gy/jioepg.xml.gz",
        "https://avkb.short.gy/epg.xml.gz",
        "https://avkb.short.gy/tsepg.xml.gz",
        "https://tsepg.cf/epg.xml.gz"
    )

    private val REGEX_BRACKETS = Regex("\\(.*?\\)")
    private val REGEX_PREFIX_D = Regex("^d ")
    private val REGEX_PREFIX_DD = Regex("^dd ")
    private val REGEX_NON_ALPHANUM = Regex("[^a-z0-9]")
    private val REGEX_REGION = Regex("(?i)\\s*\\((india|asia|international|uk|usa|uae|dubai|uae|telugu|tamil|hindi|kannada|malayalam|marathi|bengali|gujarati|punjabi|urdu)\\)")

    private var epgData = mutableMapOf<String, MutableList<EPGProgram>>()
    private var epgDataById = mutableMapOf<String, MutableList<EPGProgram>>()
    
    // LRU Cache for normalization to prevent memory bloat
    private val normalizedCache = object : java.util.LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2500
    }
    
    // Simple lookup cache for successful fuzzy matches
    private val fuzzyMatchCache = mutableMapOf<String, String>()
    
    private val mutex = Mutex()

    var epgStatus = "Not Loaded"
        private set

    private fun getCacheFile(context: Context, url: String): File {
        val hash = url.hashCode().toString()
        return File(context.cacheDir, "epg_$hash.xml.gz")
    }

    private fun getEpgUrls(): List<String> {
        val userEpg = SP.epg ?: ""
        val remoteEpg = SP.remoteEpgUrl ?: ""
        val delimiters = "[,;\\|]".toRegex()
        
        val urls = mutableListOf<String>()
        if (userEpg.isNotBlank()) urls.addAll(userEpg.split(delimiters).map { it.trim() }.filter { it.isNotEmpty() })
        if (remoteEpg.isNotBlank()) urls.addAll(remoteEpg.split(delimiters).map { it.trim() }.filter { it.isNotEmpty() })
        
        // ALWAYS append default sources for guaranteed coverage, avoiding duplicates
        val allUrls = urls.toMutableList()
        DEFAULT_EPG_SOURCES.forEach { if (it !in allUrls) allUrls.add(it) }
        
        return allUrls.distinct()
    }

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
            val urls = getEpgUrls()
            epgStatus = "Fetching ${urls.size} hubs..."
            
            val newEpgData = mutableMapOf<String, MutableList<EPGProgram>>()
            val newEpgDataById = mutableMapOf<String, MutableList<EPGProgram>>()

            val results = coroutineScope {
                urls.map { url ->
                    async(Dispatchers.IO) {
                        try {
                            val localData = mutableMapOf<String, MutableList<EPGProgram>>()
                            val localDataById = mutableMapOf<String, MutableList<EPGProgram>>()
                            fetchAndParseSingleSource(context, url, force, localData, localDataById)
                            Pair(localData, localDataById)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }

            val successCount = results.count { it != null }

            results.forEach { result ->
                if (result != null) {
                    val (localData, localDataById) = result
                    localData.forEach { (k, v) ->
                        newEpgData.getOrPut(k) { mutableListOf() }.addAll(v)
                    }
                    localDataById.forEach { (k, v) ->
                        newEpgDataById.getOrPut(k) { mutableListOf() }.addAll(v)
                    }
                }
            }
            
            // Deduplicate and Sort
            newEpgData.values.forEach { progs ->
                val unique = progs.distinctBy { it.start to it.stop }
                progs.clear()
                progs.addAll(unique)
                progs.sortBy { it.start }
            }
            newEpgDataById.values.forEach { progs ->
                val unique = progs.distinctBy { it.start to it.stop }
                progs.clear()
                progs.addAll(unique)
                progs.sortBy { it.start }
            }

            synchronized(this) {
                epgData = newEpgData
                epgDataById = newEpgDataById
                fuzzyMatchCache.clear() // Reset fuzzy cache when data refresh is complete
            }

            val totalChannels = synchronized(this) { epgDataById.size }
            epgStatus = "Loaded $successCount/${urls.size} hubs ($totalChannels channels)"
            
            cleanupOldCaches(context, urls)
        }
    }

    private fun cleanupOldCaches(context: Context, currentUrls: List<String>) {
        try {
            val currentHashes = currentUrls.map { it.hashCode().toString() }.toSet()
            context.cacheDir.listFiles { file -> 
                file.name.startsWith("epg_") && file.name.endsWith(".xml.gz") 
            }?.forEach { file ->
                val hash = file.name.removePrefix("epg_").removeSuffix(".xml.gz")
                if (hash !in currentHashes) file.delete()
            }
        } catch (e: Exception) {}
    }

    private suspend fun fetchAndParseSingleSource(
        context: Context, 
        url: String, 
        force: Boolean,
        tempData: MutableMap<String, MutableList<EPGProgram>>,
        tempDataById: MutableMap<String, MutableList<EPGProgram>>
    ): Int {
        val file = getCacheFile(context, url)
        val now = Utils.getDateTimestamp() * 1000L
        val client = SecureHttpClient.client
        
        if (force || !file.exists() || (now - file.lastModified() > 12 * 3600_000L)) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(file).use { input.copyTo(it) }
                    }
                } else throw Exception("HTTP ${response.code}")
            }
        }

        if (file.exists()) {
            FileInputStream(file).use { fis ->
                val pbis = java.io.PushbackInputStream(fis, 2)
                val b1 = pbis.read(); val b2 = pbis.read()
                if (b1 != -1 && b2 != -1) { pbis.unread(b2); pbis.unread(b1) }
                val stream = if (b1 == 0x1f && b2 == 0x8b) java.util.zip.GZIPInputStream(pbis) else pbis
                return parseXML(stream, tempData, tempDataById)
            }
        }
        return 0
    }

    private fun parseXML(
        inputStream: InputStream,
        targetData: MutableMap<String, MutableList<EPGProgram>>,
        targetDataById: MutableMap<String, MutableList<EPGProgram>>
    ): Int {
        val formats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        val channelIdToNames = mutableMapOf<String, MutableSet<String>>()
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
                        "display-name" -> if (currentChannelId != null) {
                            try { readText(parser)?.let { channelIdToNames[currentChannelId!!]?.add(it) } } catch (e: Exception) {}
                        }
                        "programme" -> {
                            val cid = parser.getAttributeValue(null, "channel")?.intern()
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            val start = parseDate(startStr, formats)
                            val stop = parseDate(stopStr, formats)
                            if (cid != null && start != null && stop != null) {
                                val now = Utils.getDateTimestamp() * 1000L
                                if (stop > (now - 24 * 3600_000L)) { // Keep last 24h
                                    currentProgram = EPGProgram("", start, stop, "")
                                    currentChannelId = cid
                                } else currentProgram = null
                            }
                        }
                        "title" -> if (currentProgram != null) try { currentProgram!!.title = readText(parser)?.intern() ?: "" } catch (e: Exception) {}
                        "desc" -> if (currentProgram != null) try {
                            var d = readText(parser) ?: ""
                            if (d.length > 250) d = d.substring(0, 250) + "..."
                            currentProgram!!.description = d
                        } catch (e: Exception) {}
                    }
                }
                XmlPullParser.END_TAG -> if (name == "programme" && currentProgram != null && currentChannelId != null) {
                    if (currentProgram!!.title.isNotEmpty()) {
                        targetDataById.getOrPut(currentChannelId!!) { mutableListOf() }.add(currentProgram!!)
                        val names = channelIdToNames[currentChannelId!!] ?: setOf(currentChannelId!!)
                        for (n in names) {
                            val norm = normalizeName(n)
                            targetData.getOrPut(norm) { mutableListOf() }.add(currentProgram!!)
                        }
                    }
                    currentProgram = null; currentChannelId = null
                }
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        return channelIdToNames.size
    }

    private fun readText(parser: XmlPullParser): String? {
        var result: String? = null
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    fun getCurrentProgram(channelName: String, channelApiId: String = ""): EPGProgram? = synchronized(this) {
        if (!SP.epgEnabled) return null
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        
        if (channelApiId.isNotEmpty()) {
            epgDataById[channelApiId]?.find { now in it.start until it.stop }?.let { return it }
        }
        
        val normalized = normalizeName(channelName)
        
        // 1. Direct match
        epgData[normalized]?.find { now in it.start until it.stop }?.let { return it }
        
        // 2. Cached fuzzy match
        fuzzyMatchCache[normalized]?.let { cachedKey ->
            epgData[cachedKey]?.find { now in it.start until it.stop }?.let { return it }
        }
        
        // 3. New fuzzy match
        val fuzzyMatchKey = epgData.keys.find { key ->
            normalized.length >= 4 && (key.contains(normalized) || normalized.contains(key))
        }
        
        if (fuzzyMatchKey != null) {
            fuzzyMatchCache[normalized] = fuzzyMatchKey
            epgData[fuzzyMatchKey]?.find { now in it.start until it.stop }?.let { return it }
        }

        return null
    }

    fun getUpcomingProgram(channelName: String, channelApiId: String = ""): EPGProgram? = synchronized(this) {
        if (!SP.epgEnabled) return null
        val now = (Utils.getDateTimestamp() * 1000L) - (SP.epgShift * 3600_000L)
        if (channelApiId.isNotEmpty()) epgDataById[channelApiId]?.find { it.start > now }?.let { return it }
        
        val normalized = normalizeName(channelName)
        val matchKey = fuzzyMatchCache[normalized] ?: normalized
        return epgData[matchKey]?.find { it.start > now }
    }

    fun getProgramsForChannel(channelName: String, channelApiId: String = ""): List<EPGProgram> = synchronized(this) {
        if (channelApiId.isNotEmpty()) epgDataById[channelApiId]?.let { return it.toList() }
        val normalized = normalizeName(channelName)
        val matchKey = fuzzyMatchCache[normalized] ?: normalized
        return epgData[matchKey]?.toList() ?: emptyList()
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
        return synchronized(normalizedCache) {
            normalizedCache.getOrPut(name) {
                name.lowercase(Locale.US)
                    .replace(REGEX_BRACKETS, "")
                    .replace(REGEX_REGION, "")
                    .replace("hd", "")
                    .replace("sd", "")
                    .replace("tv", "")
                    .replace("channel", "")
                    .replace("india", "")
                    .replace("plus", "")
                    .replace(REGEX_PREFIX_D, "")
                    .replace(REGEX_PREFIX_DD, "")
                    .replace(REGEX_NON_ALPHANUM, "")
                    .trim()
                    .intern()
            }
        }
    }

    fun clear() {
        synchronized(this) {
            epgData.clear()
            epgDataById.clear()
            synchronized(normalizedCache) { normalizedCache.clear() }
            fuzzyMatchCache.clear()
        }
        epgStatus = "Cleared"
    }
}
