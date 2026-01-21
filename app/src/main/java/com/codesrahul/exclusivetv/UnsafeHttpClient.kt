package com.codesrahul.exclusivetv

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * A permissive HTTP client for fetching user-provided streams/playlists.
 * Bypasses SSL verification to support diverse IPTV providers (http/https, self-signed, etc).
 */
object UnsafeHttpClient {
    val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(60, TimeUnit.SECONDS) // Liberal timeouts
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                // Use a robust IPTV Player User-Agent (TiviMate) to bypass anti-bot/browser-check pages.
                val request = original.newBuilder()
                    .header("User-Agent", "TiviMate/4.7.0 (Linux; Android 11; TV Box Build/RTM1.211111.111)")
                    .method(original.method(), original.body())
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
