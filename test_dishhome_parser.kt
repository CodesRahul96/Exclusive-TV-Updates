#!/usr/bin/env kscript
// Test DishHome Nepal playlist format parsing

import com.codesrahul.exclusivetv.models.M3UParser
import java.io.BufferedReader
import java.io.StringReader

fun testDishHomeFormat() {
    val playlistContent = """#EXTM3U
#EXTINF:-1 tvg-id="dh_cnn" tvg-chno="1" tvg-name="CNN" tvg-language="en" tvg-logo="https://example.com/cnn.png" group-title="News",CNN
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","kid":"test","k":"dGVzdGtleQ=="}]}
#EXTHTTP:{"cookie":"session=abc123","defaultDirection":"ltr"}
#EXTVLCOPT:http-user-agent=Mozilla/5.0 (X11; Linux x86_64)
#EXTVLCOPT:http-referer=https://example.com
https://ottlive.dishhome.com.np/protected/test/dash/manifest.mpd

#EXTINF:-1 tvg-id="dh_bbc" tvg-chno="2" tvg-name="BBC World" tvg-language="en" tvg-logo="https://example.com/bbc.png" group-title="News",BBC World
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://lic.example.com/widevine
#EXTHTTP:{"cookie":"session=xyz789","language":"en"}
#EXTVLCOPT:http-user-agent=VLC/3.0
https://ottlive.dishhome.com.np/protected/bbc/dash/manifest.mpd

#EXTINF:-1 ,Old Format Channel
https://simple.url.com/stream.m3u8
"""

    val reader = BufferedReader(StringReader(playlistContent))
    val channels = M3UParser.parse(reader)
    
    println("Parsed ${channels.size} channels")
    channels.forEach { channel ->
        println("\n=== ${channel.channelName} ===")
        println("ID: ${channel.tvgId}")
        println("Logo: ${channel.channelLogo}")
        println("Group: ${channel.groupTitle}")
        println("URLs: ${channel.streamUrl.size} sources")
        channel.streamUrl.forEach { url ->
            println("  - $url")
        }
        println("Headers: ${channel.headers.size}")
        channel.headers.forEach { (k, v) ->
            println("  $k: ${if (v.length > 50) v.substring(0, 50) + "..." else v}")
        }
        println("DRM Scheme: ${channel.drmScheme}")
        if (channel.drmLicense != null) {
            val licenseDisplay = if (channel.drmLicense!!.length > 50) 
                channel.drmLicense!!.substring(0, 50) + "..." 
            else 
                channel.drmLicense
            println("DRM License: $licenseDisplay")
        }
    }
    
    // Verify assertions
    assert(channels.size == 3) { "Expected 3 channels, got ${channels.size}" }
    
    // Check first channel (DishHome with multiple headers)
    val cnn = channels[0]
    assert(cnn.channelName == "CNN") { "Expected 'CNN', got '${cnn.channelName}'" }
    assert(cnn.drmScheme == "clearkey") { "Expected 'clearkey', got '${cnn.drmScheme}'" }
    assert(cnn.drmLicense != null && cnn.drmLicense!!.contains("keys")) { "DRM License should contain keys JSON" }
    assert(cnn.headers.containsKey("User-Agent")) { "Should have User-Agent header" }
    assert(cnn.headers.containsKey("Referer")) { "Should have Referer header" }
    
    // Check BBC channel (multiple URLs?)
    val bbc = channels[1]
    assert(bbc.channelName == "BBC World") { "Expected 'BBC World', got '${bbc.channelName}'" }
    assert(bbc.drmScheme == "widevine") { "Expected 'widevine', got '${bbc.drmScheme}'" }
    
    // Check old format still works
    val old = channels[2]
    assert(old.channelName == "Old Format Channel") { "Backward compatibility broken" }
    
    println("\n✓ All tests passed!")
}

testDishHomeFormat()
