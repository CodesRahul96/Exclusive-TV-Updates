package com.codesrahul.exclusivetv

object SecretManager {
    private var cachedKey: String? = null

    init {
        try {
            System.loadLibrary("native-lib")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    /**
     * Native method to retrieve the secret key securely.
     * Cached in memory for performance.
     */
    fun getAppKey(): String {
        if (cachedKey == null) {
            cachedKey = getNativeKey()
        }
        return cachedKey ?: ""
    }

    fun getMaintenanceModeKey(): String {
        return getMaintenanceKey()
    }

    private var cachedHmacKey: String? = null

    fun getHmacKey(): String {
        if (cachedHmacKey == null) {
            cachedHmacKey = getNativeHmacKey()
        }
        return cachedHmacKey ?: ""
    }

    fun verifyIntegrity(context: android.content.Context): Boolean {
        // Returns true if tampered (signature mismatch)
        return verifyNativeIntegrity(context)
    }

    private external fun getNativeKey(): String
    private external fun getMaintenanceKey(): String
    private external fun getNativeHmacKey(): String
    private external fun verifyNativeIntegrity(context: android.content.Context): Boolean
}
