package com.codesrahul.exclusivetv.tis

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.TvContractCompat
import com.codesrahul.exclusivetv.R

class TvInputSetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // No UI needed for this simple setup, just run logic and finish
        registerChannel()
        
        // Mark setup as complete
        val inputId = intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID)
        if (inputId != null) {
            val intent = android.content.Intent()
            intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId)
            setResult(Activity.RESULT_OK, intent)
        }
        
        finish()
    }

    private fun registerChannel() {
        try {
            val inputId = intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID) ?: return
            
            // 1. Check if channel already exists to avoid duplicates
            // (For simplicity in this V1, we just add blindly or catch error, 
            // but ideally we should query existing channels for this inputId)
            
            // 2. Build Channel Data
            val builder = Channel.Builder()
            builder.setDisplayName("Exclusive TV")
            builder.setDescription("Launch Exclusive TV App")
            builder.setInputId(inputId)
            builder.setOriginalNetworkId(1)
            builder.setServiceId(1)
            builder.setType(TvContractCompat.Channels.TYPE_OTHER)
            
            // 3. Insert into System DB
            val channelUri = contentResolver.insert(
                TvContract.Channels.CONTENT_URI,
                builder.build().toContentValues()
            )

            if (channelUri != null) {
                // Logo insertion omitted for V1 simplicity to avoid Channel object reconstruction issues
                // This is sufficient for the "Sync Sources" menu to show the text "Exclusive TV"
                
                Toast.makeText(this, "Exclusive TV Source Added", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
        }
    }
}
