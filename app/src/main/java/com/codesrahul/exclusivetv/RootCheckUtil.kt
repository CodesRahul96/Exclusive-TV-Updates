package com.codesrahul.exclusivetv
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object RootCheckUtil {

    fun isDeviceRooted(context: android.content.Context): Boolean {
        val isTV = isTvDevice(context)
        
        // On TV devices, we skip checks that produce high false-positives (test-keys, su-files, su-command)
        // because generic TV firmware often includes inactive su binaries or dev-tags.
        return checkRootMethod1() || 
               (!isTV && checkRootMethod2()) || 
               (!isTV && checkRootMethod3()) || 
               checkRootByPackages(context)
    }

    private fun checkRootMethod1(): Boolean {
        // Many Android TV boxes use test-keys by default, which causes false positives.
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
        // Only run 'which su' on non-TV devices or if other checks are uncertain
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val input = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            null != input.readLine()
        } catch (t: Throwable) {
            false
        }
    }

    private fun checkRootByPackages(context: android.content.Context): Boolean {
        val rootPackages = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zacharee1.systemuituner",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        
        val pm = context.packageManager
        return rootPackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun isTvDevice(context: android.content.Context): Boolean {
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
               context.packageManager.hasSystemFeature("android.software.leanback")
    }
}
