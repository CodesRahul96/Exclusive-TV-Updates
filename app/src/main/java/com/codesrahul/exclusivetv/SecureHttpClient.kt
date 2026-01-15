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
            .build()
    }
}
