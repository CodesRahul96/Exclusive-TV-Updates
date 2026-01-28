package com.codesrahul.exclusivetv.models

import android.util.Log
import org.json.JSONObject
import java.util.ArrayList
import java.util.Locale

object StarParser {
    private const val TAG = "StarParser"

    fun parse(content: String): ArrayList<TV> {
        val tvList = ArrayList<TV>()
        val lines = content.lines()
        
        var currentHeaders = HashMap<String, String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("#EXTHTTP:") || trimmed.startsWith("# EXTHTTP:")) {
                    val jsonStr = trimmed.substringAfter("HTTP:").trim()
                    try {
                        val jsonObject = JSONObject(jsonStr)
                        val keys = jsonObject.keys()
                        currentHeaders = HashMap()
                        
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = jsonObject.getString(key)
                            currentHeaders[key] = value
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "StarParser JSON error: $jsonStr", e)
                    }
                }
            } else {
                if (trimmed.startsWith("http")) {
                    val tv = TV(
                        uris = arrayListOf(trimmed),
                        child = ArrayList()
                    )
                    
                    val namePart = trimmed.substringAfter("/mp1/").substringBefore("/")
                    val niceName = namePart.replace("-", " ").capitalizeWords()
                    
                    tv.title = if (niceName.isNotEmpty()) niceName else "Star Channel"
                    tv.name = tv.title
                    tv.logo = "" 
                    tv.group = "Star Live"
                    
                    if (currentHeaders.isNotEmpty()) {
                        tv.headers = HashMap(currentHeaders)
                    }
                    
                    tvList.add(tv)
                    currentHeaders = HashMap() 
                }
            }
        }
        
        Log.i(TAG, "StarParser extracted ${tvList.size} channels")
        return tvList
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { 
        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } 
    }
}
