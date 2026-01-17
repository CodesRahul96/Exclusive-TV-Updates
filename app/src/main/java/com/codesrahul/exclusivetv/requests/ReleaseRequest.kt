package com.codesrahul.exclusivetv.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ReleaseRequest {
    private var apiClient = ApiClient()
    private var releaseService = apiClient.releaseService
    private var releaseServiceFallback = apiClient.releaseServiceFallback
    
    // Track which source was successful for download URL consistency
    var usedFallback = false
        private set

    suspend fun getRelease(): ReleaseResponse? {
        return withContext(Dispatchers.IO) {
            // Try primary source first
            var release = fetchRelease(releaseService, "Primary")
            
            if (release != null) {
                usedFallback = false
                android.util.Log.i(TAG, "Successfully fetched from PRIMARY source")
                return@withContext release
            }
            
            // If primary fails, try fallback
            android.util.Log.w(TAG, "Primary source failed, trying FALLBACK source...")
            release = fetchRelease(releaseServiceFallback, "Fallback")
            
            if (release != null) {
                usedFallback = true
                android.util.Log.i(TAG, "Successfully fetched from FALLBACK source")
                return@withContext release
            }
            
            // Both sources failed
            android.util.Log.e(TAG, "Both PRIMARY and FALLBACK sources failed")
            null
        }
    }

    private suspend fun fetchRelease(service: ReleaseService, sourceName: String): ReleaseResponse? {
        return suspendCoroutine { continuation ->
            service.getRelease()
                .enqueue(object : Callback<ReleaseResponse> {
                    override fun onResponse(
                        call: Call<ReleaseResponse>,
                        response: Response<ReleaseResponse>
                    ) {
                        if (response.isSuccessful) {
                            android.util.Log.i(TAG, "$sourceName source: Success")
                            continuation.resume(response.body())
                        } else {
                            android.util.Log.e(TAG, "$sourceName source: Error ${response.code()} ${response.errorBody()?.string()}")
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(call: Call<ReleaseResponse>, t: Throwable) {
                        android.util.Log.e(TAG, "$sourceName source: Network failure - ${t.message}")
                        continuation.resume(null)
                    }
                })
        }
    }

    companion object {
        private const val TAG = "ReleaseRequest"
    }
}
