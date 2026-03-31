@file:Suppress("DEPRECATION")

package com.codesrahul.exclusivetv

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.security.MessageDigest

private const val TAG = "Extensions"

private val Context.packageInfo: PackageInfo
    get() {
        val flag = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNING_CERTIFICATES
        }
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, flag)
        } else {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        }
    }

/**
 * Return the version code of the app which is defined in build.gradle.
 * eg:100
 */
val Context.appVersionCode: Long
    get() {
        val packageInfo = this.packageInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

/**
 * Return the version name of the app which is defined in build.gradle.
 * eg:1.0.0
 */
val Context.appVersionName: String get() = packageInfo.versionName

val Context.appSignature: String
    get() {
        val packageInfo = this.packageInfo
        var sign: Signature? = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val signatures: Array<out Signature>? = packageInfo.signatures
            if (signatures != null) {
                sign = signatures[0]
            }
        } else {
            val signingInfo: SigningInfo? = packageInfo.signingInfo
            if (signingInfo != null) {
                sign = signingInfo.apkContentsSigners[0]
            }
        }
        if (sign == null) {
            return ""
        }
        return hashSignature(sign)
    }

private fun hashSignature(signature: Signature): String {
    return try {
        val md = MessageDigest.getInstance("MD5")
        md.update(signature.toByteArray())
        val digest = md.digest()
        digest.let { it -> it.joinToString("") { "%02x".format(it) } }
    } catch (e: Exception) {
        ""
    }
}

fun String.showToast(duration: Int = Toast.LENGTH_SHORT) {
    MyTVApplication.getInstance().toast(this, duration)
}

/**
 * Apply unified premium focus behavior (Scale + Sound + Shadow) to any View.
 * Works across both TV (D-Pad) and Mobile (Touch/Tap-Focus).
 */
fun android.view.View.applyPremiumFocus(scale: Float = 1.08f, elevationDp: Float = 12f) {
    val context = this.context
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
    val isTv = uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    
    if (isTv) {
        // High-Quality TV Focus (Managed by TvUiUtils)
        MyTVApplication.getInstance().getTvUiUtils().applyTvFocus(this, scale, elevationDp)
    } else {
        // Subtle Mobile Focus Feedback
        this.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }
}
