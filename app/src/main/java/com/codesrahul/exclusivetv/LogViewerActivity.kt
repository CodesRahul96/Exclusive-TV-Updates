package com.codesrahul.exclusivetv

import android.app.Activity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogViewerActivity : Activity() {

    private lateinit var logText: TextView
    private lateinit var refreshParam: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        refreshParam = Button(this).apply {
            text = "Refresh Logs"
            setOnClickListener { loadLogs() }
        }
        layout.addView(refreshParam)

        logText = TextView(this).apply {
            setTextColor(android.graphics.Color.GREEN)
            textSize = 12f
            movementMethod = ScrollingMovementMethod()
            setHorizontallyScrolling(true)
            // Give it weight to fill remaining space
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        layout.addView(logText)

        setContentView(layout)
        loadLogs()
    }

    private fun loadLogs() {
        refreshParam.isEnabled = false
        refreshParam.text = "Loading..."
        
        CoroutineScope(Dispatchers.IO).launch {
            val builder = StringBuilder()
            try {
                // Command to read logs for this PID
                val process = Runtime.getRuntime().exec("logcat -d -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                // Read last 1000 lines roughly
                val lines = reader.readLines().takeLast(800)
                
                for (line in lines) {
                    // Filter for our tags or errors
                    if (line.contains("TVList") || 
                        line.contains("DeepHeuristicParser") || 
                        line.contains("M3UParser") || 
                        line.contains("Exception") || 
                        line.contains("System.err") ||
                        line.contains("ExclusiveTV")) {
                        builder.append(line).append("\n")
                    }
                }
                
                reader.close()
            } catch (e: Exception) {
                builder.append("Failed to read logs: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                logText.text = if (builder.isNotEmpty()) builder.toString() else "No relevant logs found. Try interacting with the app first."
                
                // Scroll to bottom
                logText.post {
                   val scrollAmount = logText.layout.getLineTop(logText.lineCount) - logText.height
                   if (scrollAmount > 0) logText.scrollTo(0, scrollAmount)
                }
                
                refreshParam.isEnabled = true
                refreshParam.text = "Refresh Logs"
            }
        }
    }
}
