package com.thirutricks.tllplayer

import org.junit.Test
import io.github.lizongying.Gua
import java.io.File

class ChannelDecoderTest {
    @Test
    fun decodeChannels() {
        // Try to locate the file relative to project root
        var file = File("app/src/main/res/raw/remote_channels.txt")
        if (!file.exists()) {
            // Try relative to module
            file = File("src/main/res/raw/remote_channels.txt")
        }
        
        if (!file.exists()) {
            println("File not found. CWD: " + File(".").absolutePath)
            return
        }
        val encoded = file.readText().trim()
        println("Read ${encoded.length} chars from file")
        
        try {
            val g = Gua()
            val decoded = g.decode(encoded)
            val outFile = File("decoded_channels.json")
            outFile.writeText(decoded)
            println("Decoded data written to ${outFile.absolutePath}")
        } catch (e: Exception) {
            println("DECODED_ERROR: " + e.message)
            e.printStackTrace()
        }
    }
}
