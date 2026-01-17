package com.codesrahul.exclusivetv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")
        
        // Initialize SP just in case Application onCreate failed or didn't run (unlikely but safe)
        try {
           SP.init(context)
        } catch (e: Exception) {
           Log.e("BootReceiver", "Failed to init SP", e)
        }

        if (Intent.ACTION_BOOT_COMPLETED == action || 
            "android.intent.action.QUICKBOOT_POWERON" == action ||
            "com.htc.intent.action.QUICKBOOT_POWERON" == action) {
            
            if (SP.bootStartup) {
                Log.i("BootReceiver", "Auto-start enabled. Launching MainActivity...")
                val i = Intent(context, MainActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Add these flags to ensuring a fresh clean launch
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(i)
            } else {
                Log.i("BootReceiver", "Auto-start disabled in settings.")
            }
        }
    }

}
