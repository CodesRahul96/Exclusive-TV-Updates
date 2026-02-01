package com.codesrahul.exclusivetv.requests

import com.codesrahul.exclusivetv.SecureHttpClient
import com.codesrahul.exclusivetv.UnsafeHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient {
    private var okHttpClient = UnsafeHttpClient.client

    val releaseService: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(HOST))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ReleaseService::class.java)
    }

    val releaseServiceFallback: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(HOST_FALLBACK))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ReleaseService::class.java)
    }

    companion object {
        private const val DEFAULT_HOST = "https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-APP/"
        private const val DEFAULT_DOWNLOAD_HOST = "https://github.com/CodesRahul96/Exclusive-TV-APP/releases/download/"
        
        private const val DEFAULT_HOST_FALLBACK = "https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-Updates/"
        private const val DEFAULT_DOWNLOAD_HOST_FALLBACK = "https://github.com/CodesRahul96/Exclusive-TV-Updates/releases/download/"

        val HOST: String get() = com.codesrahul.exclusivetv.SP.apiHost.takeIf { it.isNotEmpty() } ?: DEFAULT_HOST
        val DOWNLOAD_HOST: String get() = com.codesrahul.exclusivetv.SP.apiDownloadHost.takeIf { it.isNotEmpty() } ?: DEFAULT_DOWNLOAD_HOST
        
        val HOST_FALLBACK: String get() = com.codesrahul.exclusivetv.SP.apiHostFallback.takeIf { it.isNotEmpty() } ?: DEFAULT_HOST_FALLBACK
        val DOWNLOAD_HOST_FALLBACK: String get() = com.codesrahul.exclusivetv.SP.apiDownloadHostFallback.takeIf { it.isNotEmpty() } ?: DEFAULT_DOWNLOAD_HOST_FALLBACK

        private fun ensureTrailingSlash(url: String): String {
            return if (url.endsWith("/")) url else "$url/"
        }
    }
}
