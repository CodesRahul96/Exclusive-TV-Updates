package com.codesrahul.exclusivetv
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object RootCheckUtil {

    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
    }

    private fun checkRootMethod1(): Boolean {
        // Disabled: Many Android TV boxes use test-keys by default, which causes false positives.
        return false
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
            "/system/xbin/daemonsu"
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
