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

    private var cachedDeviceId: String? = null
    private val hmacPool = ThreadLocal<javax.crypto.Mac>()

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
        if (RootCheckUtil.isDeviceRooted(context)) {
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

    /**
     * Retrieves the unique Android Device ID for account binding.
     */
    @android.annotation.SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return cachedDeviceId ?: synchronized(this) {
            cachedDeviceId ?: (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device").also { cachedDeviceId = it }
        }
    }

    /**
     * Extracts a unique hardware ID from MediaDRM (Widevine).
     * This ID is highly persistent across factory resets on most modern devices.
     */
    private fun getMediaDrmId(): String {
        return try {
            // Widevine UUID: edef8ba9-79d6-4ace-a3c8-27dcd51d21ed
            val widevineUuid = java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
            val mediaDrm = android.media.MediaDrm(widevineUuid)
            val deviceUniqueId = mediaDrm.getPropertyByteArray(android.media.MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            
            // Close resource properly (API 28+ uses close, earlier uses release)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                mediaDrm.close()
            } else {
                mediaDrm.release()
            }
            
            // Hash the raw bytes to create a stable string representation
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(deviceUniqueId)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates a robust hardware fingerprint by combining multiple hardware traits.
     * This makes it harder to bypass trial restrictions by just changing the Android ID.
     */
    fun getDeviceFingerprint(context: Context): String {
        val androidId = getDeviceId(context)
        val mediaDrmId = getMediaDrmId()
        val hardwareInfo = Build.BOARD + Build.BRAND + Build.DEVICE + Build.DISPLAY +
                          Build.HOST + Build.ID + Build.MANUFACTURER + Build.MODEL +
                          Build.PRODUCT + Build.TAGS + Build.TYPE + Build.USER
        
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest((androidId + mediaDrmId + hardwareInfo).toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            androidId // Fallback to plain Android ID on error
        }
    }

    /**
     * Generates an HMAC-SHA256 signature for API requests.
     */
    fun generateHmacSha256(data: String, key: String): String {
        try {
            var mac = hmacPool.get()
            if (mac == null) {
                mac = javax.crypto.Mac.getInstance("HmacSHA256")
                hmacPool.set(mac)
            }
            val secretKeySpec = javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac?.init(secretKeySpec)
            val hash = mac?.doFinal(data.toByteArray(Charsets.UTF_8)) ?: return ""
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            return ""
        }
    }
}

class SecurityInterceptor(private val context: Context) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        if (SecurityUtil.isDeviceRestricted(context)) {
            throw IOException("Security Violation: Request blocked due to insecure environment.")
        }
        
        val original = chain.request()
        
        // --- API Request Signing (HMAC-SHA256) ---
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val deviceId = SecurityUtil.getDeviceId(context)
        
        val url = original.url.toString()
        val method = original.method
        
        // Payload format: URL|METHOD|TIMESTAMP|DEVICE_ID
        val payload = "$url|$method|$timestamp|$deviceId"
        val signature = SecurityUtil.generateHmacSha256(payload, SecretManager.getHmacKey())
        
        val requestWithAuth = original.newBuilder()
            .header("X-Timestamp", timestamp)
            .header("X-Device-ID", deviceId)
            .header("X-App-Signature", signature)
            .build()
            
        return chain.proceed(requestWithAuth)
    }
}
