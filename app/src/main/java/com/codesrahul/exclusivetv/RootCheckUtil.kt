package com.codesrahul.exclusivetv
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object RootCheckUtil {
    fun isDeviceRooted(): Boolean {
        return checkBuildTags() || checkSuExists() || checkSuCommand()
    }

    private fun checkBuildTags(): Boolean {
        val buildTags = android.os.Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkSuExists(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkSuCommand(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val input = BufferedReader(InputStreamReader(process.inputStream))
            val result = input.readLine()
            result != null
        } catch (e: Exception) {
            false
        }
    }
}
