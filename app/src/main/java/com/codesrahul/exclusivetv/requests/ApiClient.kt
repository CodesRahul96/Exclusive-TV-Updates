package com.codesrahul.exclusivetv.requests



import com.codesrahul.exclusivetv.SecureHttpClient
import com.codesrahul.exclusivetv.StringObfuscator
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
        val HOST: String
            get() = StringObfuscator.getHost()
            
        val DOWNLOAD_HOST: String
            get() = StringObfuscator.getDownloadHost()
        
        // Fallback source (public updates repository)
        val HOST_FALLBACK: String
            get() = StringObfuscator.getHostFallback()
            
        val DOWNLOAD_HOST_FALLBACK: String
            get() = StringObfuscator.getDownloadHostFallback()
    }
}
