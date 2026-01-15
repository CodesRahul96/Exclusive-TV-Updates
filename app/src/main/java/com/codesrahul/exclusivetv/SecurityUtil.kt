package com.codesrahul.exclusivetv

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object SecurityUtil {

    fun isDeviceRestricted(context: Context): Boolean {
        // 1. Debugger Check
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        // 2. Proxy Check
        if (isProxySet(context)) {
            return true
        }

        return false
    }

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
}

class SecurityInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (SecurityUtil.isDeviceRestricted(context)) {
            throw IOException("Security Violation: Request blocked due to insecure environment.")
        }
        return chain.proceed(chain.request())
    }
}
