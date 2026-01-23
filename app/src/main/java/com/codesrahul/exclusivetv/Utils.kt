package com.codesrahul.exclusivetv

import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.util.TypedValue
import com.google.gson.Gson
import com.codesrahul.exclusivetv.requests.TimeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.NetworkInterface
import java.util.Collections

object Utils {
    private var between: Long = 0

    fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(0, delim).uppercase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) { }
        return ""
    }

    fun getMacAddress(context: android.content.Context): String {
        try {
            // 1. Try NetworkInterface (Standard)
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true) && !nif.name.equals("eth0", ignoreCase = true)) continue

                val macBytes = nif.hardwareAddress ?: continue
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                val mac = res1.toString()
                if (mac.isNotEmpty() && mac != "02:00:00:00:00:00") {
                    return mac
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // 2. Fallback: Generate pseudo-MAC from Android ID (Persistent on device)
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty()) {
                return generateMacFromAndroidId(androidId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "02:00:00:00:00:00"
    }

    private fun generateMacFromAndroidId(androidId: String): String {
        try {
            // Hash the Android ID to get bytes
            val md = java.security.MessageDigest.getInstance("MD5")
            md.update(androidId.toByteArray())
            val bytes = md.digest()

            // Build MAC string from first 6 bytes
            val sb = StringBuilder()
            for (i in 0 until 6) {
                var b = bytes[i].toInt()
                if (i == 0) {
                    // Set unicast (bit 0=0) and locally administered (bit 1=1)
                    // b & 11111110 | 00000010
                    b = (b and 0xFE) or 0x02
                }
                sb.append(String.format("%02X:", b.toByte()))
            }
            if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            return sb.toString()
        } catch (e: Exception) {
            return "02:00:00:00:00:00"
        }
    }

    fun getDateFormat(format: String): String {
        return SimpleDateFormat(
            format,
            Locale.ENGLISH
        ).format(Date(System.currentTimeMillis() - between))
    }

    fun getDateTimestamp(): Long {
        return (System.currentTimeMillis() - between) / 1000
    }

    suspend fun init() {
        var currentTimeMillis: Long = 0
        try {
            currentTimeMillis = getTimestampFromServer()
        } catch (e: Exception) {
            Log.e("Utils", "Failed to retrieve timestamp from server: ${e.message}")
        }
        between = System.currentTimeMillis() - currentTimeMillis
    }

    /**
     * 从服务器获取时间戳
     * @return Long 时间戳
     */
    private suspend fun getTimestampFromServer(): Long {
        return withContext(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(1, java.util.concurrent.TimeUnit.SECONDS).build()
            val request = okhttp3.Request.Builder()
                .url("https://api.m.taobao.com/rest/api3.do?api=mtop.common.getTimestamp")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val string = response.body()?.string()
                    Gson().fromJson(string, TimeResponse::class.java).data.t.toLong()
                }
            } catch (e: IOException) {
                // Handle network errors
                throw IOException("Error during network request", e)
            }
        }
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().displayMetrics
        ).toInt()
    }

    fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics
        ).toInt()
    }

    fun isTmallDevice() = Build.MANUFACTURER.equals("Tmall", ignoreCase = true)

    fun formatUrl(url: String): String {
        // Check if the URL already starts with "http://" or "https://"
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        // Check if the URL starts with "//"
        if (url.startsWith("//")) {
            return "http://$url"
        }

        // Otherwise, add "http://" to the beginning of the URL
        return "http://${url}"
    }
}
