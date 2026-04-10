package com.codesrahul.exclusivetv

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codesrahul.exclusivetv.models.TVList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * SyncWorker handles background playlist updates.
 * Scheduled via WorkManager to ensure channels are fresh even if the app isn't active.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background playlist sync...")
            
            // Trigger a silent update
            // We use 'force = true' if we want to bypass the 15-min cooldown, 
            // but for a 6-hour background task, standard update is fine as it will 
            // naturally exceed the cooldown.
            TVList.update(applicationContext, silent = true, force = false)
            
            Log.d(TAG, "Background playlist sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed: ${e.message}")
            // Retry if it's a network failure (default backoff policy)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
