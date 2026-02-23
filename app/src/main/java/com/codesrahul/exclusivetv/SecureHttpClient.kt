package com.codesrahul.exclusivetv

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/**
 * Singleton that provides a single, certificate-pinned OkHttpClient instance for the whole app.
 *
 * Pinning strategy: Pin INTERMEDIATE and ROOT CA certificates instead of leaf certificates.
 * Let's Encrypt leaf certs rotate every 90 days, which would break pinning repeatedly.
 * The intermediate (E8) and root (ISRG Root X1) are stable for years.
 *
 * To refresh pins, run:
 *     openssl s_client -connect exclusivetvapi.indevs.in:443 2>/dev/null | openssl x509 -noout -pubkey | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
 */
object SecureHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 1. BLOCK SNIFFERS & VPN INTERCEPTS: Force No Proxy
            .proxy(java.net.Proxy.NO_PROXY)
            .addInterceptor(SecurityInterceptor(MyTVApplication.getInstance()))
            .certificatePinner(
                CertificatePinner.Builder()
                    // GitHub (DigiCert Global Root CA) - stable long-term pin
                    .add("**.github.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    .add("**.githubusercontent.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    // Vercel (ISRG Root X1 & DigiCert Global Root CA fallback)
                    .add("**.vercel.app", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQZEu06w+Ehmto=")
                    .add("**.vercel.app", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    // Indevs APIs - pinning intermediate (E8) + root (ISRG Root X1)
                    // These are STABLE and won't break when leaf certs rotate every 90 days.
                    .add("**.indevs.in", "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU=") // Let's Encrypt E8 Intermediate (current)
                    .add("**.indevs.in", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1 (current hash from device)
                    .add("**.indevs.in", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQZEu06w+Ehmto=") // ISRG Root X1 (backup/alternate hash)
                    .build()
            )
            // 2. BLOCK SNIFFERS: Enforce strict Hostname Verification (rejects forged generic certs)
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
}
