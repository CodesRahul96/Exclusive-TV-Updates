package com.codesrahul.exclusivetv

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import org.json.JSONArray

/**
 * Singleton that provides a certificate-pinned OkHttpClient instance.
 *
 * Pinning strategy:
 *  - Primary: pins loaded from Firebase Remote Config (stored in SP.sslPinsIndevs), fetched on startup.
 *  - Fallback: stable hardcoded intermediate/root CA pins for **.indevs.in.
 *
 * Pinning intermediate + root avoids the 90-day Let's Encrypt leaf cert rotation problem.
 *
 * To refresh pins manually (e.g. if E8 intermediate rotates):
 *   openssl s_client -connect exclusivetvapi.indevs.in:443 2>/dev/null | openssl x509 -noout -pubkey |
 *   openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
 * Then update Firebase Remote Config key "ssl_pins_indevs" with the new JSON array.
 */
object SecureHttpClient {

    private const val TAG = "SecureHttpClient"

    // Stable fallback pins: Let's Encrypt E8 Intermediate + ISRG Root X1 variants
    private val FALLBACK_PINS_INDEVS = listOf(
        "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU=", // Let's Encrypt E8 Intermediate (current)
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1 (device-verified hash)
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQZEu06w+Ehmto="  // ISRG Root X1 (alternate hash)
    )

    // BUG FIX: @Volatile alone is not sufficient for double-checked locking.
    // Two concurrent threads can both see _client == null and both call buildClient().
    // Using @Synchronized on the accessor ensures only one build happens at a time.
    @Volatile
    private var _client: OkHttpClient? = null

    val client: OkHttpClient
        get() = _client ?: getOrBuild()

    @Synchronized
    private fun getOrBuild(): OkHttpClient {
        // Double-checked locking: re-check inside synchronized block
        // because another thread may have already built the client between the
        // outer null check and acquiring this lock.
        return _client ?: buildClient().also { _client = it }
    }

    /**
     * Called after Remote Config is fetched (on a background thread) to rebuild
     * the client with up-to-date pins.
     * MUST be called off the main thread since OkHttpClient construction is non-trivial.
     */
    @Synchronized
    fun refresh() {
        Log.i(TAG, "Refreshing OkHttpClient with updated cert pins.")
        // Close old client's resources before replacing (releases thread pools + connection pool)
        _client?.connectionPool()?.evictAll()
        _client?.dispatcher()?.executorService()?.shutdown()
        _client = buildClient()
    }

    private fun buildClient(): OkHttpClient {
        val remotePins = parsePins(SP.sslPinsIndevs)
        val effectivePins = if (remotePins.isNotEmpty()) {
            Log.i(TAG, "Using ${remotePins.size} remote cert pin(s) for **.indevs.in")
            remotePins
        } else {
            Log.i(TAG, "No remote pins found — using ${FALLBACK_PINS_INDEVS.size} hardcoded fallback pin(s)")
            FALLBACK_PINS_INDEVS
        }

        val pinnerBuilder = CertificatePinner.Builder()
            // GitHub (DigiCert Global Root CA) - stable long-term pin
            .add("**.github.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
            .add("**.githubusercontent.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
            // Vercel (ISRG Root X1 & DigiCert Global Root CA fallback)
            .add("**.vercel.app", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQZEu06w+Ehmto=")
            .add("**.vercel.app", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")

        // Apply indevs.in pins (remote or fallback)
        for (pin in effectivePins) {
            pinnerBuilder.add("**.indevs.in", pin)
        }

        return OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .addInterceptor(SecurityInterceptor(MyTVApplication.getInstance()))
            .certificatePinner(pinnerBuilder.build())
            .hostnameVerifier { hostname, session ->
                val hv = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                hv.verify(hostname, session)
            }
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
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

    /**
     * Parses a JSON array string of pin hashes into a List<String>.
     * Returns an empty list on any parse error.
     * Example input: ["sha256/ABC...=","sha256/XYZ...="]
     */
    private fun parsePins(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.startsWith("sha256/") }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse remote ssl pins: ${e.message}")
            emptyList()
        }
    }
}
