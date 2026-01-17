package com.codesrahul.exclusivetv

import org.junit.Test
import io.github.lizongying.Gua
import java.net.URL
import com.codesrahul.exclusivetv.models.TV
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DecryptionTest {
    @Test
    fun testDecryption() {
        println("Starting decryption test...")
        val url = "https://exclusive-tv-app-api.vercel.app/"
        try {
            val encoded = URL(url).readText().trim()
            println("Fetched ${encoded.length} bytes from API")
            
            if (encoded.isEmpty()) {
                println("ERROR: API returned empty response")
                return
            }

            val g = Gua()
            if (g.verify(encoded)) {
                println("Gua verification PASSED")
                val decoded = g.decode(encoded)
                println("Decrypted Content Preview (first 1000 chars):")
                println(decoded.take(1000))
                
                // Parse Check
                try {
                    val startIndex = decoded.indexOf('[')
                    if (startIndex != -1) {
                        val json = decoded.substring(startIndex)
                        val type = object : TypeToken<List<TV>>() {}.type
                        val list: List<TV> = Gson().fromJson(json, type)
                        println("SUCCESS: Parsed ${list.size} channels")
                        if (list.isNotEmpty()) {
                            println("First channel URIs: ${list[0].uris}")
                            println("First channel Group: ${list[0].group}")
                        }
                    } else {
                        println("ERROR: No JSON array start found")
                    }
                } catch (e: Exception) {
                    println("JSON PARSING ERROR: ${e.message}")
                    e.printStackTrace()
                }

            } else {
                println("ERROR: Gua verification FAILED")
            }

        } catch (e: Exception) {
            println("NETWORK/TEST ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
}
