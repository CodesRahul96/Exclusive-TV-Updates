package com.codesrahul.exclusivetv

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    // Debounce flag: only one sync pending at a time
    @Volatile private var pendingSync = false

    fun syncUp() {
        val userId = SP.userId ?: return

        // Debounce: if a sync is already queued, skip duplicate calls
        if (pendingSync) return
        pendingSync = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val favorites = SP.favoriteUrls.toList()
                val sources = SP.playlistUrls.toList()

                val data = hashMapOf(
                    KEY_FAVORITES to favorites,
                    KEY_SOURCES to sources
                )

                FirebaseFirestore.getInstance()
                    .collection(COLLECTION_USERS)
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.i(TAG, "Cloud Sync Up successful. Favorites: ${favorites.size}, Sources: ${sources.size}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Cloud Sync Up failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "syncUp exception: ${e.message}")
            } finally {
                pendingSync = false
            }
        }
    }

    fun syncDown(onComplete: (() -> Unit)? = null) {
        val userId = SP.userId ?: run {
            onComplete?.invoke()
            return
        }

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

                            if (favorites != null) {
                                SP.favoriteUrls = favorites.toSet()
                            }
                            if (sources != null) {
                                SP.playlistUrls = sources.toSet()
                            }

                            Log.i(TAG, "Cloud Sync Down successful. Favorites: ${favorites?.size}, Sources: ${sources?.size}")
                        }
                        onComplete?.invoke()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Cloud Sync Down failed: ${e.message}")
                        onComplete?.invoke()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "syncDown exception: ${e.message}")
                onComplete?.invoke()
            }
        }
    }

    // Call this when a favorite is toggled
    fun pushFavoriteChange() {
        syncUp()
    }
}
