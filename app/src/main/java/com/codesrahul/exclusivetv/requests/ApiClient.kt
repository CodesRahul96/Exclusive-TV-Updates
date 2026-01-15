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

    companion object {
        const val HOST = "https://github.com/thirumurthy/tll-player/raw/"
        const val DOWNLOAD_HOST = "https://github.com/thirumurthy/tll-player/releases/download/"
    }
}
