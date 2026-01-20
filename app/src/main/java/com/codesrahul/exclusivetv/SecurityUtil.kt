package com.codesrahul.exclusivetv

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

import com.codesrahul.exclusivetv.requests.ReleaseResponse

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

        // 3. Frida Check
        if (checkFrida()) {
            return true
        }

        // 4. Root Check
        if (RootCheckUtil.isDeviceRooted()) {
            return true
        }

        // 5. App Outdated Check
        if (isAppOutdated) {
            return true
        }

        return false
    }

    @Volatile
    var isAppOutdated: Boolean = false

    var remoteRelease: ReleaseResponse? = null



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

    private fun checkFrida(): Boolean {
        // Check for Frida specific files and ports
        // 1. Open ports check (common Frida ports)
        /*
        try {
            val socket = java.net.Socket("127.0.0.1", 27042)
            socket.close()
            return true
        } catch (e: Exception) {
            // Port closed or unreachable, which is good
        }
        */
        // Note: NetworkOnMainThreadException if called from main thread. 
        // Better to check for artifacts in /proc/self/maps which is safer and synchronous.
        
        try {
            val file = java.io.File("/proc/self/maps")
            if (file.exists()) {
                val contents = file.readText()
                if (contents.contains("frida") || contents.contains("com.android.reverse") || contents.contains("re.frida.server")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore access errors
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
