package com.codesrahul.exclusivetv

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val FILE_NAME = "crash_logs.txt"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(context, throwable, "UNCAUGHT EXCEPTION")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun logError(context: Context, error: Throwable, tag: String = "HANDLED ERROR") {
        saveCrashLog(context, error, tag)
    }

    private fun saveCrashLog(context: Context, throwable: Throwable, type: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logFile = File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
            
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val logEntry = """
                
                --------------------------------------------------
                TIME: $timestamp
                TYPE: $type
                MESSAGE: ${throwable.message}
                --------------------------------------------------
                $stackTrace
                --------------------------------------------------
                
            """.trimIndent()

            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
            
            Log.e(TAG, "Crash log saved to: ${logFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }
}
