package com.codesrahul.exclusivetv

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SyncManager handles bidirectional Firestore sync of user favorites and custom sources.
 * All Firestore calls are dispatched on the IO thread to avoid blocking the main thread.
 */
object SyncManager {
    private const val TAG = "SyncManager"
    private const val COLLECTION_USERS = "users"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_SOURCES = "sources"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_AUDIO_TRACKS = "audio_tracks"

    // Debounce: only one sync job at a time, cancels previous if new one comes
    private var syncJob: Job? = null
    @Volatile private var isRestoring = false

    fun syncUp() {
        val userId = SP.userId ?: return
        if (isRestoring) return

        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            delay(1000) // 1 second debounce
            try {
                val favorites = SP.favoriteUrls.toList()
                val sources = SP.playlistUrls.toList()
                
                // Collect all toggleable settings
                val settings = hashMapOf<String, Any>(
                    "channelReversal" to SP.channelReversal,
                    "channelNum" to SP.channelNum,
                    "time" to SP.time,
                    "bootStartup" to SP.bootStartup,
                    "repeatInfo" to SP.repeatInfo,
                    "bufferMode" to SP.bufferMode,
                    "epgEnabled" to SP.epgEnabled,
                    "showDateInInfo" to SP.showDateInInfo,
                    "watchLast" to SP.watchLast,
                    "forceHighQuality" to SP.forceHighQuality,
                    "pipMode" to SP.pipMode,
                    "audioStabilizer" to SP.audioStabilizer,
                    "channelCheck" to SP.channelCheck,
                    "lastChannelUrl" to SP.lastChannelUrl,
                    "lastChannelName" to SP.lastChannelName,
                    "watermarkEnabled" to SP.watermarkEnabled,
                    "watermarkOpacity" to SP.watermarkOpacity,
                    "watermarkPosition" to SP.watermarkPosition,
                    "epgShift" to SP.epgShift,
                    "sleepTimer" to SP.sleepTimer
                )

                // Store audio tracks as a JSON string inside settings to avoid Firestore map key issues (URLs contain '.', '/', '?')
                val rawTracks = SP.getAllAudioTracks()
                val audioTracksJson = buildString {
                    append("{")
                    rawTracks.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) append(",")
                        // Escape key for JSON safety
                        val escapedKey = k.replace("\\", "\\\\").replace("\"", "\\\"")
                        append("\"$escapedKey\":$v")
                    }
                    append("}")
                }
                settings["audio_tracks_json"] = audioTracksJson

                val data = hashMapOf(
                    KEY_FAVORITES to favorites,
                    KEY_SOURCES to sources,
                    KEY_SETTINGS to settings
                )

                FirebaseFirestore.getInstance()
                    .collection(COLLECTION_USERS)
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.i(TAG, "Cloud Sync Up successful.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Cloud Sync Up failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "syncUp exception: ${e.message}")
            }
        }
    }

    fun syncDown(onComplete: (() -> Unit)? = null) {
        val userId = SP.userId ?: run {
            onComplete?.invoke()
            return
        }

        isRestoring = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection(COLLECTION_USERS)
                    .document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            @Suppress("UNCHECKED_CAST")
                            val favorites = document.get(KEY_FAVORITES) as? List<String>
                            @Suppress("UNCHECKED_CAST")
                            val sources = document.get(KEY_SOURCES) as? List<String>
                            @Suppress("UNCHECKED_CAST")
                            val audioTracks = document.get(KEY_AUDIO_TRACKS) as? Map<String, Long>
                            @Suppress("UNCHECKED_CAST")
                            val settings = document.get(KEY_SETTINGS) as? Map<String, Any>

                            if (favorites != null) SP.favoriteUrls = favorites.toSet()
                            if (sources != null) SP.playlistUrls = sources.toSet()
                            
                            settings?.let { s ->
                                // Restore audio tracks from JSON string stored inside settings
                                (s["audio_tracks_json"] as? String)?.let { json ->
                                    try {
                                        val result = mutableMapOf<String, Int>()
                                        val inner = json.trim().removeSurrounding("{", "}")
                                        if (inner.isNotEmpty()) {
                                            // Parse simple {"key":value,...} JSON manually to avoid dependency
                                            val regex = Regex("\"((?:[^\"\\\\]|\\\\.)*?)\":(\\d+)")
                                            regex.findAll(inner).forEach { match ->
                                                val key = match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
                                                val value = match.groupValues[2].toIntOrNull() ?: -1
                                                if (value != -1) result[key] = value
                                            }
                                        }
                                        if (result.isNotEmpty()) SP.setAllAudioTracks(result)
                                        Log.i(TAG, "Audio tracks restored: ${result.size} tracks")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to parse audio_tracks_json: ${e.message}")
                                    }
                                }

                                (s["channelReversal"] as? Boolean)?.let { SP.channelReversal = it }
                                (s["channelNum"] as? Boolean)?.let { SP.channelNum = it }
                                (s["time"] as? Boolean)?.let { SP.time = it }
                                (s["bootStartup"] as? Boolean)?.let { SP.bootStartup = it }
                                (s["repeatInfo"] as? Boolean)?.let { SP.repeatInfo = it }
                                (s["bufferMode"] as? Long)?.let { SP.bufferMode = it.toInt() }
                                (s["epgEnabled"] as? Boolean)?.let { SP.epgEnabled = it }
                                (s["showDateInInfo"] as? Boolean)?.let { SP.showDateInInfo = it }
                                (s["watchLast"] as? Boolean)?.let { SP.watchLast = it }
                                (s["forceHighQuality"] as? Boolean)?.let { SP.forceHighQuality = it }
                                (s["pipMode"] as? Boolean)?.let { SP.pipMode = it }
                                (s["audioStabilizer"] as? Boolean)?.let { SP.audioStabilizer = it }
                                (s["channelCheck"] as? Boolean)?.let { SP.channelCheck = it }
                                (s["lastChannelUrl"] as? String)?.let { SP.lastChannelUrl = it }
                                (s["lastChannelName"] as? String)?.let { SP.lastChannelName = it }
                                (s["watermarkEnabled"] as? Boolean)?.let { SP.watermarkEnabled = it }
                                (s["watermarkOpacity"] as? Long)?.let { SP.watermarkOpacity = it.toInt() }
                                (s["watermarkPosition"] as? String)?.let { SP.watermarkPosition = it }
                                (s["epgShift"] as? Long)?.let { SP.epgShift = it.toInt() }
                                (s["sleepTimer"] as? Long)?.let { SP.sleepTimer = it.toInt() }
                            }

                            Log.i(TAG, "Cloud Sync Down successful.")
                        }
                        isRestoring = false
                        onComplete?.invoke()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Cloud Sync Down failed: ${e.message}")
                        isRestoring = false
                        onComplete?.invoke()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "syncDown exception: ${e.message}")
                isRestoring = false
                onComplete?.invoke()
            }
        }
    }

    // Call this when a favorite is toggled
    fun pushFavoriteChange() {
        syncUp()
    }
}
