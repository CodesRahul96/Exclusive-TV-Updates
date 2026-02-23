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

    private var cachedStandardUrl: String? = null
    fun getStandardApiUrl(): String {
        if (cachedStandardUrl == null) {
            cachedStandardUrl = getNativeStandardUrl()
        }
        return cachedStandardUrl ?: ""
    }

    private var cachedPremiumUrl: String? = null
    fun getPremiumApiUrl(): String {
        if (cachedPremiumUrl == null) {
            cachedPremiumUrl = getNativePremiumUrl()
        }
        return cachedPremiumUrl ?: ""
    }

    fun verifyIntegrity(context: android.content.Context): Boolean {
        // Returns true if tampered (signature mismatch)
        return verifyNativeIntegrity(context)
    }

    fun isVpnActiveNative(): Boolean {
        return checkVpnNative()
    }

    private external fun getNativeKey(): String
    private external fun getMaintenanceKey(): String
    private external fun getNativeHmacKey(): String
    private external fun getNativeStandardUrl(): String
    private external fun getNativePremiumUrl(): String
    private external fun verifyNativeIntegrity(context: android.content.Context): Boolean
    private external fun checkVpnNative(): Boolean
}
