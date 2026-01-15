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
import com.codesrahul.exclusivetv.showToast
import com.codesrahul.exclusivetv.SecureHttpClient
import com.codesrahul.exclusivetv.OrderPreferenceManager
import okhttp3.Request
import io.github.lizongying.Gua
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object TVList {
    private const val TAG = "TVList"
    const val FILE_NAME = "channels_test.txt"
    private lateinit var appDirectory: File
    private lateinit var serverUrl: String
    private lateinit var list: List<TV>
    var listModel: List<TVModel> = listOf()
    val groupModel = TVGroupModel()

    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int>
        get() = _importProgress

    fun init(context: Context) {
        _position.value = 0
        _importProgress.value = 0

        groupModel.addTVListModel(TVListModel("My Collection", 0))
        groupModel.addTVListModel(TVListModel("All channels", 1))

        appDirectory = context.filesDir
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
            // Loading form file should be fast, but ideally also async.
            // For now, keeping synchronous for init, but could be refactored.
            // Or just fire a fake 100% if needed, but probably not needed on startup for file read
            str2ListSync(str)
        } catch (e: Exception) {
            Log.e("", "error $e")
            file.deleteOnExit()
            Toast.makeText(context, "Failed to read the channel, please set it in the menu", Toast.LENGTH_LONG).show()
        }

        SP.configAutoLoad = true
        SP.config = "https://firetv-api-worker.technoholicrahul.workers.dev/"


        if (SP.configAutoLoad && !SP.config.isNullOrEmpty()) {
            SP.config?.let {
                update(it)
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

    private fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (size() == 0) {
                     _importProgress.value = 5
                } else {
                     _importProgress.value = 0
                }
            }
            try {
                Log.i(TAG, "request $serverUrl")
                // Use the custom unsafe client
                val client = unsafeClient
                val request = okhttp3.Request.Builder().url(serverUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    // Update progress to 60 (Server responded)
                     withContext(Dispatchers.Main) {
                        _importProgress.value = 60
                    }

                    val file = File(appDirectory, FILE_NAME)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    val str = response.body()!!.string()

                    // Process JSON in background
                    val success = str2List(str)

                    withContext(Dispatchers.Main) {
                        try {
                             if (success) {
                                file.writeText(str)
                                SP.config = serverUrl
                                "Channel imported successfully".showToast()
                                checkChannelsInBackground()

                                // Update progress to 100 (Done)
                                _importProgress.value = 100
                            } else {
                                "Channel import error: Invalid content".showToast()
                                _importProgress.value = 0 // Reset/Fail
                            }
                        } catch (e: Exception) {
                             Log.e(TAG, "Parsing error", e)
                             "Channel import error: ${e.message}".showToast()
                             _importProgress.value = 0 // Reset/Fail
                        }
                    }
                } else {
                    Log.e("", "request status ${response.code()}")
                    withContext(Dispatchers.Main) {
                        "Channel status error: ${response.code()}".showToast()
                    }
                }
            } catch (e: JsonSyntaxException) {
                Log.e("JSON Parse Error", e.toString())
                withContext(Dispatchers.Main) {
                    "Channel format error".showToast()
                }
            } catch (e: NullPointerException) {
                Log.e("Null Pointer Error", e.toString())
                 withContext(Dispatchers.Main) {
                    "Unable to read channel".showToast()
                 }
            } catch (e: Exception) {
                Log.e("", "request error $e")
                 withContext(Dispatchers.Main) {
                    "Channel request error: ${e.message}".showToast()
                 }
            }
        }
    }

    private fun update(serverUrl: String) {
        this.serverUrl = serverUrl
        update()
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
                            "Channel imported successfully".showToast(Toast.LENGTH_LONG)
                            checkChannelsInBackground()
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
            update(uri.toString())
        }
    }

    suspend fun str2List(str: String): Boolean = withContext(Dispatchers.Default) {
        var string = str.trim()
        val g = Gua()
        if (g.verify(string)) {
            Log.i(TAG, "Content verified with Gua")
            string = g.decode(string)
        } else {
             Log.i(TAG, "Content verification failed or not encrypted")
        }
        
        if (string.isBlank()) {
            Log.e(TAG, "Decrypted string is empty")
            return@withContext false
        }
        
        Log.i(TAG, "Decrypted content preview: ${string.take(100)}")

        // Try to find the start of the JSON array
        val startIndex = string.indexOf('[')
        if (startIndex != -1) {
             string = string.substring(startIndex)
             try {
                val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                // Use lenient Gson to handle malformed JSON
                list = com.google.gson.GsonBuilder().setLenient().create().fromJson(string, type)
                Log.i(TAG, "Import Channel ${list.size}")
            } catch (e: Exception) {
                Log.e(TAG, "parse error $string")
                Log.i(TAG, e.message, e)
                return@withContext false
            }
        } else {
            Log.e(TAG, "No JSON array start found")
            return@withContext false
        }

        refreshModels()
        return@withContext true
    }

    // Synchronous version for Init (called from Main thread or during init where blocking is acceptable/expected or needs refactoring)
    // Actually init calls it. Let's keep a sync version or make init suspend. Init is called in MainActivity.onCreate which is bad for suspend.
    // For now, duplicate the logic slightly or call runBlocking (bad).
    // Better: keep the original str2List as private helper, make public one suspend, and have a str2ListSync.
    
    private fun str2ListSync(str: String): Boolean {
          var string = str.trim()
        val g = Gua()
        if (g.verify(string)) {
            Log.i(TAG, "Content verified with Gua")
            string = g.decode(string)
        } else {
             Log.i(TAG, "Content verification failed or not encrypted")
        }
        
        if (string.isBlank()) {
            Log.e(TAG, "Decrypted string is empty")
            return false
        }

        val startIndex = string.indexOf('[')
        if (startIndex != -1) {
             string = string.substring(startIndex)
             try {
                val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                list = com.google.gson.GsonBuilder().setLenient().create().fromJson(string, type)
            } catch (e: Exception) {
                return false
            }
        } else {
            return false
        }

        // refreshModels is mostly safe depending on thread, but it touches groupModel.
        // If init is main thread, this is fine.
        kotlinx.coroutines.runBlocking {
            refreshModels()
        }
        return true
    }

    suspend fun refreshModels() = withContext(Dispatchers.Default) {
        if (!::list.isInitialized || list.isEmpty()) {
            Log.w(TAG, "Cannot refresh models: list not initialized or empty")
            return@withContext
        }

        // Preparation Phase (Background) - Work with TV objects, NOT TVModel
        val map: MutableMap<String, MutableList<TV>> = mutableMapOf()
        for (v in list) {
            if (v.group !in map) {
                map[v.group] = mutableListOf()
            }
            map[v.group]?.add(v)
        }

        // Apply saved category order
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

        // Prepare raw data structures for Main thread update
        // Triple<CategoryName, GroupIndex, List<TV>>
        val preparedGroups = mutableListOf<Triple<String, Int, List<TV>>>()
        
        var groupIndex = 2
        // We will assign IDs and build TVModels in the main thread to be safe, 
        // OR we can assign IDs here if 'id' in TV is just an Int and not LiveData.
        // TV.id is Int. So checks are fine.
        // But TVModel creation MUST be on Main.
        
        for (categoryName in sortedCategories) {
            val originalCategoryName = categoryName
            val displayCategoryName = categoryRenames[originalCategoryName] ?: originalCategoryName
            val channels = map[originalCategoryName] ?: continue
            
            // Apply saved channel order
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
            
            // Renaming can happen here safely on TV objects
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
            groupModel.clear()
            val listModelNew: MutableList<TVModel> = mutableListOf()
            var id = 0
            
            for ((name, idx, channels) in preparedGroups) {
                // TVListModel init calls _position.value which requires Main Thread
                val tvListModel = TVListModel(name, idx)
                val groupChannels = mutableListOf<TVModel>()

                for ((listIndex, tv) in channels.withIndex()) {
                     tv.id = id
                     // Instantiate TVModel here (Main Thread)
                     val tvModel = TVModel(tv)
                     tvModel.groupIndex = idx
                     tvModel.listIndex = listIndex
                     
                     groupChannels.add(tvModel)
                     listModelNew.add(tvModel)
                     id++
                }

                tvListModel.setTVListModel(groupChannels)
                groupModel.addTVListModel(tvListModel)
            }

            listModel = listModelNew

            // All channels
            groupModel.getTVListModel(1)?.setTVListModel(listModel)

            Log.i(TAG, "groupModel ${groupModel.size()}")
            
            groupModel.setChange()
        }
    }

    private fun checkChannelsInBackground() {
        // Temporarily disabled: Remove dead channels and automatic removal
        // because of this many working channels get removed.
        // Also app is getting crashed because of index changes.
        /*
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            if (!::list.isInitialized || list.isEmpty()) return@launch

            val initialSize = list.size
            Log.i(TAG, "Starting background channel check. Total: $initialSize")

            val validList = mutableListOf<TV>()
            var removedCount = 0

            // Create a copy to iterate
            val currentList = list.toList()

            for (tv in currentList) {
                var isAlive = false
                if (tv.uris.isEmpty()) {
                    isAlive = false // No URIs means dead? Or just empty? Assuming dead.
                } else {
                    for (uri in tv.uris) {
                        if (checkLink(uri)) {
                            isAlive = true
                            break // One working link is enough
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
                    refreshModels()
                    "$removedCount not working channels removed".showToast(Toast.LENGTH_LONG)
                }
                
                // Optional: Save the cleaned list to file?
                // The user request was "remove from the list", implying memory.
                // But for persistence, we might want to write it back.
                // Logic:
                // val gson = com.google.gson.Gson()
                // val json = gson.toJson(list)
                // val file = File(appDirectory, FILE_NAME)
                // file.writeText(json)
            } else {
                Log.i(TAG, "No dead channels found")
            }
        }
        */
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
        return getTVModel(position.value!!)
    }

    fun getTVModel(idx: Int): TVModel? {
        if (idx >= size()) {
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

        val tvModel = getTVModel(position)

        // set a new position or retry when position same
        tvModel!!.setReady()

        groupModel.setPosition(tvModel.groupIndex)

        SP.positionGroup = tvModel.groupIndex
        SP.position = position
        return true
    }

    fun size(): Int {
        return listModel.size
    }
}
