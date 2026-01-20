package com.codesrahul.exclusivetv
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object RootCheckUtil {

    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
    }

    private fun checkRootMethod1(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootMethod2(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/xbin/daemonsu",
            "/data/local/xbin/daemonsu",
            "/sbin/.magisk",
            "/node_modules",
            "/ssh/bin/sudo"
        )
        return paths.any { java.io.File(it).exists() }
    }

    private fun checkRootMethod3(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val input = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            null != input.readLine()
        } catch (t: Throwable) {
            false
        }
    }
    
    private fun checkRootMethod4(): Boolean {
        // Check for known Root Apps Package Names
        // This requires Context to be accurate, but here we can't easily check packages without Context.
        // We will skip package check in this Util unless we pass context. 
        // For now, let's check for standard "su" executable execution
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            true
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}
