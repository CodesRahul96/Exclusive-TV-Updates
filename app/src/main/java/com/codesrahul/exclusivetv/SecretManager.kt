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

    private external fun getNativeKey(): String
}
