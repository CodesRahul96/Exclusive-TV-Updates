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
import com.codesrahul.exclusivetv.UnsafeHttpClient
import com.codesrahul.exclusivetv.OrderPreferenceManager
import okhttp3.Request
import com.codesrahul.exclusivetv.SecretManager
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import com.codesrahul.exclusivetv.SecurityUtil
import java.io.BufferedReader
import java.io.StringReader
import java.io.Reader

object TVList {
    fun clear() {
        list = emptyList()
        listModel = emptyList()
        // Create empty structures to update valid state
        val emptyGroups = listOf(
            TVListModel("My Collection", "My Collection", 0),
            TVListModel("All channels", "All channels", 1)
        )
        groupModel.setTVListModelList(emptyGroups)
        groupModel.setChange()
        _position.postValue(-1)
        
        // Clear EPG Memory as well to free up significantly
        EPGManager.clear()
        
        try {
            if (::appDirectory.isInitialized) {
                File(appDirectory, FILE_NAME).delete()
            }
        } catch (e: Exception) {
        }
    }
    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    const val DEFAULT_CONFIG_URL = "https://exclusivetvapi.indevs.in/api/channels"
    private lateinit var appDirectory: File

    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()
    
    private var isUpdating = false
    fun isUpdating() = isUpdating
    private val refreshLock = kotlinx.coroutines.sync.Mutex()
    
    private var lastListHash: Int = 0

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _likeChangedEvent = MutableLiveData<Pair<TVModel, Boolean>>()
    val likeChangedEvent: LiveData<Pair<TVModel, Boolean>> get() = _likeChangedEvent

    fun notifyLikeChanged(model: TVModel, liked: Boolean) {
        _likeChangedEvent.postValue(Pair(model, liked))
    }

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int>
        get() = _importProgress
        
    private val initDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()

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
        _position.value = -1
        _importProgress.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", "My Collection", 0))
        groupModel.addTVListModel(TVListModel("All channels", "All channels", 1))

        appDirectory = context.filesDir
        


        CoroutineScope(Dispatchers.IO).launch {
            val file = File(appDirectory, FILE_NAME)

            // OPTIMIZATION: Use streaming parser instead of readText() to avoid OOM
            try {
                if (file.exists()) {
                    val result = parseUniversalFile(file)
                    if (result.isNotEmpty()) {
                        list = result
                        refreshModels(MyTVApplication.getInstance())
                    }
                } else {
                    context.resources.openRawResource(R.raw.channels).bufferedReader(Charsets.UTF_8).use { reader ->
                         // Use streaming parser for resource
                         val result = parseUniversal(reader)
                         if (result.isNotEmpty()) {
                             list = result
                             refreshModels(MyTVApplication.getInstance())
                         }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read channel configuration", Toast.LENGTH_LONG).show()
                }
            } finally {
                initDeferred.complete(Unit)
            }

            // Initial config setup
            if (SP.config.isNullOrEmpty()) {
                SP.config = DEFAULT_CONFIG_URL
            }

            val currentVersion = com.codesrahul.exclusivetv.BuildConfig.VERSION_CODE
            // FIX: Only clear cache on MAJOR version changes (not every debug build)

            if (currentVersion != SP.lastVersion) {
                 val lastMajorVersion = SP.lastVersion / 1000000
                 val currentMajorVersion = currentVersion / 1000000
                 
                 if (currentMajorVersion != lastMajorVersion) {
                     File(appDirectory, FILE_NAME).delete()
                 } else {
                 }
                 SP.lastVersion = currentVersion
            }

            // Early version check removed to prevent startup crash (moved to MainActivity)



            

            // OPTIMIZATION: Removed automatic update call to prevent duplicate API fetch
            // MainActivity.onCreate() already calls TVList.update(silent=true)
            // This saves 5-10 seconds on startup
            /*
            val cfg = SP.config
            if (SP.configAutoLoad && !cfg.isNullOrEmpty()) {
                if (!SecurityUtil.isAppOutdated) {
                    update(context, cfg)
                } else {
                }
            }
            */
        }
    }



    private val _importStatus = MutableLiveData<String>()
    val importStatus: LiveData<String>
        get() = _importStatus

    private val sourceCache = mutableMapOf<String, List<TV>>()

    fun update(ctx: Context, silent: Boolean = false, force: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
                // Ensure initialization is finished before updating
                initDeferred.await()
                
                refreshLock.withLock {
                
                if (SecurityUtil.isMaintenanceMode || SecurityUtil.isAppOutdated) {
                    isUpdating = false
                    withContext(Dispatchers.Main) {
                        _importProgress.value = 0
                        _importStatus.value = ""
                    }
                    return@launch
                }
                
                val showUi = !silent || size() == 0
                
                try {
                
                
                isUpdating = true
                
                // Ensure all URLs from preferences are included, but prioritize Main API
                val mainUrl = SP.config ?: DEFAULT_CONFIG_URL
                val urls = mutableListOf<String>()
                
                // 1. Get Custom Playlists (filter out empty or Main API leaks)
                val customUrls = SP.playlistUrls.filter { url ->
                    url.isNotEmpty() && url != mainUrl && !url.startsWith(mainUrl)
                }
                
                // 2. Load Logic: Custom sources if they exist, otherwise fallback to Main API
                if (customUrls.isNotEmpty()) {
                    urls.addAll(customUrls)
                } else if (mainUrl.isNotEmpty()) {
                    // NEW LOGIC: support single file/API
                    if (mainUrl.contains("/api/", ignoreCase = true) || 
                        mainUrl.endsWith(".json", ignoreCase = true) || 
                        mainUrl.endsWith(".txt", ignoreCase = true) || 
                        mainUrl.endsWith(".m3u", ignoreCase = true) || 
                        mainUrl.endsWith(".m3u8", ignoreCase = true)) {
                        urls.add(mainUrl)
                    } else {
                        // Legacy: Load Main API (Split into 4 parts for performance/reliability)
                        val baseUrl = if (mainUrl.endsWith("/")) mainUrl else "$mainUrl/"
                        urls.add(baseUrl + "part1.txt")
                        urls.add(baseUrl + "part2.txt")
                        urls.add(baseUrl + "part3.txt")
                        urls.add(baseUrl + "part4.txt")
                    }
                }

                
                withContext(Dispatchers.Main) {
                    if (showUi) {
                         _importProgress.value = 5
                         _importStatus.value = "Initializing..."
                    } else {
                         _importProgress.value = 0
                         _importStatus.value = "Checking for updates..."
                    }
                }
                
                val client = UnsafeHttpClient.client
                val allChannels = mutableListOf<TV>()
                
                // Fetch all playlists concurrently
                val totalSources = urls.size
                var completedSources = 0

                val deferredResults = urls.mapIndexed { index, url ->
                    async {
                        try {
                           if (showUi) {
                               withContext(Dispatchers.Main) {
                                   _importStatus.value = "Connecting: Source ${index + 1}/$totalSources"
                               }
                           }
                           
                           val requestBuilder = Request.Builder().url(url).get()
                           val etag = if (!force && sourceCache.containsKey(url)) SP.getEtag(url) else null
                           if (etag != null) {
                               requestBuilder.header("If-None-Match", etag)
                           }
                           val request = requestBuilder.build()
                           
                           client.newCall(request).execute().use { response ->
                               // 1. Handle 304 Not Modified (Server says content hasn't changed)
                               if (response.code == 304) {
                                   withContext(Dispatchers.Main) {
                                        if (showUi) _importStatus.value = "Source ${index + 1} up to date"
                                   }
                                   // RETURN CACHE: Retrieve previous channels for this specific URL
                                   return@async sourceCache[url] ?: emptyList<TV>()
                               }
                               
                               if (response.isSuccessful) {
                                   withContext(Dispatchers.Main) {
                                       if (!silent || size() == 0) {
                                           _importStatus.value = "Downloading: Source ${index + 1}/$totalSources"
                                       }
                                   }
                                   
                                   // Save ETag for next time
                                   val newEtag = response.header("ETag")
                                   if (newEtag != null) {
                                       SP.setEtag(url, newEtag)
                                   }

                                   val responseBody = response.body
                                   if (responseBody != null) {
                                       val tempFile = File(ctx.cacheDir, "playlist_source_$index.tmp")
                                       try {
                                           responseBody.byteStream().use { input ->
                                               tempFile.outputStream().use { output ->
                                                   input.copyTo(output)
                                               }
                                           }
                                           
                                           // OPTIMIZATION: Parse in parallel with other downloads
                                           val channels = parseUniversalFile(tempFile)
                                           
                                           // UPDATE CACHE
                                           sourceCache[url] = channels
                                           
                                           withContext(Dispatchers.Main) {
                                               if (showUi) _importStatus.value = "Parsed: ${channels.size} channels from source ${index + 1}"
                                           }
                                           return@async channels
                                           
                                        } catch (e: Exception) {
                                            return@async sourceCache[url] ?: emptyList<TV>()
                                        } finally {
                                            tempFile.delete() 
                                        }
                                   } else {
                                       return@async sourceCache[url] ?: emptyList<TV>()
                                   }
                               } else {
                                   return@async sourceCache[url] ?: emptyList<TV>()
                               }
                           }
                        } catch (e: Exception) {
                           return@async sourceCache[url] ?: emptyList<TV>()
                        } finally {
                            completedSources++
                            if (showUi) {
                                val progress = ((completedSources.toFloat() / totalSources) * 80).toInt() + 10
                                withContext(Dispatchers.Main) {
                                    _importProgress.value = progress
                                }
                            }
                        }
                    }
                }
                
                
                // FINAL SECURITY CHECK before UI update
                if (SecurityUtil.isMaintenanceMode || SecurityUtil.isAppOutdated) {
                    return@launch
                }

                // Await all results
                val results = deferredResults.map { it.await() }
                
                // Rebuild allChannels from all results (New + Cached)
                allChannels.clear()
                results.forEach { res ->
                    if (res != null) allChannels.addAll(res)
                }

                if (allChannels.isNotEmpty()) {
                      if (showUi) {
                           withContext(Dispatchers.Main) {
                                _importStatus.value = "Finalizing..."
                           }
                      }
                     
                      // PROFESSIONAL IMPROVEMENT: Remove redundant unrolling. 
                      // The player already handles backup links (uris list) correctly.
                      // This keeps the UI list clean and prevents duplicate entries.
                      val finalChannels = allChannels.toMutableList()
                      
                      // FIX: Re-index securely
                      finalChannels.forEachIndexed { index, tv ->
                          tv.id = index
                      }

                      // DIFF OPTIMIZATION: Only update if anything actually changed
                      val newListHash = finalChannels.sumOf { (it.uris.firstOrNull() ?: "").hashCode() } + finalChannels.size
                      if (newListHash == lastListHash && list.isNotEmpty()) {
                          withContext(Dispatchers.Main) {
                              if (showUi) {
                                  _importProgress.value = 100
                                  _importStatus.value = "Up to date"
                              }
                          }
                          return@launch
                      }
                      lastListHash = newListHash

                      val gson = com.google.gson.Gson()
                      val jsonStr = gson.toJson(finalChannels)
                      
                      val file = File(ctx.filesDir, FILE_NAME)
                      file.writeText(jsonStr) // Save formatted JSON
                      
                      // Update memory
                      list = finalChannels
                      
                       withContext(Dispatchers.Main) {
                           refreshModelsInternal(ctx)
                           checkChannelsInBackground()
                           
                           // Always update status to "Complete" only if we were showing progress
                           if (showUi) {
                              _importProgress.value = 100
                              _importStatus.value = "Complete"
                           }
                          
                          if (!silent && SP.config != DEFAULT_CONFIG_URL) {
                               "Channels Updated: ${list.size}".showToast()
                          }
                          
                          // OPTIMIZATION: Defer EPG loading to avoid blocking startup
                          // Load EPG 10 seconds after channels are ready
                          if (SP.epgEnabled) {
                              android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                  EPGManager.init(ctx)
                                  CoroutineScope(Dispatchers.IO).launch {
                                       withContext(Dispatchers.Main) { 
                                           if (showUi) _importStatus.value = "Updating Guide..." 
                                       } 
                                      EPGManager.fetchEPG(force = false)
                                      withContext(Dispatchers.Main) {
                                          listModel.forEach { it.updateEPG() }
                                      }
                                  }
                              }, 10000) // 10 second delay
                          }
                      }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!silent) "Failed to update channels, using cached data".showToast()
                        _importProgress.value = 0
                        _importStatus.value = "Failed"
                        
                        // FIX: Try to load from cache if no channels in memory
                        if (list.isEmpty()) {
                            val file = File(ctx.filesDir, FILE_NAME)
                            if (file.exists()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val cached = parseUniversalFile(file)
                                        if (cached.isNotEmpty()) {
                                            list = cached
                                            withContext(Dispatchers.Main) {
                                                refreshModelsInternal(ctx)
                                                "Loaded ${cached.size} channels from cache".showToast()
                                            }
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                            } else {
                            }
                        } else {
                        }
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                     if (!silent) "Update error: ${e.message}".showToast()
                     _importProgress.value = 0
                     _importStatus.value = "Error"
                }
            } finally {
                isUpdating = false
                if (showUi) {
                    withContext(Dispatchers.Main) {
                        // If we didn't reach "Complete" or "Failed" yet, clear it after a delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isUpdating) {
                                _importStatus.value = ""
                                _importProgress.value = 0
                            }
                        }, 5000)
                    }
                }
            }
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
            emptyList()
        }
    }

    // Helper to fetch content from URL
    private suspend fun fetchContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) { _importStatus.value = "Downloading Playlist..." }
            val client = com.codesrahul.exclusivetv.SecureHttpClient.client
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    withContext(Dispatchers.Main) { _importStatus.value = "Processing Channels..." }
                    return@withContext body
                }
            }
        } catch (e: Exception) {
            // Log exception or handle it
        }
        return@withContext null
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { 
                    it.bufferedReader().readText() 
                }
                
                if (!content.isNullOrBlank()) {
                    val parsed = parseUniversal(content)
                    if (parsed.isNotEmpty()) {
                        list = parsed
                        SP.addPlaylistUrl(uri.toString()) // Optional: Save URI? Maybe not readable later.
                        withContext(Dispatchers.Main) {
                            refreshModels(context)
                            _importStatus.value = "Processing File..."
                            Toast.makeText(context, "Loaded ${parsed.size} channels from file", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                         withContext(Dispatchers.Main) {
                             Toast.makeText(context, "No channels found in file", Toast.LENGTH_SHORT).show()
                         }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseUniversal(reader: BufferedReader): List<TV> {
        // Use PushbackReader to peek at the first non-whitespace character 
        // to decide between JSON and M3U without consuming the stream or relying on mark/reset.
        val pushbackReader = java.io.PushbackReader(reader, 10)
        
        try {
            var firstChar = -1
            // Skip leading whitespace to find the first significant character
            while (true) {
                val c = pushbackReader.read()
                if (c == -1) break
                if (!Character.isWhitespace(c)) {
                    firstChar = c
                    pushbackReader.unread(c)
                    break
                }
            }
            
            if (firstChar == '{'.toInt() || firstChar == '['.toInt()) {
                return GenericJsonParser.parse(pushbackReader)
            } else if (firstChar == '#'.toInt()) {
                return M3UParser.parse(BufferedReader(pushbackReader))
            } else if (firstChar >= 0x4D00 && firstChar <= 0x4DFF) {
                // Gua requires full string for decoding. 
                // We read the rest of the stream into a string.
                val remaining = BufferedReader(pushbackReader).readText()
                val g = Gua()
                val decoded = if (g.verify(remaining)) g.decode(remaining) else remaining
                val secretKey = SecretManager.getAppKey()
                val finalContent = SecurityUtil.decryptChannelData(decoded, secretKey)
                return parseUniversal(finalContent)
            } else {
            }
        } catch (e: Exception) {
        }
        
        return emptyList()
    }

    private fun parseUniversal(content: String): List<TV> {
        var string = content.trim()
        // SECURITY UPGRADE: Use Native Key
        // Layer 1: Gua64 Decoding (Encoding layer)
        val g = Gua()
        val decodedFromGua = if (g.verify(string)) g.decode(string) else string
        
        // Layer 2: AES Decryption (Security layer)
        val secretKey = SecretManager.getAppKey()
        val finalString = SecurityUtil.decryptChannelData(decodedFromGua, secretKey)
        
        string = finalString
        
        if (string.isBlank()) return emptyList()
        val decryptedContent = string

        // 1. JSON Detection (PRIORITY)
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
                            try {
                                val tv = gson.fromJson(obj, TV::class.java)
                                if (tv != null && !tv.uris.isNullOrEmpty()) {
                                    allChannels.add(tv)
                                } else {
                                    val genericTv = GenericJsonParser.parseSingleObject(obj, allChannels.size)
                                    if (genericTv != null) allChannels.add(genericTv)
                                }
                            } catch (e: Exception) {
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
                    return allChannels
                }
            } catch (e: Exception) {
            }
        }

        // 2. PLS Playlist
        if (decryptedContent.contains("[playlist]", ignoreCase = true)) {
             val plsList = PlsParser.parse(decryptedContent)
             if (plsList.isNotEmpty()) return plsList
        }

        // 3. M3U / M3U8 / Kodi / Star Playlist
        if (decryptedContent.contains("#EXTINF") || decryptedContent.contains("#EXTM3U") || 
            decryptedContent.contains("EXTHTTP") || decryptedContent.contains("#KODIPROP")) {
            
            // Route to KodiParser if it contains Kodi properties or Star headers
            if (decryptedContent.contains("#KODIPROP") || decryptedContent.contains("EXTHTTP")) {
                try {
                    val kodiList = KodiParser.parse(decryptedContent)
                    if (kodiList.isNotEmpty()) return kodiList
                } catch (e: Exception) {
                }
            }

            try {
                // Use String Reader for M3UParser (compatible)
                val m3uList = M3UParser.parse(java.io.BufferedReader(java.io.StringReader(decryptedContent)))
                if (m3uList.isNotEmpty()) return m3uList
            } catch (e: Exception) {
            }
        }

        // 4. Fallback: Try Simple List Parser (Original Logic Restored)
        if (string.contains("http://") || string.contains("https://")) {
            val simpleList = SimpleListParser.parse(string)
            if (simpleList.isNotEmpty()) {
                return simpleList
            }
        }

        return emptyList()
    }

    private suspend fun parseUniversalFile(file: File): List<TV> = withContext(Dispatchers.IO) {
        // OPTIMIZATION: Direct Stream Parsing for Cache (Avoids reading 50MB string into memory)
        val filename = file.name
        
        // 1. FASTEST PATH: If it's the internal cache file, we KNOW it is JSON.
        // Skip all guessing and sniffing.
        if (filename == FILE_NAME || filename.endsWith(".json")) {
            try {
                // Open standard BufferedReader for performance
                val reader = java.io.BufferedReader(java.io.FileReader(file), 8192)
                // Use the streamlined GenericJsonParser which now supports streaming
                val result = GenericJsonParser.parse(reader)
                reader.close()
                if (result.isNotEmpty()) return@withContext result
            } catch (e: Exception) {
                // If JSON fails, fall back to legacy sniffing below
            }
        }

        // 2. Legacy/Universal Logic for unknown files
        // Peek Header
        val peekBuilder = StringBuilder()
        try {
            java.io.BufferedReader(java.io.FileReader(file)).use { br ->
                val buffer = CharArray(1024)
                val read = br.read(buffer)
                if (read > 0) peekBuilder.append(buffer, 0, read)
            }
        } catch (e: Exception) {
        }
        
        val peekContent = peekBuilder.toString().trim()
        
        // Strategy A: JSON
        if (peekContent.startsWith("[") || peekContent.startsWith("{")) {
            try {
                val reader = java.io.BufferedReader(java.io.FileReader(file))
                val result = GenericJsonParser.parse(reader)
                reader.close()
                if (result.isNotEmpty()) {
                     return@withContext expandNestedPlaylists(result)
                }
            } catch (e: Exception) {
            }
        }
        
        // 3. Strategy B: Gua / Encrypted (Legacy String requirement)
        if (peekContent.isNotEmpty() && (peekContent[0].toInt() >= 0x4D00 && peekContent[0].toInt() <= 0x4DFF)) {
            try {
                val content = file.readText()
                val g = Gua()
                val decodedFromGua = if (g.verify(content)) g.decode(content) else content
                val secretKey = SecretManager.getAppKey()
                val finalContent = SecurityUtil.decryptChannelData(decodedFromGua, secretKey)
                val result = parseUniversal(finalContent)
                if (result.isNotEmpty()) return@withContext expandNestedPlaylists(result)
            } catch (e: Exception) {
            }
        }

        // 4. Strategy C: M3U / Universal Stream
        try {
            val reader = java.io.BufferedReader(java.io.FileReader(file))
            val result = M3UParser.parse(reader)
            reader.close()
            
            if (result.isNotEmpty()) {
                 return@withContext expandNestedPlaylists(result)
            }
        } catch (e: Exception) {
        }
        
        // 5. Strategy D: Legacy String Fallback
        try {
            val reader = file.bufferedReader()
            val result = parseUniversal(reader)
            reader.close()
            if (result.isNotEmpty()) return@withContext expandNestedPlaylists(result)
        } catch (e: Exception) {
        }
        
        return@withContext emptyList<TV>()
    }

    private suspend fun expandNestedPlaylists(originalList: List<TV>, depth: Int = 0): List<TV> = withContext(Dispatchers.IO) {
        // Prevent infinite recursion or excessive depth
        // Optimization: Don't auto-expand large lists. Big lists are usually final channel lists.
        if (depth > 1 || originalList.size > 20) {
            return@withContext originalList
        }

        val client = UnsafeHttpClient.client
        // Limit concurrency to avoid overwhelming servers (max 5 parallel fetches)
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        
        // 1. Map each item to a Deferred result (or immediate value)
        val deferredResults = originalList.map { tv ->
            val url = tv.uris.firstOrNull() ?: ""
            
            // Broader check for playlists
            // If it's NOT a clearly identified stream extension, treat it as a potential playlist
            // specially if coming from a dynamic API
            val isStream = (url.contains(".m3u8", ignoreCase = true) || 
                           url.contains(".mpd", ignoreCase = true) ||
                           url.contains(".ts", ignoreCase = true) ||
                           url.contains(".mkv", ignoreCase = true) ||
                           url.contains(".mp4", ignoreCase = true) ||
                           url.startsWith("rtsp://", ignoreCase = true) ||
                           url.startsWith("rtmp://", ignoreCase = true) ||
                           url.contains("/manifest", ignoreCase = true) ||
                           url.contains("playlist.m3u8", ignoreCase = true) ||
                           url.contains("stream/", ignoreCase = true) ||
                           url.contains("/live/", ignoreCase = true) ||
                           url.contains("/play/", ignoreCase = true)) && 
                           !url.contains(".m3u", ignoreCase = true) // .m3u is usually an IPTV list we WANT to expand

            // If it has children already, it's a group, don't expand
            if (tv.child.isNotEmpty()) {
                 async { listOf(tv) }
            }
            // If it's a candidate (not a stream, or a .m3u), fetch asynchronously
            else if ((!isStream || url.contains(".m3u", ignoreCase = true)) && url.startsWith("http")) {
                async {
                    semaphore.acquire()
                    try {
                        if (SecurityUtil.isMaintenanceMode) return@async listOf(tv)
                        val requestBuilder = Request.Builder().url(url).get()
                        
                        // FIX: Do NOT propagate parent headers to the nested playlist fetch.
                        // We want to fetch the M3U using the standard client (Chrome UA), just like Source Config.
                        // Propagating API headers to a GitHub/External URL is incorrect.
                        
                        val request = requestBuilder.build()
                         
                        // Execute blocking call with shorter timeout for nested expansion
                        val expansionClient = client.newBuilder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        
                        expansionClient.newCall(request).execute().use { response ->
                            // FIX: Use streaming instead of .string() to avoid OOM
                            val responseBody = response.body
                            if (response.isSuccessful && responseBody != null) {
                                // Use UNIVERSAL parser with streaming
                                // We use Reader helper
                                val sourceStream = responseBody.byteStream()
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(sourceStream, Charsets.UTF_8))
                                
                                 val subChannels = parseUniversal(reader)
                                 if (subChannels.isNotEmpty()) {
                                     subChannels.forEach { child ->
                                         // Inherit group from parent name if child has no group or is "Uncategorized"
                                         if (child.group.isBlank() || child.group == "Uncategorized") {
                                             child.group = tv.name
                                         }
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
            refreshLock.withLock {
                withContext(Dispatchers.Main) {
                    refreshModelsInternal(ctx)
                }
            }
        }
    }

    private suspend fun refreshModelsInternal(ctx: Context) {
        if (SecurityUtil.isMaintenanceMode || SecurityUtil.isAppOutdated) {
            return
        }
        if (!::list.isInitialized || list.isEmpty()) {
            return
        }

        try {
            // 1. Preparation Phase (Offloaded to Default dispatcher)
            val preparedData = withContext(Dispatchers.Default) {
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

                data class PreparedGroup(val originalName: String, val displayName: String, val index: Int, val channels: List<TV>)
                val preparedGroups = mutableListOf<PreparedGroup>()
                var groupIdx = 2
                
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
                    
                    preparedGroups.add(PreparedGroup(originalCategoryName, displayCategoryName, groupIdx, sortedChannels))
                    groupIdx++
                }
                preparedGroups
            }

            // 2. Update Phase (Main Thread)
            withContext(Dispatchers.Main) {
                val listModelSnapshot = listModel 
                val listModelNew: MutableList<TVModel> = mutableListOf()
                val oldIdToModel = listModelSnapshot.associateBy { it.tv.uris.firstOrNull() ?: "" }
                
                var id = 0
                val currentGroupModels = groupModel.getTVListModelList()
                val oldGroupMap = currentGroupModels.associateBy { it.getOriginalName() }
                
                val newGroupList = mutableListOf<TVListModel>()
                
                // Preserve/Create Special Groups
                val collectionGroup = groupModel.getTVListModel(0) ?: TVListModel("My Collection", "My Collection", 0)
                val allChannelsGroup = groupModel.getTVListModel(1) ?: TVListModel("All channels", "All channels", 1)
                
                newGroupList.add(collectionGroup)
                newGroupList.add(allChannelsGroup)

                for (group in preparedData) {
                    val isUncategorized = group.originalName.isBlank() || group.originalName == "Uncategorized"
                    
                    val tvListModel = if (!isUncategorized) {
                        val model = oldGroupMap[group.originalName] ?: TVListModel(group.displayName, group.originalName, group.index)
                        model.updateMetadata(group.displayName, group.index)
                        model
                    } else null

                    val groupChannels = mutableListOf<TVModel>()
                    for (tv in group.channels) {
                         tv.id = id
                         val tvModel = oldIdToModel[tv.uris.firstOrNull() ?: ""]?.apply { update(tv) } ?: TVModel(tv)
                         tvModel.groupIndex = group.index
                         tvModel.listIndex = groupChannels.size
                         
                         groupChannels.add(tvModel)
                         listModelNew.add(tvModel)
                         id++
                    }

                    if (tvListModel != null) {
                        tvListModel.setTVListModel(groupChannels)
                        newGroupList.add(tvListModel)
                    }
                }

                val likedChannels = listModelNew.filter { it.like.value == true || SP.getLike(it.tv.id) }.toMutableList()
                collectionGroup.setTVListModel(likedChannels)

                listModel = listModelNew
                groupModel.setTVListModelList(newGroupList)
                allChannelsGroup.setTVListModel(listModel)
                groupModel.setChange()
                
                if (SP.epgEnabled) {
                    EPGManager.init(ctx)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            EPGManager.fetchEPG(force = false)
                            withContext(Dispatchers.Main) {
                                listModel.forEach { it.updateEPG() }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
        }
    }

    private fun checkChannelsInBackground() {
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!::list.isInitialized || list.isEmpty()) return@launch

                val initialSize = list.size

                // Use concurrent collection for thread safety if needed, 
                // but map + filter is safer.
                val currentList = list.toList()
                
                // Limit concurrency to avoid OOM or OS limits, but high enough for speed
                // 50 concurrent checks is standard for fast checkers
                val semaphore = kotlinx.coroutines.sync.Semaphore(50)
                
                val validList = currentList.map { tv ->
                    async {
                        semaphore.acquire()
                        try {
                            var isAlive = false
                            if (tv.uris.isNotEmpty()) {
                                for (uri in tv.uris) {
                                    if (checkLink(uri, tv.headers)) {
                                        isAlive = true
                                        break
                                    }
                                }
                            }
                            if (isAlive) tv else null
                        } finally {
                            semaphore.release()
                        }
                    }
                }.mapNotNull { it.await() }

                val removedCount = initialSize - validList.size

                if (removedCount > 0) {
                    list = validList
                    withContext(Dispatchers.Main) {
                        refreshModels(MyTVApplication.getInstance())
                        if (SP.epgEnabled) {
                            EPGManager.fetchEPG()
                            listModel.forEach { it.updateEPG() }
                        }
                        "$removedCount not working channels removed".showToast(Toast.LENGTH_LONG)
                    }
                } else {
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun checkLink(url: String, headers: Map<String, String>? = null): Boolean {
        return try {
            val requestBuilder = Request.Builder().url(url)
            headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            
            // FIX: Increased timeout and added GET fallback for servers that don't support HEAD
            val checkClient = UnsafeHttpClient.client.newBuilder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            // Try HEAD request first (faster)
            var request = requestBuilder.head().build()
            checkClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return true
                } else if (response.code == 405) {
                    // Method not allowed, try GET instead
                    request = requestBuilder.get().build()
                    checkClient.newCall(request).execute().use { getResponse ->
                        return getResponse.isSuccessful
                    }
                }
                return false
            }
        } catch (e: Exception) {
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

    fun setPositionByModel(tvModel: TVModel): Boolean {
        val index = listModel.indexOf(tvModel)
        if (index == -1) {
            return false
        }
        return setPosition(index)
    }

    fun setPosition(position: Int): Boolean {
        if (position < 0 || position >= size()) {
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
                 val strictIndex = listModel.indexOfFirst { model ->
                     model.tv.name == savedName && model.tv.uris.any { it == savedUrl }
                 }
                 if (strictIndex != -1) {
                     return strictIndex
                 }
            }

            // Priority 2: URL Match (Any URL in the list)
            val index = listModel.indexOfFirst { model ->
                model.tv.uris.any { it == savedUrl }
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
