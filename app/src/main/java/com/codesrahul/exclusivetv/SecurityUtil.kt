package com.codesrahul.exclusivetv

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

import com.codesrahul.exclusivetv.requests.ReleaseResponse

object SecurityUtil {

    @Volatile
    var isAppOutdated: Boolean = false

    @Volatile
    var isMaintenanceMode: Boolean = false

    private var lastFullCheckTime: Long = 0
    private var lastCheckResult: Boolean = false
    private const val CHECK_CACHE_DURATION = 30 * 1000L // 30 seconds

    fun isDeviceRestricted(context: Context): Boolean {
        // 1. Maintenance Mode Check - Always check, very fast
        if (isMaintenanceMode) {
            Log.e("SecurityUtil", "Security Violation: Maintenance Mode active")
            return true
        }

        // 2. Debugger Check - Always check, very fast
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            Log.e("SecurityUtil", "Security Violation: Debugger connected")
            return true
        }

        // 3. Cached Extensive Checks
        val now = System.currentTimeMillis()
        if (now - lastFullCheckTime < CHECK_CACHE_DURATION) {
            return lastCheckResult
        }

        // Perform extensive checks
        val result = performExtensiveChecks(context)
        lastCheckResult = result
        lastFullCheckTime = now
        return result
    }

    private fun performExtensiveChecks(context: Context): Boolean {
        // Proxy Check
        if (isProxySet(context)) {
            android.util.Log.e("SecurityUtil", "Security Violation: Proxy detected")
            return true
        }

        // Frida Check
        if (checkFrida()) {
            android.util.Log.e("SecurityUtil", "Security Violation: Frida detected")
            return true
        }

        // Root Check
        if (RootCheckUtil.isDeviceRooted()) {
            android.util.Log.e("SecurityUtil", "Security Violation: Root detected")
            return true
        }

        // Native Integrity Check (Signature)
        if (SecretManager.verifyIntegrity(context)) {
            android.util.Log.e("SecurityUtil", "Security Violation: Signature mismatch")
            return true
        }

        return false
    }

    var remoteRelease: ReleaseResponse? = null



    private fun isProxySet(context: Context): Boolean {
        val proxyAddress = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        
        if (!proxyAddress.isNullOrEmpty() || !proxyPort.isNullOrEmpty()) {
            return true
        }
        
        try {
            val globalProxy = Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
            if (!globalProxy.isNullOrEmpty()) {
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return false
    }

    private fun checkFrida(): Boolean {
        try {
            val file = java.io.File("/proc/self/maps")
            if (file.exists()) {
                val contents = file.readText()
                if (contents.contains("frida") || contents.contains("com.android.reverse") || contents.contains("re.frida.server")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore access errors
        }
        
        return false
    }

    /**
     * Decrypts channel data using AES-256-CBC.
     * Use this with the NDK key for the ultimate security.
     */
    fun decryptChannelData(data: String, key: String): String {
        if (key.isEmpty() || data.isEmpty()) return data
        try {
            val combined = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
            if (combined.size < 16) return data
            
            val iv = combined.sliceArray(0 until 16)
            val encryptedBytes = combined.sliceArray(16 until combined.size)
            
            // PROFESSIONAL KEY DERIVATION: Use SHA-256 to create a 32-byte key
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes = md.digest(key.toByteArray(Charsets.UTF_8))
            
            val secretKeySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
            
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)
            
            return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            return data
        }
    }
}

class SecurityInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (SecurityUtil.isDeviceRestricted(context)) {
            throw IOException("Security Violation: Request blocked due to insecure environment.")
        }
        return chain.proceed(chain.request())
    }
}
