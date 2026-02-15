package com.codesrahul.exclusivetv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * PhoenixActivity
 * 
 * This Activity runs in a separate process (see AndroidManifest :phoenix)
 * to facilitate a complete application restart. It survives the death of the
 * main application process and relaunches it from scratch.
 */
class PhoenixActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Retrieve the main application launch intent
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        
        if (launchIntent != null) {
            // 2. Prepare the intent for a fresh start
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // 3. Launch the main application
            startActivity(launchIntent)
        }

        // 4. Kill this Phoenix process to clean up
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /**
         * Triggers a hard application restart.
         * 
         * 1. Starts PhoenixActivity in a new process.
         * 2. Immediately kills the current process.
         */
        fun trigger(context: Context) {
            val intent = Intent(context, PhoenixActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Necessary if context is not an Activity
            context.startActivity(intent)

            // Kill the current process immediately
            Process.killProcess(Process.myPid())
            System.exit(0)
        }
    }
}
