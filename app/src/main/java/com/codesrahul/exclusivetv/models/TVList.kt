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
import com.codesrahul.exclusivetv.StringObfuscator
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
    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

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
        _position.value = 0
        _importProgress.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", "My Collection", 0))
        groupModel.addTVListModel(TVListModel("All channels", "All channels", 1))

        appDirectory = context.filesDir
        


        CoroutineScope(Dispatchers.IO).launch {
            val file = File(appDirectory, FILE_NAME)

            // OPTIMIZATION: Use streaming parser instead of readText() to avoid OOM
            try {
                if (file.exists()) {
                    Log.i(TAG, "Parsing local file stream: $file")
                    val result = parseUniversalFile(file)
                    if (result.isNotEmpty()) {
                        list = result
                        refreshModels(MyTVApplication.getInstance())
                    }
                } else {
                    Log.i(TAG, "Parsing default resource stream")
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
                Log.e(TAG, "error init parsing", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read channel configuration", Toast.LENGTH_LONG).show()
                }
            } finally {
                initDeferred.complete(Unit)
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
            // FIX: Only clear cache on MAJOR version changes (not every debug build)
            if (currentVersion != SP.lastVersion) {
                 val lastMajorVersion = SP.lastVersion / 1000000
                 val currentMajorVersion = currentVersion / 1000000
                 
                 if (currentMajorVersion != lastMajorVersion) {
                     Log.i(TAG, "Major version change detected ($lastMajorVersion -> $currentMajorVersion), clearing cache")
                     File(appDirectory, FILE_NAME).delete()
                 } else {
                     Log.i(TAG, "Minor version change detected, keeping cache")
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
                    Log.i(TAG, "Skipping channel load - update required")
                }
            }
            */
        }
    }



    private val _importStatus = MutableLiveData<String>()
    val importStatus: LiveData<String>
        get() = _importStatus

    fun update(ctx: Context, silent: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure initialization is finished before updating
                initDeferred.await()
                
                Log.i(TAG, "=== UPDATE STARTED ===")
                Log.i(TAG, "Current channel count: ${if (::list.isInitialized) list.size else 0}")
                Log.i(TAG, "Silent mode: $silent")
                
                isUpdating = true
                
                // Get all URLs to fetch
                val urls = SP.playlistUrls.toMutableSet()
                
                // Ensure default API is only added if no other sources are present
                if (urls.size > 1 && urls.contains(DEFAULT_CONFIG_URL)) {
                    urls.remove(DEFAULT_CONFIG_URL)
                    Log.i(TAG, "Custom sources found. Disabling Main API.")
                } else if (urls.isEmpty()) {
                    urls.add(DEFAULT_CONFIG_URL)
                }
                
                // If the set changed (e.g. first run), save it
                if (urls.size != SP.playlistUrls.size) {
                    urls.forEach { SP.addPlaylistUrl(it) }
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
                
                val client = UnsafeHttpClient.client
                val allChannels = mutableListOf<TV>()
                var successCount = 0
                
                // Fetch all playlists concurrently
                val totalSources = urls.size
                var completedSources = 0

                val deferredResults = urls.mapIndexed { index, url ->
                    async {
                        try {
                           withContext(Dispatchers.Main) {
                               if (!silent) _importStatus.value = "Loading source ${index + 1}/$totalSources..."
                           }
                           Log.i(TAG, "Fetching playlist: $url")
                           val request = Request.Builder().url(url).get().build()
                           
                           client.newCall(request).execute().use { response ->
                               if (response.isSuccessful) {
                                   val responseBody = response.body()
                                   if (responseBody != null) {
                                       val tempFile = File(ctx.cacheDir, "playlist_source_$index.tmp")
                                       try {
                                           // Download stream to file
                                           responseBody.byteStream().use { input ->
                                               tempFile.outputStream().use { output ->
                                                   input.copyTo(output)
                                               }
                                           }
                                           
                                           // Process the file
                                           val channels = parseUniversalFile(tempFile)
                                           
                                           if (channels.isNotEmpty()) {
                                                Log.i(TAG, "Source $index parsed successfully: ${channels.size} channels")
                                           }
                                           
                                           return@async channels
                                           
                                       } catch (e: Exception) {
                                           Log.e(TAG, "Error processing file source $index", e)
                                           return@async emptyList<TV>()
                                       } finally {
                                           // Cleanup
                                           tempFile.delete() 
                                       }
                                   } else {
                                       return@async emptyList<TV>()
                                   }
                               } else {
                                   Log.e(TAG, "Failed to fetch $url: ${response.code()}")
                                   return@async emptyList<TV>()
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
                         checkChannelsInBackground()
                         // Only show toast if using custom config (not default)
                         if (!silent && SP.config != DEFAULT_CONFIG_URL) {
                             "Channels updated from $successCount sources".showToast()
                         }
                         _importProgress.value = 100
                         _importStatus.value = "Complete"
                         
                         // OPTIMIZATION: Defer EPG loading to avoid blocking startup
                         // Load EPG 10 seconds after channels are ready
                         if (SP.epgEnabled) {
                             android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                 EPGManager.init(ctx)
                                 CoroutineScope(Dispatchers.IO).launch {
                                     withContext(Dispatchers.Main) { _importStatus.value = "Updating Guide..." } 
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
                                        Log.i(TAG, "Loading channels from cache file")
                                        val cached = parseUniversalFile(file)
                                        if (cached.isNotEmpty()) {
                                            list = cached
                                            withContext(Dispatchers.Main) {
                                                refreshModels(ctx)
                                                "Loaded ${cached.size} channels from cache".showToast()
                                            }
                                            Log.i(TAG, "Successfully loaded ${cached.size} channels from cache")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to load cache", e)
                                    }
                                }
                            } else {
                                Log.w(TAG, "No cache file available")
                            }
                        } else {
                            Log.i(TAG, "Keeping existing ${list.size} channels in memory")
                        }
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
                Log.i(TAG, "=== UPDATE COMPLETED ===")
                Log.i(TAG, "Final channel count: ${if (::list.isInitialized) list.size else 0}")
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
                            Toast.makeText(context, "Loaded ${parsed.size} channels from file", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                         withContext(Dispatchers.Main) {
                             Toast.makeText(context, "No channels found in file", Toast.LENGTH_SHORT).show()
                         }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing ParseUri", e)
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
                Log.d(TAG, "Detected JSON format via peek")
                return GenericJsonParser.parse(pushbackReader)
            } else if (firstChar == '#'.toInt()) {
                Log.d(TAG, "Detected M3U format via peek")
                return M3UParser.parse(BufferedReader(pushbackReader))
            } else if (firstChar >= 0x4D00 && firstChar <= 0x4DFF) {
                Log.d(TAG, "Detected Gua encoded content via peek")
                // Gua requires full string for decoding. 
                // We read the rest of the stream into a string.
                val remaining = BufferedReader(pushbackReader).readText()
                val decoded = Gua().decode(remaining)
                return parseUniversal(decoded)
            } else {
                Log.w(TAG, "Unknown format starting with '${firstChar.toChar()}' (0x${Integer.toHexString(firstChar)})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during stream peeking", e)
        }
        
        return emptyList()
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
                Log.d(TAG, "Not a valid JSON, falling back")
            }
        }

        // 2. PLS Playlist
        if (decryptedContent.contains("[playlist]", ignoreCase = true)) {
             val plsList = PlsParser.parse(decryptedContent)
             if (plsList.isNotEmpty()) return plsList
        }

        // 2.5 Star / Hotstar Playlist (Custom Parser)
        if (decryptedContent.contains("EXTHTTP") && !decryptedContent.contains("#KODIPROP")) {
             try {
                 val starList = StarParser.parse(decryptedContent)
                 if (starList.isNotEmpty()) return starList
             } catch (e: Exception) {
                 Log.e(TAG, "StarParser error", e)
             }
        }

        // 3. M3U / M3U8 / Kodi Playlist
        if (decryptedContent.contains("#EXTINF") || decryptedContent.contains("#EXTM3U") || 
            decryptedContent.contains("EXTHTTP") || decryptedContent.contains("#KODIPROP")) {
            
            if (decryptedContent.contains("#KODIPROP")) {
                try {
                    val kodiList = KodiParser.parse(decryptedContent)
                    if (kodiList.isNotEmpty()) return kodiList
                } catch (e: Exception) {
                    Log.e(TAG, "Kodi parse error", e)
                }
            }

            try {
                // Use String Reader for M3UParser (compatible)
                val m3uList = M3UParser.parse(java.io.BufferedReader(java.io.StringReader(decryptedContent)))
                if (m3uList.isNotEmpty()) return m3uList
            } catch (e: Exception) {
                Log.e(TAG, "M3U parse error", e)
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

    private fun parseUniversalFile(file: File): List<TV> {
        // 1. Peek Header
        val peekBuilder = StringBuilder()
        try {
            java.io.BufferedReader(java.io.FileReader(file)).use { br ->
                val buffer = CharArray(1024)
                val read = br.read(buffer)
                if (read > 0) peekBuilder.append(buffer, 0, read)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error peeking file", e)
        }
        
        val peekContent = peekBuilder.toString().trim()
        
        // 2. Strategy A: JSON
        if (peekContent.startsWith("[") || peekContent.startsWith("{")) {
            try {
                // Open new Reader
                val reader = java.io.BufferedReader(java.io.FileReader(file))
                val result = GenericJsonParser.parse(reader)
                reader.close() // Close explicitly
                
                if (result.isNotEmpty()) {
                     Log.i(TAG, "Parsed ${result.size} channels from JSON File")
                     return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSON File Parse failed, falling back")
            }
        }
        
        // 3. Strategy B: Gua / Encrypted (Legacy String requirement)
        if (peekContent.isNotEmpty() && (peekContent[0].toInt() >= 0x4D00 && peekContent[0].toInt() <= 0x4DFF)) {
             try {
                 Log.i(TAG, "Parsing Gua file: $file")
                 val content = file.readText()
                 val decoded = Gua().decode(content)
                 val result = parseUniversal(decoded)
                 if (result.isNotEmpty()) return result
             } catch (e: Exception) {
                 Log.e(TAG, "Gua File Parse failed", e)
             }
        }

        // 4. Strategy C: M3U / Universal Stream
        try {
            val reader = java.io.BufferedReader(java.io.FileReader(file))
            val result = M3UParser.parse(reader)
            reader.close()
            
            if (result.isNotEmpty()) {
                 Log.i(TAG, "Parsed ${result.size} channels from M3U File")
                 return result
            }
        } catch (e: Exception) {
             Log.w(TAG, "M3U File Parse failed", e)
        }
        
        // 5. Strategy D: Legacy String Fallback
        try {
            Log.i(TAG, "Falling back to Legacy String Parsing (Limited Stream)")
            val reader = file.bufferedReader()
            val result = parseUniversal(reader)
            reader.close()
            if (result.isNotEmpty()) return result
        } catch (e: Exception) {
            Log.e(TAG, "Legacy Parse failed", e)
        }
        
        return emptyList()
    }

    private suspend fun expandNestedPlaylists(originalList: List<TV>, depth: Int = 0): List<TV> = withContext(Dispatchers.IO) {
        // Prevent infinite recursion or excessive depth
        if (depth > 3) {
            Log.w(TAG, "Max playlist expansion depth reached, skipping nested content")
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
                            // FIX: Use streaming instead of .string() to avoid OOM
                            val responseBody = response.body()
                            if (response.isSuccessful && responseBody != null) {
                                // Use UNIVERSAL parser with streaming
                                // We use Reader helper
                                val sourceStream = responseBody.byteStream()
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(sourceStream, Charsets.UTF_8))
                                
                                val subChannels = parseUniversal(reader)
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
            // FIX: Add synchronization to prevent race conditions
            refreshLock.withLock {
            try {
                if (!::list.isInitialized || list.isEmpty()) {
                    Log.w(TAG, "Cannot refresh models: list not initialized or empty")
                    return@withLock
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

                data class PreparedGroup(val originalName: String, val displayName: String, val index: Int, val channels: List<TV>)
                val preparedGroups = mutableListOf<PreparedGroup>()
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
                    
                    preparedGroups.add(PreparedGroup(originalCategoryName, displayCategoryName, groupIndex, sortedChannels))
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
                    newGroups.add(groupModel.getTVListModel(0) ?: TVListModel("My Collection", "My Collection", 0))
                    newGroups.add(groupModel.getTVListModel(1) ?: TVListModel("All channels", "All channels", 1))

                    for ((itemOriginalName, itemDisplayName, idx, channels) in preparedGroups) {
                        val tvListModel = TVListModel(itemDisplayName, itemOriginalName, idx)
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
                            try {
                                EPGManager.fetchEPG(force = false) // Don't force every refresh
                                withContext(Dispatchers.Main) {
                                    listModel.forEach { it.updateEPG() }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching EPG in refreshModels", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in refreshModels", e)
            }
            } // End of refreshLock.withLock
        }
    }

    private fun checkChannelsInBackground() {
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!::list.isInitialized || list.isEmpty()) return@launch

                val initialSize = list.size
                Log.i(TAG, "Starting background channel check. Total: $initialSize")

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
                    Log.i(TAG, "No dead channels found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkChannelsInBackground", e)
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
                } else if (response.code() == 405) {
                    // Method not allowed, try GET instead
                    Log.d(TAG, "HEAD not supported for $url, trying GET")
                    request = requestBuilder.get().build()
                    checkClient.newCall(request).execute().use { getResponse ->
                        return getResponse.isSuccessful
                    }
                }
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Check failed for $url: ${e.message}")
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
