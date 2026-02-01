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
                return@withContext release
            }
            
            // If primary fails, try fallback
            release = fetchRelease(releaseServiceFallback, "Fallback")
            
            if (release != null) {
                usedFallback = true
                return@withContext release
            }
            
            // Both sources failed
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
                            continuation.resume(response.body())
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(call: Call<ReleaseResponse>, t: Throwable) {
                        continuation.resume(null)
                    }
                })
        }
    }

    companion object {
        private const val TAG = "ReleaseRequest"
    }
}
