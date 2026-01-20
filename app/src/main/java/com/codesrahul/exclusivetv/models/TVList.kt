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
import com.codesrahul.exclusivetv.SecurityUtil
import com.codesrahul.exclusivetv.StringObfuscator

object TVList {
    fun clear() {
        list = emptyList()
        listModel = emptyList()
        // Create empty structures to update valid state
        val emptyGroups = listOf(
            TVListModel("My Collection", 0),
            TVListModel("All channels", 1)
        )
        groupModel.setTVListModelList(emptyGroups)
        groupModel.setChange()
        _position.postValue(-1)
        
        try {
            if (::appDirectory.isInitialized) {
                File(appDirectory, FILE_NAME).delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete secret file", e)
        }
    }
    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    val DEFAULT_CONFIG_URL: String
        get() = StringObfuscator.getConfigUrl()
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

            // Early version check to prevent data fetch if update available
            try {
                Log.i(TAG, "Performing early update check...")
                val release = com.codesrahul.exclusivetv.requests.ReleaseRequest().getRelease()
                if (release != null) {
                    SecurityUtil.remoteRelease = release
                    if (release.version_code!! > currentVersion) {
                        SecurityUtil.isAppOutdated = true
                        Log.w(TAG, "Early update check: App is outdated (Remote: ${release.version_code}, Local: $currentVersion). Blocking load.")
                        clear() // Delete cached channels.txt to make app "useless"
                    } else {
                        Log.i(TAG, "Early update check: App is up to date.")
                    }
                } else {
                    Log.i(TAG, "Early update check: Check failed (No response).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Early update check error", e)
            }

            val cfg = SP.config
            if (SP.configAutoLoad && !cfg.isNullOrEmpty()) {
                if (!SecurityUtil.isAppOutdated) {
                    update(context, cfg)
                } else {
                    Log.i(TAG, "Skipping channel load - update required")
                }
            }
        }
    }



    private val _importStatus = MutableLiveData<String>()
    val importStatus: LiveData<String>
        get() = _importStatus

    fun update(ctx: Context, silent: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isUpdating = true
                
                // Get all URLs to fetch
                val urls = SP.playlistUrls.toMutableSet()
                
                // Hide default API if custom sources exist
                if (urls.any { it != DEFAULT_CONFIG_URL }) {
                    urls.remove(DEFAULT_CONFIG_URL)
                } else {
                    if (urls.isEmpty()) {
                         urls.add(DEFAULT_CONFIG_URL)
                         SP.addPlaylistUrl(DEFAULT_CONFIG_URL)
                    } else if (!urls.contains(DEFAULT_CONFIG_URL)) {
                         urls.add(DEFAULT_CONFIG_URL)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (size() == 0 && !silent) {
                         _importProgress.value = 5
                         _importStatus.value = "Initializing..."
                    } else if (!silent) {
                         _importProgress.value = 0
                         _importStatus.value = "Checking for updates..."
                    }
                }
                
                val client = SecureHttpClient.client
                val allChannels = mutableListOf<TV>()
                var successCount = 0
                
                // Fetch all playlists concurrently
                val totalSources = urls.size
                var completedSources = 0

                val deferredResults = urls.mapIndexed { index, url ->
                    async {
                        try {
                           withContext(Dispatchers.Main) {
                               if (!silent) _importStatus.value = "Fetching source ${index + 1} of $totalSources..."
                           }
                           Log.i(TAG, "Fetching playlist: $url")
                           val request = Request.Builder().url(url).get().build()
                           
                           client.newCall(request).execute().use { response ->
                               if (response.isSuccessful) {
                                   val str = response.body()?.string() ?: ""
                                   if (str.isNotBlank()) {
                                       withContext(Dispatchers.Main) {
                                            if (!silent) _importStatus.value = "Parsing data..."
                                       }
                                       parseUniversal(str)
                                   } else {
                                       emptyList<TV>()
                                   }
                               } else {
                                   Log.e(TAG, "Failed to fetch $url: ${response.code()}")
                                   emptyList<TV>()
                               }
                           }
                        } catch (e: Exception) {
                           Log.e(TAG, "Error fetching $url", e)
                           emptyList<TV>()
                        } finally {
                            completedSources++
                            val progress = ((completedSources.toFloat() / totalSources) * 80).toInt() + 10
                            withContext(Dispatchers.Main) {
                                if (!silent) _importProgress.value = progress
                            }
                        }
                    }
                }
                
                // Await all
                val results = deferredResults.map { it.await() }
                results.forEach { 
                    if (it.isNotEmpty()) {
                        allChannels.addAll(it)
                        successCount++
                    }
                }
                
                if (successCount > 0) {
                     withContext(Dispatchers.Main) {
                          if (!silent) _importStatus.value = "Finalizing..."
                     }
                     
                     // UNROLL: If one channel contains multiple sources, show them all in the list
                     val finalChannels = mutableListOf<TV>()
                     allChannels.forEach { tv ->
                         if (tv.uris.size > 1) {
                             tv.uris.forEachIndexed { index, uri ->
                                 finalChannels.add(tv.copy(
                                     id = tv.id * 100 + index, // Generate a unique sub-id
                                     title = "${tv.title} (S${index + 1})",
                                     uris = listOf(uri)
                                 ))
                             }
                         } else {
                             finalChannels.add(tv)
                         }
                     }

                     val gson = com.google.gson.Gson()
                     val jsonStr = gson.toJson(finalChannels)
                     
                     val file = File(ctx.filesDir, FILE_NAME)
                     file.writeText(jsonStr) // Save formatted JSON
                     
                     // Update memory
                     list = finalChannels
                     
                     withContext(Dispatchers.Main) {
                         refreshModels(MyTVApplication.getInstance())
                         if (!silent) "Channels updated from $successCount sources".showToast()
                         _importProgress.value = 100
                         _importStatus.value = "Complete"
                         
                         if (SP.epgEnabled) {
                             EPGManager.init(ctx)
                             CoroutineScope(Dispatchers.IO).launch {
                                 withContext(Dispatchers.Main) { _importStatus.value = "Updating Guide..." } 
                                 EPGManager.fetchEPG(force = false)
                                 withContext(Dispatchers.Main) {
                                     listModel.forEach { it.updateEPG() }
                                 }
                             }
                         }
                     }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!silent) "Failed to update channels".showToast()
                        _importProgress.value = 0
                        _importStatus.value = "Failed"
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
                withContext(Dispatchers.Main) {
                     if (!silent) "Update error: ${e.message}".showToast()
                     _importProgress.value = 0
                     _importStatus.value = "Error"
                }
            } finally {
                isUpdating = false
            }
        }
    }
    
    // Helper that returns List<TV> instead of setting global 'list'
    private suspend fun parseContentHelper(str: String): List<TV> = withContext(Dispatchers.Default) {
        try {
            val parsed = parseUniversal(str)
            if (parsed.isNotEmpty()) {
                expandNestedPlaylists(parsed)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseContentHelper", e)
            emptyList()
        }
    }

    // Kept for backward compatibility/single file logic if needed, but updated to use helper
    suspend fun str2List(str: String): Boolean {
         val result = parseContentHelper(str)
         if (result.isNotEmpty()) {
             list = result
             refreshModels(MyTVApplication.getInstance())
             return true
         }
         return false
    }
    
    // Helper to call update with single URL (legacy support)
    fun update(ctx: Context, serverUrl: String, silent: Boolean = false) {
        // If specific URL passed, add it to list and update all?
        // Or just update that one?
        // Let's assume we add it to our list if valid
        if (serverUrl.isNotEmpty()) {
            SP.addPlaylistUrl(serverUrl)
        }
        update(ctx, silent)
    }

    fun parseUri(context: Context, uri: Uri) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { 
                it.bufferedReader().readText() 
            }
            
            if (!content.isNullOrBlank()) {
                val parsed = parseUniversal(content)
                if (parsed.isNotEmpty()) {
                    list = parsed
                    SP.addPlaylistUrl(uri.toString()) // Optional: Save URI? Maybe not readable later.
                    refreshModels(context)
                    Toast.makeText(context, "Loaded ${parsed.size} channels from file", Toast.LENGTH_SHORT).show()
                } else {
                     Toast.makeText(context, "No channels found in file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ParseUri", e)
            Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
        }
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

        // LOGGING: Aid in debugging large payloads
        Log.d(TAG, "Parsing universal content (Length: ${decryptedContent.length})")
        Log.d(TAG, "Content Sample: ${decryptedContent.take(500)}")

        // 1. JSON Detection (PRIORITY)
        // We try JSON first because our main API returns a JSON array that might contain M3U strings.
        // If we check for M3U first, the presence of "EXTHTTP" in the JSON will trigger M3U parsing prematurely.
        val startIndex = string.indexOfFirst { it == '[' || it == '{' }
        if (startIndex != -1) {
             try {
                val jsonString = string.substring(startIndex)
                val element = com.google.gson.JsonParser.parseString(jsonString)
                val allChannels = mutableListOf<TV>()
                val gson = com.google.gson.GsonBuilder().setLenient().create()

                fun processElement(item: com.google.gson.JsonElement) {
                    when {
                        item.isJsonObject -> {
                            val obj = item.asJsonObject
                            // A. Try Standard TV mapping
                            try {
                                val tv = gson.fromJson(obj, TV::class.java)
                                if (tv != null && !tv.uris.isNullOrEmpty()) {
                                    allChannels.add(tv)
                                } else {
                                    // B. Fallback to Generic Parser for this object
                                    val genericTv = GenericJsonParser.parseSingleObject(obj, allChannels.size)
                                    if (genericTv != null) allChannels.add(genericTv)
                                }
                            } catch (e: Exception) {
                                // C. Fallback for object-wrapped lists (like {"channels": [...]})
                                val subList = GenericJsonParser.parse(obj.toString())
                                if (subList.isNotEmpty()) allChannels.addAll(subList)
                            }
                        }
                        item.isJsonPrimitive && item.asJsonPrimitive.isString -> {
                            val m3uContent = item.asString
                            if (m3uContent.contains("#EXTINF") || m3uContent.contains("http") || m3uContent.contains("EXTHTTP")) {
                                allChannels.addAll(parseUniversal(m3uContent))
                            }
                        }
                    }
                }

                if (element.isJsonArray) {
                    element.asJsonArray.forEach { processElement(it) }
                } else if (element.isJsonObject) {
                    processElement(element)
                }

                if (allChannels.isNotEmpty()) {
                    Log.i(TAG, "Parsed ${allChannels.size} channels from JSON")
                    return allChannels
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a valid JSON or parsing failed, falling back to M3U")
            }
        }

        // 2. PLS Playlist
        if (decryptedContent.contains("[playlist]", ignoreCase = true)) {
             val plsList = PlsParser.parse(decryptedContent)
             if (plsList.isNotEmpty()) return plsList
        }

        // 3. M3U / M3U8 / Kodi Playlist
        if (decryptedContent.contains("#EXTINF") || decryptedContent.contains("#EXTM3U") || 
            decryptedContent.contains("EXTHTTP") || decryptedContent.contains("#KODIPROP")) {
            
            // Prefer KodiParser if KODIPROP present
            if (decryptedContent.contains("#KODIPROP")) {
                try {
                    val kodiList = KodiParser.parse(decryptedContent)
                    if (kodiList.isNotEmpty()) {
                        Log.i(TAG, "Parsed Kodi M3U: ${kodiList.size}")
                        return kodiList
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kodi parse error", e)
                }
            }

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

        // 4. Fallback: Try Simple List Parser (for raw URL lists)
        // Only try if content has http links and wasn't parsed by others
        if (string.contains("http://") || string.contains("https://")) {
            val simpleList = SimpleListParser.parse(string)
            if (simpleList.isNotEmpty()) {
                return simpleList
            }
        }

        return emptyList()
    }

    private suspend fun expandNestedPlaylists(originalList: List<TV>, depth: Int = 0): List<TV> = withContext(Dispatchers.IO) {
        // Prevent infinite recursion or excessive depth
        if (depth > 3) {
            Log.w(TAG, "Max playlist expansion depth reached, skipping nested content")
            return@withContext originalList
        }

        val client = SecureHttpClient.client
        // Limit concurrency to avoid overwhelming servers (max 5 parallel fetches)
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        
        // 1. Map each item to a Deferred result (or immediate value)
        val deferredResults = originalList.map { tv ->
            val url = tv.uris.firstOrNull() ?: ""
            
            // Broader check for playlists
            // If it's NOT a clearly identified stream extension, treat it as a potential playlist
            // specially if coming from a dynamic API
            val isStream = url.contains(".m3u8", ignoreCase = true) || 
                           url.contains(".mpd", ignoreCase = true) ||
                           url.contains(".ts", ignoreCase = true) ||
                           url.contains(".mkv", ignoreCase = true) ||
                           url.contains(".mp4", ignoreCase = true) ||
                           url.startsWith("rtsp://", ignoreCase = true) ||
                           url.startsWith("rtmp://", ignoreCase = true) ||
                           url.contains("/manifest", ignoreCase = true) ||
                           url.contains("playlist.m3u8", ignoreCase = true)

            // If it has children already, it's a group, don't expand
            if (tv.child.isNotEmpty()) {
                 async { listOf(tv) }
            }
            // If it's a candidate, fetch asynchronously
            else if (!isStream && url.startsWith("http")) {
                async {
                    semaphore.acquire()
                    try {
                        Log.i(TAG, "Checking nested content: ${tv.name}")
                        val requestBuilder = Request.Builder().url(url).get()
                        
                        // FIX: Do NOT propagate parent headers to the nested playlist fetch.
                        // We want to fetch the M3U using the standard client (Chrome UA), just like Source Config.
                        // Propagating API headers to a GitHub/External URL is incorrect.
                        
                        val request = requestBuilder.build()
                         
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
                                    }
                                    // RECURSIVE: Expand if these items are also playlists
                                    expandNestedPlaylists(subChannels, depth + 1)
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
                    } finally {
                        semaphore.release()
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
            
            val hiddenCategories = OrderPreferenceManager.getHiddenCategories()
            
            for (categoryName in sortedCategories) {
                if (categoryName in hiddenCategories) continue // Skip hidden groups

                val originalCategoryName = categoryName
                val displayCategoryName = OrderPreferenceManager.getCategoryDisplayName(originalCategoryName)
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
                         val tvModel = oldIdToModel[tv.uris.firstOrNull() ?: ""]?.apply { update(tv) } ?: TVModel(tv)
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
            
            val client = SecureHttpClient.client
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
        if (position < 0 || position >= size()) {
            Log.w(TAG, "setPosition invalid: $position (size: ${size()})")
            return false
        }

        // 1. Get Model FIRST to ensure validity
        val tvModel = getTVModel(position) ?: return false
        
        // 2. Update LiveData (Trigger Observers)
        if (_position.value != position) {
             // Main Thread safety check for LiveData
             if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                 _position.value = position
             } else {
                 _position.postValue(position)
             }
        }

        // 3. Update State & Persistence
        groupModel.setPosition(tvModel.groupIndex)
        SP.positionGroup = tvModel.groupIndex
        SP.position = position
        
        if (tvModel.tv.uris.isNotEmpty()) {
            SP.lastChannelUrl = tvModel.tv.uris[0]
            SP.lastChannelName = tvModel.tv.name
        }
        
        return true
    }

    fun size(): Int {
        return listModel.size
    }

    fun restorePosition(): Int {
        val savedUrl = SP.lastChannelUrl
        val savedName = SP.lastChannelName
        val savedPos = SP.position
        
        if (savedUrl.isNotEmpty()) {
            // Priority 1: Strict Match (URL + Name)
            if (savedName.isNotEmpty()) {
                 val strictIndex = listModel.indexOfFirst { 
                     it.tv.uris.isNotEmpty() && it.tv.uris[0] == savedUrl && it.tv.name == savedName
                 }
                 if (strictIndex != -1) {
                     return strictIndex
                 }
            }

            // Priority 2: URL Match
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
