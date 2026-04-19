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
import com.codesrahul.exclusivetv.SubscriptionManager
import java.io.BufferedReader
import java.io.StringReader
import java.io.Reader
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.stream.JsonWriter

object TVList {
    fun clear(context: Context) {
        list = emptyList()
        listModel = emptyList()
        sourceCache.clear()
        lastListHash = 0
        
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
            // Clear Channel Cache File
            if (::appDirectory.isInitialized) {
                File(appDirectory, FILE_NAME).delete()
            }
        } catch (e: Exception) {
        }
    }
    fun updatePlaybackModel(model: TVModel?) {
        _currentPlayingModel.postValue(model)
    }

    private const val TAG = "TVList"
    const val FILE_NAME = "channels.txt"
    private const val UPDATE_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes TTL
    private lateinit var appDirectory: File

    private var serverUrl: String = ""
    private var list: List<TV> = emptyList()

    fun removeChannelsBySource(url: String) {
        list = list.filter { it.sourceUrl != url }
    }

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

    private val _currentPlayingModel = MutableLiveData<TVModel?>()
    val currentPlayingModel: LiveData<TVModel?> get() = _currentPlayingModel


    fun notifyLikeChanged(model: TVModel, liked: Boolean) {
        _likeChangedEvent.postValue(Pair(model, liked))
    }

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int>
        get() = _importProgress
        
    private val _lastUpdatedTimeStr = MutableLiveData<String>()
    val lastUpdatedTimeStr: LiveData<String>
        get() = _lastUpdatedTimeStr

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
                    val isPremium = "Premium".equals(SubscriptionManager.planName, ignoreCase = true)
                    
                    // SECURITY: If Standard user, don't even load a "fat" cache file 
                    // which might be left over from a previous Premium session/leak
                    // INCREASED LIMIT: 50 KB was too small for M3U, increased to 500 KB (safe for ~2000 channels)
                    if (!isPremium && file.length() > 500 * 1024) { 
                         file.delete()
                    }

                    // CACHE PURGE: Check for polluted "JioStar" or "iptv" content from previous fallback
                    if (file.exists()) {
                         try {
                             // Read first 2KB to check for signatures
                             val header = file.readText().take(2048)
                             if (header.contains("JioStar", ignoreCase = true) || 
                                 header.contains("iptv", ignoreCase = true) ||
                                 header.contains("scraper", ignoreCase = true)) {
                                 file.delete()
                             }
                         } catch (e: Exception) {}
                    }
                    
                    if (file.exists()) {
                        val result = parseUniversalFile(file)
                        if (result.isNotEmpty()) {
                            list = result
                            // PRE-POPULATE SOURCE CACHE: Group existing channels by their source URL
                            result.groupBy { it.sourceUrl }.forEach { (url, channels) ->
                                if (url != null) sourceCache[url] = channels
                            }
                            refreshModels(MyTVApplication.getInstance())
                        }
                    }
                }
                
                if (list.isEmpty()) {
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

            // Initial config setup (Removed DEFAULT_CONFIG_URL fallback)
            
            // Clean up tiered configs if they point to dead URLs
            if (SP.standardConfig?.contains("rebroadcast.indevs.in") == true) {
                SP.standardConfig = "" 
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

    private val sourceCache = ConcurrentHashMap<String, List<TV>>()

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
                
                // --- LOAD BALANCING SAFEGUARDS ---
                val now = System.currentTimeMillis()
                
                // 1. Update Cooldown (Prevent redundant hits if not forced)
                if (!force && (now - SP.lastUpdateTime < UPDATE_COOLDOWN_MS) && list.isNotEmpty()) {
                    return@launch
                }

                // 2. Thundering Herd Protection (Silent/Startup Jitter)
                if (silent && !force) {
                    val jitter = (0..5000).random().toLong()
                    kotlinx.coroutines.delay(jitter)
                }
                
                val showUi = !silent || size() == 0
                
                try {
                
                
                isUpdating = true
                
                // --- STEP 1: RESOLVE PLAN & URLs ---
                val plan = SubscriptionManager.planName
                val isPremium = "Premium".equals(plan, ignoreCase = true)
                
                val sCfg = SP.standardConfig
                val pCfg = SP.premiumConfig
                val standardUrl = if (sCfg.isNullOrEmpty()) SecretManager.getStandardApiUrl() else sCfg
                val premiumUrl = if (pCfg.isNullOrEmpty()) SecretManager.getPremiumApiUrl() else pCfg
                
                val urls = mutableListOf<String>()

                val allPlaylistUrls = SP.playlistUrls

                // [NEW] EXCLUSIVE SOURCE MODE
                // Check if user has added custom sources (excluding the system URLs and dead legacy URLs)
                val customUrls = allPlaylistUrls.filter { url ->
                    url.isNotEmpty() && 
                    url != standardUrl && 
                    url != premiumUrl
                }

                // DEFAULT MODE: Load System APIs
                if (isPremium) {
                    // PREMIUM PRIORITY STACK
                    if (premiumUrl.isNotEmpty()) {
                        urls.add(premiumUrl)
                    }
                    if (standardUrl.isNotEmpty() && standardUrl != premiumUrl) {
                        urls.add(standardUrl)
                    }
                } else {
                    // STANDARD PRIORITY STACK
                    if (standardUrl.isNotEmpty()) {
                        urls.add(standardUrl)
                    }
                }
                
                // Add custom user sources
                if (customUrls.isNotEmpty()) {
                    urls.addAll(customUrls)
                }

                
                withContext(Dispatchers.Main) {
                    if (showUi) {
                         _importProgress.value = 5
                         _importStatus.value = "Initializing Experience..."
                    } else {
                         _importProgress.value = 0
                         _importStatus.value = "Checking for improvements..."
                    }
                }
                
                val client = SecureHttpClient.client
                val allChannels = mutableListOf<TV>()
                
                // Fetch all playlists concurrently
                val totalSources = urls.size
                var completedSources = 0

                val deferredResults = urls.mapIndexed { index, url ->
                    async {
                        try {
                           if (showUi) {
                               withContext(Dispatchers.Main) {
                                   _importStatus.value = "Connecting to Live Channels..."
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
                                        if (showUi) _importStatus.value = "Optimizing Stream Quality..."
                                   }
                                   // RETURN CACHE: Retrieve previous channels for this specific URL
                                   return@async sourceCache[url] ?: emptyList<TV>()
                               }
                               
                               if (response.isSuccessful) {
                                   withContext(Dispatchers.Main) {
                                       if (!silent || size() == 0) {
                                           _importStatus.value = "Updating Channels..."
                                       }
                                   }
                                   
                                   // HTML Detection (Prevention of caching landing/error pages)
                                   val contentType = response.header("Content-Type") ?: ""
                                   if (contentType.contains("text/html", ignoreCase = true)) {
                                       withContext(Dispatchers.Main) {
                                           if (showUi) Toast.makeText(ctx, "Source ${index + 1} returned HTML (redirect/dead). Skipped.", Toast.LENGTH_SHORT).show()
                                       }
                                       return@async sourceCache[url] ?: emptyList<TV>()
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
                                            channels.forEach { it.sourceUrl = url }
                                           
                                            // DEBUG LOGGING FOR STANDARD API ISSUES
                                            if (channels.isEmpty()) {
                                                try {
                                                     val preview = tempFile.readText().take(250)
                                                } catch (e: Exception) {}
                                            }

                                           // Verify if it's actually data or a tiny HTML file missed by headers
                                           if (channels.isEmpty() && tempFile.length() < 5000) {
                                               val sample = tempFile.readText(Charsets.UTF_8).take(200).lowercase()
                                               if (sample.contains("<!doctype html") || sample.contains("<html")) {
                                                   withContext(Dispatchers.Main) {
                                                       if (showUi) Toast.makeText(ctx, "Source ${index + 1} contains HTML. Check API.", Toast.LENGTH_SHORT).show()
                                                   }
                                                   return@async sourceCache[url] ?: emptyList<TV>()
                                               }
                                           }

                                           // UPDATE CACHE
                                           sourceCache[url] = channels
                                           
                                           withContext(Dispatchers.Main) {
                                               if (showUi) _importStatus.value = "Curating Channels..."
                                           }
                                           return@async channels
                                           
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            return@async sourceCache[url] ?: emptyList<TV>()
                                        } finally {
                                            tempFile.delete() 
                                        }
                                   } else {
                                       return@async sourceCache[url] ?: emptyList<TV>()
                                   }
                               } else {
                                   withContext(Dispatchers.Main) {
                                       if (showUi) Toast.makeText(ctx, "Failed to connect to Source ${index + 1} (Error ${response.code})", Toast.LENGTH_SHORT).show()
                                   }
                                   return@async sourceCache[url] ?: emptyList<TV>()
                               }
                           }
                        } catch (e: Exception) {
                           e.printStackTrace()
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
                     
                      // SMART DEDUPLICATION: 
                      // 1. Group by Title to collect backup URLs.
                      // 2. Then ensure unique URLs to prevent same stream appearing multiple times.
                      val titleMap = LinkedHashMap<String, TV>()
                      val seenUrls = mutableSetOf<String>()
                      
                      allChannels.forEach { tv ->
                          val titleKey = tv.title.lowercase().trim()
                          val primaryUrl = tv.uris.firstOrNull() ?: ""
                          
                          if (primaryUrl.isEmpty()) return@forEach
                          
                          if (titleMap.containsKey(titleKey)) {
                              val existing = titleMap[titleKey]!!
                              // Merge missing technical metadata if existing is empty
                              if (existing.logo.isEmpty() && tv.logo.isNotEmpty()) existing.logo = tv.logo
                              if (existing.language.isNullOrEmpty() && !tv.language.isNullOrEmpty()) existing.language = tv.language
                              if (existing.country.isNullOrEmpty() && !tv.country.isNullOrEmpty()) existing.country = tv.country
                              if (existing.resolution.isNullOrEmpty() && !tv.resolution.isNullOrEmpty()) existing.resolution = tv.resolution
                              if (existing.bitrate.isNullOrEmpty() && !tv.bitrate.isNullOrEmpty()) existing.bitrate = tv.bitrate
                              if (existing.frameRate.isNullOrEmpty() && !tv.frameRate.isNullOrEmpty()) existing.frameRate = tv.frameRate
                              if (existing.videoCodec.isNullOrEmpty() && !tv.videoCodec.isNullOrEmpty()) existing.videoCodec = tv.videoCodec
                              if (existing.genre.isNullOrEmpty() && !tv.genre.isNullOrEmpty()) existing.genre = tv.genre
                              
                              // Collect unique backup URLs
                              val combinedUris = (existing.uris + tv.uris).distinct()
                              existing.uris = combinedUris
                          } else {
                              titleMap[titleKey] = tv
                          }
                      }
                      
                      // Now Filter for Unique Primary URLs across all titled channels
                      val finalChannelsList = mutableListOf<TV>()
                      titleMap.values.forEach { tv ->
                          val primaryUrl = tv.uris.firstOrNull() ?: ""
                          if (primaryUrl.isNotEmpty() && !seenUrls.contains(primaryUrl)) {
                              finalChannelsList.add(tv)
                              seenUrls.add(primaryUrl)
                          }
                      }
                      
                      val finalChannels = finalChannelsList.toMutableList()
                      
                      // FIX: Re-index securely after merging
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
                              
                              // EPG SAFETY CHECK: Fetch EPG even if channels haven't changed!
                              // This is crucial if user just toggled EPG on from settings.
                              if (SP.epgEnabled) {
                                  EPGManager.init(ctx)
                                  CoroutineScope(Dispatchers.IO).launch {
                                      try {
                                          if (showUi) {
                                              withContext(Dispatchers.Main) { _importStatus.value = "Updating Guide..." }
                                          }
                                          EPGManager.fetchEPG(force = false)
                                          withContext(Dispatchers.Main) {
                                              listModel.forEach { it.updateEPG() }
                                              if (showUi) _importStatus.value = ""
                                          }
                                      } catch (e: Exception) {
                                          if (showUi) withContext(Dispatchers.Main) { _importStatus.value = "" }
                                      }
                                  }
                              }
                          }
                          return@launch
                      }
                      lastListHash = newListHash
                      SP.lastUpdateTime = System.currentTimeMillis() // Update success timestamp
                      
                      val dateFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                      _lastUpdatedTimeStr.postValue("Last sync: ${dateFormat.format(java.util.Date())}")

                      // ATOMIC SAVE: Write to temp file first then rename to prevent corruption on crash
                      val finalFile = File(ctx.filesDir, FILE_NAME)
                      val tempFile = File(ctx.filesDir, "$FILE_NAME.tmp")
                      
                      try {
                          val writer = JsonWriter(FileWriter(tempFile))
                          writer.setIndent("  ")
                          val gson = com.google.gson.Gson()
                          gson.toJson(finalChannels, finalChannels::class.java, writer)
                          writer.close()
                          
                          // Atomic rename
                          if (tempFile.renameTo(finalFile)) {
                              // Cache saved successfully
                          } else {
                          }
                      } catch (e: Exception) {
                      } finally {
                          if (tempFile.exists()) tempFile.delete() // Cleanup if rename failed
                      }
                      
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
                          
                          if (!silent) {
                               "Channels Updated: ${list.size}".showToast()
                          }
                          
                          // [PROFESSIONAL] Defer EPG loading using Coroutines
                          if (SP.epgEnabled) {
                              CoroutineScope(Dispatchers.IO).launch {
                                  kotlinx.coroutines.delay(30000)
                                  EPGManager.init(ctx)
                                  try {
                                      withContext(Dispatchers.Main) { 
                                          if (showUi) _importStatus.value = "Updating Guide..." 
                                      } 
                                      EPGManager.fetchEPG(force = false)
                                      withContext(Dispatchers.Main) {
                                          listModel.forEach { it.updateEPG() }
                                      }
                                  } catch (e: Exception) {
                                  } finally {
                                      withContext(Dispatchers.Main) {
                                          // Fix: Clear status so loading spinner doesn't get stuck
                                          if (showUi) _importStatus.value = ""
                                      }
                                  }
                              }
                          }
                      }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!silent) "Failed to update channels, loading offline cache...".showToast()
                        _importProgress.value = 0
                        _importStatus.value = "Offline Mode"
                        
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
        val pushbackReader = java.io.PushbackReader(reader, 1024)
        
        try {
            var firstChar = -1
            while (true) {
                val c = pushbackReader.read()
                if (c == -1) break
                if (!Character.isWhitespace(c)) {
                    firstChar = c
                    pushbackReader.unread(c)
                    break
                }
            }
            
            return when {
                firstChar == '{'.code || firstChar == '['.code -> {
                    DeepHeuristicParser.parse(pushbackReader)
                }
                firstChar == '#'.code -> {
                    M3UParser.parse(BufferedReader(pushbackReader))
                }
                firstChar >= 0x4D00 && firstChar <= 0x4DFF -> {
                    // Encrypted/Gua
                    val remaining = BufferedReader(pushbackReader).readText()
                    val g = Gua()
                    val decoded = if (g.verify(remaining)) g.decode(remaining) else remaining
                    val secretKey = SecretManager.getAppKey()
                    val finalContent = SecurityUtil.decryptChannelData(decoded, secretKey)
                    parseUniversal(finalContent)
                }
                else -> {
                    // Peek deeper to see if it's an M3U starting with comments or other tags
                    var isM3UFound = false
                    try {
                        val buffer = CharArray(1024)
                        val read = pushbackReader.read(buffer)
                        if (read > 0) {
                            val peek = String(buffer, 0, read)
                            if (peek.contains("#EXTINF") || peek.contains("#KODIPROP") || peek.contains("#EXTVLCOPT")) {
                                isM3UFound = true
                            }
                            pushbackReader.unread(buffer, 0, read)
                        }
                    } catch (e: Exception) {}

                    if (isM3UFound) {
                        M3UParser.parse(BufferedReader(pushbackReader))
                    } else {
                        // Fallback: Try Heuristic on raw text
                        DeepHeuristicParser.parse(pushbackReader)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Universal Parsing Error", e)
        }
        
        return emptyList()
    }

    private fun parseUniversal(content: String): List<TV> {
        val string = content.trim()
        if (string.isBlank()) return emptyList()

        return when {
            string.startsWith("{") || string.startsWith("[") -> {
                DeepHeuristicParser.parse(string)
            }
            string.contains("#EXTM3U") || string.contains("#EXTINF") || 
            string.contains("#KODIPROP") || string.contains("#EXTVLCOPT") -> {
                M3UParser.parse(BufferedReader(StringReader(string)))
            }
            else -> {
                // Secondary check: Gua/Encrypted
                if (string.isNotEmpty() && string[0].code >= 0x4D00 && string[0].code <= 0x4DFF) {
                    val g = Gua()
                    val decoded = if (g.verify(string)) g.decode(string) else string
                    val secretKey = SecretManager.getAppKey()
                    val decrypted = SecurityUtil.decryptChannelData(decoded, secretKey)
                    parseUniversal(decrypted)
                } else if (string.contains("[playlist]", ignoreCase = true)) {
                    PlsParser.parse(string)
                } else {
                    // Final Heuristic Attempt
                    DeepHeuristicParser.parse(string)
                }
            }
        }
    }

    private suspend fun parseUniversalFile(file: File): List<TV> = withContext(Dispatchers.IO) {
        // OPTIMIZATION: Direct Stream Parsing for Cache (Avoids reading 50MB string into memory)
        val filename = file.name
        
        // 1. FASTEST PATH: If it's the internal cache file OR ends with .json
        // OR starts with JSON chars (Heuristic)
        var isJson = filename == FILE_NAME || filename.endsWith(".json")
        
        if (!isJson) {
             // Quick Peek for JSON char to support .tmp files from network
             try {
                 val reader = java.io.FileReader(file)
                 val first = reader.read()
                 reader.close()
                 if (first == '{'.code || first == '['.code) {
                     isJson = true
                 }
             } catch(e: Exception) {}
        }

        if (isJson) {
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
        if (peekContent.isNotEmpty() && (peekContent[0].code >= 0x4D00 && peekContent[0].code <= 0x4DFF)) {
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
    private suspend fun expandNestedPlaylists(originalList: List<TV>, depth: Int = 0, processedUrls: MutableSet<String> = mutableSetOf()): List<TV> = withContext(Dispatchers.IO) {
        if (depth > 3 || originalList.size > 2000) return@withContext originalList
        
        val expandedList = mutableListOf<TV>()
        val client = SecureHttpClient.client
        
        for (tv in originalList) {
            val url = tv.uris.firstOrNull() ?: continue
            
            // Smater logic: check if this is a playlist link, not a stream
            val isPlaylist = url.endsWith(".m3u") || url.endsWith(".m3u8") || 
                           url.endsWith(".json") || url.endsWith(".txt") ||
                           url.contains("playlist") || url.contains("get.php")
            
            // Streams usually have certain keywords or multiple segments
            val isLikelyStream = url.contains(".ts") || url.contains("/hls/") || url.contains(".mpd") || url.contains(".m3u8/")

            if (isPlaylist && !isLikelyStream && !processedUrls.contains(url)) {
                processedUrls.add(url)
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyText = response.body?.string() ?: ""
                            val nestedChannels = parseUniversal(bodyText)
                            if (nestedChannels.isNotEmpty()) {
                                // RECURSION: Smater than TiviMate (recursive merging)
                                val deepChannels = expandNestedPlaylists(nestedChannels, depth + 1, processedUrls)
                                expandedList.addAll(deepChannels)
                            } else {
                                expandedList.add(tv)
                            }
                        } else {
                            expandedList.add(tv)
                        }
                    }
                } catch (e: Exception) {
                    expandedList.add(tv)
                }
            } else {
                expandedList.add(tv)
            }
        }
        return@withContext expandedList
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
        if (list.isEmpty()) {
            return
        }

        val isPremium = "Premium".equals(SubscriptionManager.planName, ignoreCase = true)
        
        // HARD SECURITY FILTER: Ensure Standard users never see a Premium-sized list
        if (!isPremium && list.size > 300) {
             list = list.take(280) // Truncate to a safe standard size
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

            // 2. Pre-process Update Phase (Offload to Default Dispatcher)
            // Capture snapshot of current models to map old instances
            val oldIdToModel = listModel.associateBy { it.tv.uris.firstOrNull() ?: "" }
            val currentGroupModels = groupModel.getTVListModelList()
            val oldGroupMap = currentGroupModels.associateBy { it.getOriginalName() }

            val (newList, newGroupList, likedChannels) = withContext(Dispatchers.Default) {
                val listModelNew = mutableListOf<TVModel>()
                val groupListNew = mutableListOf<TVListModel>()
                
                var id = 0

                // Preserve/Create Special Groups
                val collectionGroup = oldGroupMap["My Collection"] ?: TVListModel("My Collection", "My Collection", 0)
                val allChannelsGroup = oldGroupMap["All channels"] ?: TVListModel("All channels", "All channels", 1)
                
                groupListNew.add(collectionGroup)
                groupListNew.add(allChannelsGroup)

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
                         // Reuse existing TVModel if available (preserves state) or create new
                         val tvModel = oldIdToModel[tv.uris.firstOrNull() ?: ""]?.apply { update(tv) } ?: TVModel(tv)
                         tvModel.groupIndex = group.index
                         tvModel.listIndex = groupChannels.size
                         
                         groupChannels.add(tvModel)
                         listModelNew.add(tvModel)
                         id++
                    }

                    if (tvListModel != null) {
                        tvListModel.setTVListModel(groupChannels)
                        // ENSURE INDEX SYNC: Sync category index with its actual position in the new list
                        val targetIndex = groupListNew.size
                        tvListModel.updateMetadata(group.displayName, targetIndex)
                        groupChannels.forEach { it.groupIndex = targetIndex }
                        
                        groupListNew.add(tvListModel)
                    }
                }
                
                // [FIX] HANG PREVENTION: Filter Liked channels in background thread
                val likedChannels = listModelNew.filter { it.like.value == true || SP.getLike(it.tv.id) }.toMutableList()
                
                // Return all prepared data
                Triple(listModelNew, groupListNew, likedChannels)
            }

            // 3. Final Commit (Main Thread)
            withContext(Dispatchers.Main) {
                val collectionGroup = newGroupList[0] 
                val allChannelsGroup = newGroupList[1] 

                collectionGroup.setTVListModel(likedChannels)

                listModel = newList
                groupModel.setTVListModelList(newGroupList)
                allChannelsGroup.setTVListModel(listModel)
                groupModel.setChange()
                
                if (SP.epgEnabled) {
                    EPGManager.init(ctx)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            EPGManager.fetchEPG(force = false)
                            listModel.forEach { it.updateEPG() }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun checkChannelsInBackground() {
        if (!SP.channelCheck) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (list.isEmpty()) return@launch

                val initialSize = list.size

                // Use concurrent collection for thread safety if needed, 
                // but map + filter is safer.
                val currentList = list.toList()
                
                // Limit concurrency to reduce CPU/IO pressure (Lag prevention)
                val semaphore = kotlinx.coroutines.sync.Semaphore(10)
                
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
                        EPGManager.fetchEPG()
                        listModel.forEach { it.updateEPG() }
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
            val checkClient = SecureHttpClient.client.newBuilder()
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

    private fun addUrlToList(url: String, urls: MutableList<String>) {
        if (url.isEmpty()) return
        
        // Handle folder/legacy style URLs if they contain commas
        if (url.contains(",")) {
            val parts = url.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            urls.addAll(parts)
        } else {
            urls.add(url)
        }
    }

    fun restorePosition(): Int {
        val savedUrl = SP.lastChannelUrl
        val savedName = SP.lastChannelName
        val savedPos = SP.position
        
        if (savedUrl.isNotEmpty() || savedName.isNotEmpty()) {
            // Priority 1: Strict Match (URL + Name)
            if (savedUrl.isNotEmpty() && savedName.isNotEmpty()) {
                 val strictIndex = listModel.indexOfFirst { model ->
                     model.tv.name == savedName && model.tv.uris.any { it == savedUrl }
                 }
                 if (strictIndex != -1) return strictIndex
            }

            // Priority 2: URL Match
            if (savedUrl.isNotEmpty()) {
                val index = listModel.indexOfFirst { model ->
                    model.tv.uris.any { it == savedUrl }
                }
                if (index != -1) return index
            }

            // Priority 3: Name Match (handles transient URL changes)
            if (savedName.isNotEmpty()) {
                val index = listModel.indexOfFirst { model ->
                    model.tv.name == savedName
                }
                if (index != -1) return index
            }
        }
        
        // Priority 4: Fallback to saved numerical position if valid
        if (savedPos >= 0 && savedPos < listModel.size) {
            return savedPos
        }
        
        // Final Fallback: Always return 0 to ensure something plays
        return 0
    }
}
