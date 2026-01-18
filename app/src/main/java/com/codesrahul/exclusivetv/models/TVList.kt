package com.codesrahul.exclusivetv.models

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonSyntaxException
import com.codesrahul.exclusivetv.R
import com.codesrahul.exclusivetv.SP
import com.codesrahul.exclusivetv.EPGManager
import com.codesrahul.exclusivetv.showToast
import com.codesrahul.exclusivetv.MyTVApplication
import com.codesrahul.exclusivetv.SecureHttpClient
import com.codesrahul.exclusivetv.OrderPreferenceManager
import okhttp3.Request
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import java.io.File
import com.codesrahul.exclusivetv.models.JioTVChannel

object TVList {
    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    const val DEFAULT_CONFIG_URL = "https://exclusive-tv-app-api.vercel.app/"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()
    
    private var isUpdating = false

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int>
        get() = _importProgress

    fun findChannelByName(query: String): TVModel? {
        val q = query.lowercase().trim()
        // 1. Exact match (title)
        listModel.find { it.tv.title.equals(q, ignoreCase = true) }?.let { return it }
        
        // 2. Contains match (title)
        listModel.find { it.tv.title.lowercase().contains(q) }?.let { return it }
        
        // 3. Normalized match (remove spaces/special chars)
        val qNorm = q.replace(Regex("[^a-z0-9]"), "")
        listModel.find { 
            it.tv.title.lowercase().replace(Regex("[^a-z0-9]"), "").contains(qNorm) 
        }?.let { return it }
        
        return null
    }

    fun init(context: Context) {
        _position.value = 0
        _importProgress.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", 0))
        groupModel.addTVListModel(TVListModel("All channels", 1))

        appDirectory = context.filesDir
        


        CoroutineScope(Dispatchers.IO).launch {
            val file = File(appDirectory, FILE_NAME)
            val str = if (file.exists()) {
                Log.i(TAG, "read $file")
                file.readText()
            } else {
                Log.i(TAG, "read resource")
                context.resources.openRawResource(R.raw.channels).bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }

            try {
                str2List(str)
            } catch (e: Exception) {
                Log.e(TAG, "error $e")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read the channel, please set it in the menu", Toast.LENGTH_LONG).show()
                }
            }

            if (SP.config.isNullOrEmpty()) {
                SP.config = DEFAULT_CONFIG_URL
            }

            // Check for App Update or Stale Config
            val currentVersion = com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE
            // Force update to new API if different
            if (SP.config != DEFAULT_CONFIG_URL) {
                Log.i(TAG, "Config mismatch. Resetting to default.")
                SP.config = DEFAULT_CONFIG_URL
            }
            // Update last version
            if (currentVersion != SP.lastVersion) {
                 SP.lastVersion = currentVersion
                 File(appDirectory, FILE_NAME).delete() // Clear old cache
            }

            if (SP.configAutoLoad && !SP.config.isNullOrEmpty()) {
                val cfg = SP.config
                if (cfg != null) {
                    // Check if update is required before loading channels
                    val mainActivityClass = try {
                        Class.forName("com.codesrahul.exclusivetv.MainActivity")
                    } catch (e: Exception) {
                        null
                    }
                    val isUpdateRequired = mainActivityClass?.let {
                        it.getDeclaredField("isUpdateRequired").apply { isAccessible = true }.getBoolean(null)
                    } ?: false
                    
                    if (!isUpdateRequired) {
                        update(context, cfg)
                    } else {
                        Log.i(TAG, "Skipping channel load - update required")
                    }
                }
            }
        }
    }

    private val unsafeClient: okhttp3.OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .method(original.method(), original.body())
                        .build()
                    chain.proceed(request)
                }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun update(serverUrl: String, silent: Boolean = false) {
        update(MyTVApplication.getInstance(), serverUrl, silent)
    }

    fun update(ctx: Context, serverUrl: String, silent: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isUpdating = true
                withContext(Dispatchers.Main) {
                    if (size() == 0 && !silent) {
                         _importProgress.value = 5
                    } else if (!silent) {
                         _importProgress.value = 0
                    }
                }
                
                // Use HttpURLConnection as fallback
                val targetUrl = serverUrl
                
                val urlObj = java.net.URL(targetUrl)
                val conn = urlObj.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                conn.setRequestProperty("version_code", com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE.toString())
                
                val responseCode = conn.responseCode
                
                if (responseCode == 200) {
                    // Update progress to 60 (Server responded)
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            _importProgress.value = 60
                        }
                    }

                    val file = File(ctx.filesDir, FILE_NAME)
                    if (!file.exists()) {
                        file.createNewFile()
                    }

                    val str = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.i(TAG, "Body length: ${str.length}")

                    // Process JSON in background
                    val success = try {
                        str2List(str)
                    } catch (e: Exception) {
                        Log.e(TAG, "Critical error in str2List", e)
                        false
                    }

                    withContext(Dispatchers.Main) {
                        try {
                             if (success) {
                                file.writeText(str)
                                SP.config = serverUrl
                                if (!silent) "Channels updated".showToast()
                                
                                 // checkChannelsInBackground()
                                 
                                 // Fetch EPG if enabled
                                 if (SP.epgEnabled) {
                                     Log.i(TAG, "Fetching EPG...")
                                     EPGManager.init(ctx)
                                     CoroutineScope(Dispatchers.IO).launch {
                                         EPGManager.fetchEPG(force = true)
                                         withContext(Dispatchers.Main) {
                                             listModel.forEach { it.updateEPG() }
                                         }
                                     }
                                 }

                                // Update progress to 100 (Done)
                                if (!silent) {
                                    _importProgress.value = 100
                                }
                            } else {
                                if (!silent) {
                                    "Channel import error: Invalid content".showToast()
                                    _importProgress.value = 0 // Reset/Fail
                                }
                            }
                        } catch (e: Exception) {
                             Log.e(TAG, "Parsing error", e)
                             if (!silent) {
                                 "Channel import error: ${e.message}".showToast()
                                 _importProgress.value = 0 // Reset/Fail
                             }
                        }
                    }
                } else {
                    Log.e("", "request status $responseCode")
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            "Channel status error: $responseCode".showToast()
                        }
                    }
                }
            } catch (e: JsonSyntaxException) {
                Log.e("JSON Parse Error", e.toString())
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        "Channel format error".showToast()
                    }
                }
            } catch (e: NullPointerException) {
                Log.e("Null Pointer Error", e.toString())
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        "Unable to read channel".showToast()
                    }
                }
            } catch (e: Exception) {
                Log.e("", "request error $e")
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        "Channel request error: ${e.message}".showToast()
                    }
                }
            } finally {
                isUpdating = false
            }
        }
    }

    fun parseUri(uri: Uri) {
        if (uri.scheme == "file") {
            val file = uri.toFile()
            Log.i(TAG, "file $file")
            val str = if (file.exists()) {
                Log.i(TAG, "read $file")
                file.readText()
            } else {
                "File does not exist".showToast(Toast.LENGTH_LONG)
                return
            }

            try {
                CoroutineScope(Dispatchers.IO).launch {
                    val success = str2List(str)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            SP.config = uri.toString()
                            "Channels updated".showToast(Toast.LENGTH_LONG)
                            // checkChannelsInBackground()
                        } else {
                            "Channel import failed".showToast(Toast.LENGTH_LONG)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("", "error $e")
                file.deleteOnExit()
                "Failed to read channel".showToast(Toast.LENGTH_LONG)
            }
        } else {
            update(MyTVApplication.getInstance(), uri.toString())
        }
    }

    suspend fun str2List(str: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val parsed = parseUniversal(str)
            if (parsed.isNotEmpty()) {
                list = parsed
            } else {
                Log.e(TAG, "No channels found in data")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in str2List", e)
            return@withContext false
        }

        // Expand Nested Playlists (Recursive Import)
        try {
            list = expandNestedPlaylists(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding playlists", e)
        }

        refreshModels(MyTVApplication.getInstance())
        return@withContext true
    }

    private fun parseUniversal(content: String): List<TV> {
        var string = content.trim()
        val g = Gua()
        if (g.verify(string)) {
            Log.i(TAG, "Content verified with Gua")
            string = g.decode(string)
        }
        
        if (string.isBlank()) return emptyList()
        val decryptedContent = string

        // 1. PLS Playlist
        if (decryptedContent.contains("[playlist]", ignoreCase = true)) {
             val plsList = PlsParser.parse(decryptedContent)
             if (plsList.isNotEmpty()) return plsList
        }

        // 2. M3U / M3U8 Playlist (Check first if explicit M3U header, or fall through)
        // We check M3U *before* generic JSON if it looks strongly like M3U, but *after* JSON if ambiguous?
        // Actually, JSON starts with { or [, M3U with #. Easy distinction.
        
        val startIndex = string.indexOfFirst { it == '[' || it == '{' }
        val isJsonCandidate = startIndex != -1 && !string.trim().startsWith("#")

        if (isJsonCandidate) {
             try {
                val jsonString = string.substring(startIndex)
                val gson = com.google.gson.GsonBuilder().setLenient().create()
                
                // A. Try Standard TV List
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                    val parsedList = gson.fromJson<List<TV>>(jsonString, type)
                    if (!parsedList.isNullOrEmpty() && !parsedList[0].uris.isNullOrEmpty()) {
                        Log.i(TAG, "Parsed Standard JSON: ${parsedList.size}")
                        return parsedList
                    }
                } catch (e: Exception) { /* Continue */ }

                // B. Try JioTV JSON
                try {
                    val jioType = object : com.google.gson.reflect.TypeToken<List<JioTVChannel>>() {}.type
                    val jioList = gson.fromJson<List<JioTVChannel>>(jsonString, jioType)
                    if (!jioList.isNullOrEmpty() && !jioList[0].mpdUrl.isNullOrEmpty()) {
                        Log.i(TAG, "Parsed JioTV JSON: ${jioList.size}")
                        return jioList.map { jio ->
                            TV(
                                apiId = jio.id ?: "",
                                name = jio.name ?: "",
                                logo = jio.logo ?: "",
                                group = jio.group ?: "",
                                uris = if (jio.mpdUrl != null) listOf(jio.mpdUrl) else emptyList(),
                                drmLicenseUrl = jio.licenseUrl,
                                type = if (jio.type == "dash") Type.STREAM else Type.WEB,
                                headers = mutableMapOf<String, String>().apply {
                                    jio.headers?.let { putAll(it) }
                                    jio.userAgent?.let { put("User-Agent", it) }
                                },
                                child = emptyList()
                            )
                        }
                    }
                } catch (e: Exception) { /* Continue */ }

                // C. Try Generic Heuristic JSON (Xtream Codes / Other)
                val genericList = GenericJsonParser.parse(jsonString)
                if (genericList.isNotEmpty()) {
                    Log.i(TAG, "Parsed Generic JSON: ${genericList.size}")
                    return genericList
                }

            } catch (e: Exception) {
                Log.e(TAG, "JSON parsing failed", e)
            }
        }

        // 3. Fallback to M3U
        // Check for common M3U indicators
        if (decryptedContent.contains("#EXTINF") || decryptedContent.contains("#EXTM3U") || 
            decryptedContent.contains("EXTHTTP") || decryptedContent.contains("#KODIPROP")) {
            try {
                val m3uList = M3UParser.parse(decryptedContent)
                if (m3uList.isNotEmpty()) {
                    Log.i(TAG, "Parsed M3U: ${m3uList.size}")
                    return m3uList
                }
            } catch (e: Exception) {
                Log.e(TAG, "M3U parse error", e)
            }
        }

        return emptyList()
    }

    private suspend fun expandNestedPlaylists(originalList: List<TV>): List<TV> = withContext(Dispatchers.IO) {
        val client = unsafeClient
        
        // 1. Map each item to a Deferred result (or immediate value)
        val deferredResults = originalList.map { tv ->
            val url = tv.uris.firstOrNull() ?: ""
            
            // Broader check for playlists
            val isPlaylistCandidate = url.contains(".m3u", ignoreCase = true) || 
                                      url.contains(".php", ignoreCase = true) ||
                                      url.contains(".txt", ignoreCase = true) ||
                                      url.contains("type=m3u", ignoreCase = true)
            
            // Exclude explicit stream types/extensions
            val isStream = url.contains(".m3u8", ignoreCase = true) || 
                           url.contains(".mpd", ignoreCase = true)

            // If it's a candidate, fetch asynchronously
            if (isPlaylistCandidate && !isStream) {
                // Return a Deferred<List<TV>>? No, allow mixed types.
                // We use async to fetch.
                async {
                    Log.i(TAG, "Checking nested content: ${tv.name}")
                    try {
                        val request = Request.Builder().url(url).get().build()
                         
                        // Execute blocking call inside IO async block
                        client.newCall(request).execute().use { response ->
                            val content = response.body()?.string()
                            if (response.isSuccessful && !content.isNullOrBlank()) {
                                // Use UNIVERSAL parser
                                val subChannels = parseUniversal(content)
                                if (subChannels.isNotEmpty()) {
                                    Log.i(TAG, "Expanded ${tv.name}: ${subChannels.size} channels")
                                    subChannels.forEach { child ->
                                        if (child.group.isBlank()) child.group = tv.group
                                        val mergedHeaders = mutableMapOf<String, String>()
                                        tv.headers?.let { mergedHeaders.putAll(it) }
                                        child.headers?.let { mergedHeaders.putAll(it) }
                                        child.headers = mergedHeaders
                                    }
                                    subChannels
                                } else {
                                    listOf(tv) // Keep original if empty/parsing failed
                                }
                            } else {
                                listOf(tv)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching nested playlist ${tv.name}", e)
                        listOf(tv)
                    }
                }
            } else {
                // If not a candidate, wrap in immediate result
                async { listOf(tv) }
            }
        }

        // 2. Await all results and flatten
        deferredResults.map { it.await() }.flatten()
    }

    fun refreshModels(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!::list.isInitialized || list.isEmpty()) {
                Log.w(TAG, "Cannot refresh models: list not initialized or empty")
                return@launch
            }

            // Preparation Phase (Background)
            val map: MutableMap<String, MutableList<TV>> = mutableMapOf()
            for (v in list) {
                if (v.group !in map) {
                    map[v.group] = mutableListOf()
                }
                map[v.group]?.add(v)
            }

            val categoryOrder = OrderPreferenceManager.getCategoryOrder()
            val categoryRenames = OrderPreferenceManager.getCategoryRenames()
            
            val sortedCategories = if (categoryOrder != null && categoryOrder.isNotEmpty()) {
                val orderedCategories = mutableListOf<String>()
                val unorderedCategories = map.keys.filter { it !in categoryOrder }.toMutableList()
                for (catName in categoryOrder) {
                    if (catName in map) {
                        orderedCategories.add(catName)
                    }
                }
                orderedCategories.addAll(unorderedCategories)
                orderedCategories
            } else {
                map.keys.toList()
            }

            val preparedGroups = mutableListOf<Triple<String, Int, List<TV>>>()
            var groupIndex = 2
            
            for (categoryName in sortedCategories) {
                val originalCategoryName = categoryName
                val displayCategoryName = categoryRenames[originalCategoryName] ?: originalCategoryName
                val channels = map[originalCategoryName] ?: continue
                
                val channelOrder = OrderPreferenceManager.getChannelOrder(originalCategoryName)
                val channelRenames = OrderPreferenceManager.getChannelRenames()
                
                val sortedChannels = if (channelOrder != null && channelOrder.isNotEmpty()) {
                    val urlToModel = channels.associateBy { it.uris.firstOrNull() ?: "" }
                    val orderedChannels = mutableListOf<TV>()
                    val unorderedChannels = channels.filter { 
                        it.uris.firstOrNull()?.let { url -> url !in channelOrder } ?: true 
                    }.toMutableList()
                    
                    for (url in channelOrder) {
                        urlToModel[url]?.let { orderedChannels.add(it) }
                    }
                    orderedChannels.addAll(unorderedChannels)
                    orderedChannels
                } else {
                    channels
                }
                
                for (tv in sortedChannels) {
                     val channelUrl = tv.uris.firstOrNull() ?: ""
                     val renamedTitle = channelRenames[channelUrl]
                     if (renamedTitle != null) {
                         tv.title = renamedTitle
                     }
                }
                
                preparedGroups.add(Triple(displayCategoryName, groupIndex, sortedChannels))
                groupIndex++
            }

            // Update Phase (Main Thread)
            withContext(Dispatchers.Main) {
                // Optimization: If current groupModel size matches and names match, 
                // we might avoid a full clear, but clear() is safer for now due to 
                // complex list indexing. However, we ensure listModel is updated atomically.
                
                val listModelSnapshot = listModel // Keep old for reference
                val listModelNew: MutableList<TVModel> = mutableListOf()
                val oldIdToModel = listModelSnapshot.associateBy { it.tv.uris.firstOrNull() ?: "" }
                
                var id = 0
                val newGroups = mutableListOf<TVListModel>()
                
                // Keep "Special" groups logic from clear() but more explicit
                newGroups.add(groupModel.getTVListModel(0) ?: TVListModel("My Collection", 0))
                newGroups.add(groupModel.getTVListModel(1) ?: TVListModel("All channels", 1))

                for ((name, idx, channels) in preparedGroups) {
                    val tvListModel = TVListModel(name, idx)
                    val groupChannels = mutableListOf<TVModel>()

                    for (tv in channels) {
                         tv.id = id
                         // Reuse existing TVModel if URL match to preserve observers if possible
                         // (Though TVModel usually gets recreated on full refresh)
                         val tvModel = TVModel(tv)
                         tvModel.groupIndex = idx
                         tvModel.listIndex = groupChannels.size
                         
                         groupChannels.add(tvModel)
                         listModelNew.add(tvModel)
                         id++
                    }

                    tvListModel.setTVListModel(groupChannels)
                    newGroups.add(tvListModel)
                }

                listModel = listModelNew
                groupModel.setTVListModelList(newGroups)
                
                // All channels
                groupModel.getTVListModel(1)?.setTVListModel(listModel)

                Log.i(TAG, "groupModel refreshed: ${groupModel.size()} groups")
                groupModel.setChange()
                
                if (SP.epgEnabled) {
                    EPGManager.init(ctx)
                    CoroutineScope(Dispatchers.IO).launch {
                        EPGManager.fetchEPG(force = false) // Don't force every refresh
                        withContext(Dispatchers.Main) {
                            listModel.forEach { it.updateEPG() }
                        }
                    }
                }
            }
        }
    }

    private fun checkChannelsInBackground() {
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            if (!::list.isInitialized || list.isEmpty()) return@launch

            val initialSize = list.size
            Log.i(TAG, "Starting background channel check. Total: $initialSize")

            val validList = mutableListOf<TV>()
            var removedCount = 0

            val currentList = list.toList()

            for (tv in currentList) {
                var isAlive = false
                if (tv.uris.isEmpty()) {
                    isAlive = false 
                } else {
                    for (uri in tv.uris) {
                        if (checkLink(uri)) {
                            isAlive = true
                            break 
                        }
                    }
                }

                if (isAlive) {
                    validList.add(tv)
                } else {
                    removedCount++
                    Log.i(TAG, "Removing dead channel: ${tv.name}")
                }
            }

            if (removedCount > 0) {
                list = validList
                withContext(Dispatchers.Main) {
                    refreshModels(MyTVApplication.getInstance())
                    // Fetch EPG if enabled
                    if (SP.epgEnabled) {
                        EPGManager.fetchEPG()
                        listModel.forEach { it.updateEPG() }
                    }
                    "$removedCount not working channels removed".showToast(Toast.LENGTH_LONG)
                }
            } else {
                Log.i(TAG, "No dead channels found")
            }
        }
    }

    private fun checkLink(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head() // Try HEAD first
                .build()
            
            val client = unsafeClient
            var response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.close()
                return true
            }
            response.close()

            // If HEAD fails (e.g. 405), try GET
             val getRequest = Request.Builder()
                .url(url)
                .get()
                .build()
            response = client.newCall(getRequest).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            // Log.d(TAG, "Link check failed for $url: ${e.message}")
            false
        }
    }

    fun getTVModel(): TVModel? {
        return _position.value?.let { getTVModel(it) }
    }

    fun getTVModel(idx: Int): TVModel? {
        if (idx < 0 || idx >= listModel.size) {
            return null
        }
        return listModel[idx]
    }

    fun setPosition(position: Int): Boolean {
        Log.i(TAG, "setPosition $position/${size()}")
        if (position < 0 || position >= size()) {
            return false
        }

        if (_position.value != position) {
            _position.value = position
        }

        val tvModel = getTVModel(position) ?: return false

        groupModel.setPosition(tvModel.groupIndex)

        SP.positionGroup = tvModel.groupIndex
        SP.position = position
        
        // Save stable identifier (URL)
        if (tvModel.tv.uris.isNotEmpty()) {
            SP.lastChannelUrl = tvModel.tv.uris[0]
        }
        
        return true
    }

    fun size(): Int {
        return listModel.size
    }

    fun restorePosition(): Int {
        val savedUrl = SP.lastChannelUrl
        val savedPos = SP.position
        
        if (savedUrl.isNotEmpty()) {
            // Find index by URL
            val index = listModel.indexOfFirst { 
                it.tv.uris.isNotEmpty() && it.tv.uris[0] == savedUrl 
            }
            if (index != -1) {
                return index
            }
        }
        
        // Fallback to saved position if valid
        if (savedPos >= 0 && savedPos < listModel.size) {
            return savedPos
        }
        
        return 0
    }
}
