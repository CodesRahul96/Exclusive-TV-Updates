package com.codesrahul.exclusivetv.requests

import com.codesrahul.exclusivetv.SecureHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient {
    private var okHttpClient = SecureHttpClient.client

    val releaseService: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(HOST)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ReleaseService::class.java)
    }

    val releaseServiceFallback: ReleaseService by lazy {
        Retrofit.Builder()
            .baseUrl(HOST_FALLBACK)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ReleaseService::class.java)
    }

    companion object {
        // Primary source (will be private)
        const val HOST = "https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-APP/"
        const val DOWNLOAD_HOST = "https://github.com/CodesRahul96/Exclusive-TV-APP/releases/download/"
        
        // Fallback source (public updates repository)
        const val HOST_FALLBACK = "https://raw.githubusercontent.com/CodesRahul96/Exclusive-TV-Updates/"
        const val DOWNLOAD_HOST_FALLBACK = "https://github.com/CodesRahul96/Exclusive-TV-Updates/releases/download/"
    }
}
