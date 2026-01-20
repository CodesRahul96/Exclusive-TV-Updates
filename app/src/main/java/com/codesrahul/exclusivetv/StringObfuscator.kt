package com.codesrahul.exclusivetv

import android.util.Base64

object StringObfuscator {
    private const val KEY = "ExclusiveTVByCodesRahul96"

    fun getHost(): String = decode("LQwXHAZJRlkXNSFsHiobDBARJxINBw9WWDEdDRhbEAYbShc5JhwwPQUNBj5YXlopQVUpDRAFAxZEIjN5FxIpbA==")
    fun getDownloadHost(): String = decode("LQwXHAZJRlkCPSIqDCFBBwoefSIHEQlKZCQQFgBMRUYzHTc6NwoqGQFIJwRMKSU8FkQgFAYNBhYaWQE7ISwVLA4ASg==")
    
    fun getHostFallback(): String = decode("LQwXHAZJRlkXNSFsHiobDBARJxINBw9WWDEdDRhbEAYbShc5JhwwPQUNBj5YXlopQVUpDRAFAxZEIjN5AzIdIhsBFlw=")
    fun getDownloadHostFallback(): String = decode("LQwXHAZJRlkCPSIqDCFBBwoefSIHEQlKZCQQFgBMRUYzHTc6NwoqGQFIJwRMPQUIWEIgC0weEB8MFxYxJW0dLBgKCRwzBUc=")
    
    fun getConfigUrl(): String = decode("LQwXHAZJRlkALDUuDDAGEgBeJhdFFBxJGyQICkIDFhsVADh4IwkzQA==")

    private fun decode(str: String): String {
        try {
            val decodedBytes = Base64.decode(str, Base64.DEFAULT)
            var decrypted = ""
            for (i in decodedBytes.indices) {
                decrypted += (decodedBytes[i].toInt() xor KEY[i % KEY.length].code).toChar()
            }
            return decrypted
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
