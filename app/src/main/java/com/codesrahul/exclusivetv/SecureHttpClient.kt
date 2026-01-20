package com.codesrahul.exclusivetv

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/**
 * Singleton that provides a single, certificate-pinned OkHttpClient instance for the whole app.
 *
 * Replace the sample SHA-256 hashes below with the real pins taken from your production server’s
 * certificates. You can grab them with:
 *
 *     openssl s_client -connect example.com:443 -servername example.com 2>/dev/null | \
 *         openssl x509 -noout -pubkey | \
 *         openssl pkey -pubin -outform DER | \
 *         openssl dgst -sha256 -binary | base64
 *
 * Each host you talk to must be added with its own pin.  If a host rotates its certs you can add
 * multiple pins (one per line) for the same host.
 */
object SecureHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(SecurityInterceptor(MyTVApplication.getInstance()))
            .certificatePinner(
                CertificatePinner.Builder()
                    // GitHub (DigiCert Global Root CA)
                    .add("**.github.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    .add("**.githubusercontent.com", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    // Vercel (ISRG Root X1 & DigiCert Global Root CA fallback)
                    .add("**.vercel.app", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQZEu06w+Ehmto=")
                    .add("**.vercel.app", "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=")
                    .build()
            )
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .method(original.method(), original.body())
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
