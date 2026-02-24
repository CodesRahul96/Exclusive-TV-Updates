package com.codesrahul.exclusivetv.requests

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ReleaseRequest {
    // Brand new vanilla OkHttpClient completely detached from SecureHttpClient.
    // No CertificatePinner, no SecurityInterceptor, no VPN checks.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    
    suspend fun getRelease(): ReleaseResponse? {
        return withContext(Dispatchers.IO) {
            fetchRelease(ApiClient.HOST, "Primary")
        }
    }

    private fun fetchRelease(baseUrl: String, sourceName: String): ReleaseResponse? {
        val url = if (baseUrl.endsWith("/")) {
            "${baseUrl}version.json"
        } else {
            "$baseUrl/version.json"
        }
        
        Log.d(TAG, "Fetching update from $sourceName: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
            
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    gson.fromJson(bodyString, ReleaseResponse::class.java)
                } else {
                    Log.e(TAG, "$sourceName update fetch failed with code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching from $sourceName: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ReleaseRequest"
    }
}
