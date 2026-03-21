package com.codesrahul.exclusivetv

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.tasks.await

/**
 * IntegrityManager handles requests to the Google Play Integrity API.
 * This provides a cryptographically signed token that proves the device's 
 * integrity and that the app is genuine.
 */
object IntegrityManager {
    private const val TAG = "IntegrityManager"
    
    // Cloud project number from the Google Cloud Console for the Firebase project
    // Note: In a production app, this should be fetched from remote config or BuildConfig
    private const val CLOUD_PROJECT_NUMBER = 0L // Placeholder

    suspend fun getIntegrityToken(context: Context): String? {
        if (CLOUD_PROJECT_NUMBER == 0L) return "test_token_no_project_id"

        val integrityManager = IntegrityManagerFactory.create(context)
        
        return try {
            val integrityTokenResponse = integrityManager.requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .build()
            ).await()
            
            integrityTokenResponse.token()
        } catch (e: Exception) {
            Log.e(TAG, "Integrity token request failed: ${e.message}")
            null
        }
    }
}
