package com.codesrahul.exclusivetv

import okhttp3.OkHttpClient

/**
 * Singleton that provides a secure OkHttpClient instance.
 * SSL certificate pinning removed — standard TLS verification is used.
 */
object SecureHttpClient {

    @Volatile
    private var _client: OkHttpClient? = null

    val client: OkHttpClient
        get() = _client ?: getOrBuild()

    val syncClient: OkHttpClient
        get() = _syncClient ?: getOrBuildSync()

    @Volatile
    private var _syncClient: OkHttpClient? = null

    @Synchronized
    private fun getOrBuild(): OkHttpClient {
        return _client ?: buildClient().also { _client = it }
    }

    @Synchronized
    private fun getOrBuildSync(): OkHttpClient {
        return _syncClient ?: buildSyncClient().also { _syncClient = it }
    }

    /**
     * Called after Remote Config is fetched (on a background thread) to rebuild the client.
     */
    @Synchronized
    fun refresh() {
        _client?.connectionPool?.evictAll()
        _client?.dispatcher?.executorService?.shutdown()
        _client = buildClient()
        
        _syncClient?.connectionPool?.evictAll()
        _syncClient?.dispatcher?.executorService?.shutdown()
        _syncClient = buildSyncClient()
    }

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .addInterceptor(SecurityInterceptor(MyTVApplication.getInstance()))
            .hostnameVerifier { hostname, session ->
                val hv = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                hv.verify(hostname, session)
            }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(15, 5, java.util.concurrent.TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun buildSyncClient(): OkHttpClient {
        return client.newBuilder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

