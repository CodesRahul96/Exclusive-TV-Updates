package com.thirutricks.tllplayer

import org.junit.Test
import java.io.File
import java.net.URL
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

class ChannelConverterTest {

    data class SourceChannel(
        val type: String?,
        val id: String?,
        val name: String?,
        val group: String?,
        val language: String?,
        val logo: String?,
        val mpd_url: String?,
        val license_url: String?,
        val headers: Map<String, String>?,
        val expires_in: Long?
    )

    data class TargetChannel(
        val group: String,
        val logo: String,
        val name: String,
        val title: String,
        val uris: List<String>,
        val headers: Map<String, String>? = null
    )

    @Test
    fun convertChannels() {
        val url = "https://cloudplay-app.cloudplay-help.workers.dev/jiotv?password=all"
        println("Fetching data from $url...")
        
        try {
            val jsonString = URL(url).readText()
            println("Fetched ${jsonString.length} chars.")

            val gson = GsonBuilder().setPrettyPrinting().create()
            val sourceType = object : TypeToken<List<SourceChannel>>() {}.type
            val sourceList: List<SourceChannel> = gson.fromJson(jsonString, sourceType)

            println("Parsed ${sourceList.size} channels.")

            val targetList = sourceList.map { src ->
                TargetChannel(
                    group = src.group ?: "Uncategorized",
                    logo = src.logo ?: "",
                    name = src.name ?: "Unknown",
                    title = src.name ?: "Unknown",
                    uris = listOfNotNull(src.mpd_url),
                    headers = src.headers
                )
            }

            val convertedJson = gson.toJson(targetList)
            val outFile = File("converted_channels.json")
            outFile.writeText(convertedJson)
            
            println("Conversion successful. Saved to ${outFile.absolutePath}")

        } catch (e: Exception) {
            println("Error converting channels: ${e.message}")
            e.printStackTrace()
        }
    }
}
