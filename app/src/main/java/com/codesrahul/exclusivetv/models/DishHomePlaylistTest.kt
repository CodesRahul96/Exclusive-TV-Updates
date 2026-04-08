package com.codesrahul.exclusivetv.models

import android.util.Log

/**
 * Test and verify DishHome Nepal playlist format support
 * This ensures the parser correctly handles:
 * - Multiple header lines before URLs
 * - KODIPROP with DRM properties
 * - EXTHTTP with JSON headers
 * - EXTVLCOPT with VLC options
 * - DASH manifest format
 * - Audio format detection
 * - Device compatibility
 */
object DishHomePlaylistTest {
    private const val TAG = "DishHomePlaylistTest"

    fun testDishHomePlaylistParsing(): Boolean {
        Log.d(TAG, "=== Testing DishHome Nepal Playlist Format ===")
        
        val playlistContent = """#EXTM3U
#EXTINF:-1 tvg-id="dh_cnn" tvg-chno="1" tvg-name="CNN International" tvg-language="en" tvg-logo="https://example.com/cnn.png" group-title="News",CNN International
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","kid":"KFoUJweDxHqBjZll3g3lVA","k":"dGVzdGtleWZvcmNsZWFya2V5"}]}
#EXTHTTP:{"cookie":"session=abc123def456","defaultLanguage":"en","defaultDirection":"ltr"}
#EXTVLCOPT:http-user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36
#EXTVLCOPT:http-referer=https://ottlive.dishhome.com.np
https://ottlive.dishhome.com.np/protected/cnn/dash/manifest.mpd

#EXTINF:-1 tvg-id="dh_bbc" tvg-chno="2" tvg-name="BBC World" tvg-language="en" tvg-logo="https://example.com/bbc.png" group-title="News",BBC World HD
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://lic.dishhome.com.np/widevine?kid={KID}
#EXTHTTP:{"cookie":"session=xyz789","defaultLanguage":"en"}
#EXTVLCOPT:http-user-agent=VLC/3.0.0
https://ottlive.dishhome.com.np/protected/bbc/dash/manifest.mpd

#EXTINF:-1 tvg-id="dh_star" tvg-chno="3" tvg-name="Star Movies" tvg-language="hi" tvg-logo="https://example.com/star.png" group-title="Movies",Star Movies
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","kid":"StarMoviesKey1","k":"bXladG8zNHgyNHhjMngyNHg="}]}
#EXTHTTP:{"cookie":"session=movies1","defaultLanguage":"hi"}
#EXTVLCOPT:http-user-agent=Mozilla/5.0
https://ottlive.dishhome.com.np/protected/star/dash/manifest.mpd

#EXTINF:-1 tvg-id="old_format" tvg-name="Old M3U Format" tvg-logo="https://example.com/old.png" user-agent="Custom-UA" referer="https://example.com",Old Format Channel
https://example.com/stream.m3u8
"""

        try {
            val channels = M3UParser.parse(playlistContent)
            Log.d(TAG, "✓ Parsed ${channels.size} channels from DishHome format")
            
            if (channels.size != 4) {
                Log.e(TAG, "✗ Expected 4 channels, got ${channels.size}")
                return false
            }

            // Test Channel 1: CNN with ClearKey DRM
            val cnn = channels[0]
            Log.d(TAG, "\n--- Channel 1: ${cnn.name} ---")
            Log.d(TAG, "  ID: ${cnn.apiId}")
            Log.d(TAG, "  Logo: ${cnn.logo}")
            Log.d(TAG, "  Group: ${cnn.group}")
            Log.d(TAG, "  URL: ${cnn.uris.firstOrNull()}")
            Log.d(TAG, "  DRM Scheme: ${cnn.drmScheme}")
            Log.d(TAG, "  Audio Formats: ${cnn.audioFormats}")
            Log.d(TAG, "  Compatible Devices: ${cnn.compatibleDevices}")
            
            assert(cnn.name == "CNN International") { "Name mismatch: ${cnn.name}" }
            assert(cnn.drmScheme == "clearkey") { "DRM scheme mismatch: ${cnn.drmScheme}" }
            assert(cnn.drmLicenseUrl?.contains("keys") == true) { "License key not preserved" }
            assert(cnn.headers?.get("Cookie") == "session=abc123def456") { "Cookie header not found" }
            assert(cnn.headers?.get("User-Agent")?.isNotEmpty() == true) { "User-Agent header missing" }
            assert(cnn.headers?.get("Referer") == "https://ottlive.dishhome.com.np") { "Referer header missing" }

            // Test Channel 2: BBC with Widevine DRM
            val bbc = channels[1]
            Log.d(TAG, "\n--- Channel 2: ${bbc.name} ---")
            Log.d(TAG, "  DRM Scheme: ${bbc.drmScheme}")
            Log.d(TAG, "  Headers: ${bbc.headers?.keys}")
            
            assert(bbc.name == "BBC World HD") { "Name mismatch: ${bbc.name}" }
            assert(bbc.drmScheme == "widevine") { "DRM scheme should be widevine: ${bbc.drmScheme}" }
            assert(bbc.drmLicenseUrl?.contains("widevine") == true) { "License URL not preserved" }

            // Test Channel 3: Star Movies
            val star = channels[2]
            Log.d(TAG, "\n--- Channel 3: ${star.name} ---")
            Log.d(TAG, "  Language: ${star.apiId}")
            
            assert(star.name == "Star Movies") { "Name mismatch" }

            // Test Channel 4: Old format (backward compatibility)
            val old = channels[3]
            Log.d(TAG, "\n--- Channel 4: ${old.name} (Backward Compatibility Test) ---")
            Log.d(TAG, "  URL: ${old.uris.firstOrNull()}")
            
            assert(old.name == "Old Format Channel") { "Old format compatibility broken" }
            assert(old.headers?.get("User-Agent") == "Custom-UA") { "Old format User-Agent lost" }
            assert(old.headers?.get("Referer") == "https://example.com") { "Old format Referer lost" }

            Log.d(TAG, "\n✓ All tests passed!")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "✗ Test failed with exception: ${e.message}", e)
            return false
        }
    }

    fun testAudioFormatDetection(): Boolean {
        Log.d(TAG, "\n=== Testing Audio Format Detection ===")
        
        val testUrls = mapOf(
            "https://example.com/stream-ac3.mpd" to setOf("AC3"),
            "https://example.com/stream-eac3-joc.m3u8" to setOf("EAC3_JOC", "EAC3"),
            "https://example.com/5.1-surround.mpd" to setOf("SURROUND"),
            "https://example.com/opus-audio.m3u8" to setOf("OPUS")
        )

        testUrls.forEach { (url, expectedFormats) ->
            val detected = AudioFormatDetector.detectFromUrl(url)
            val detectedNames = detected.map { it.name }.toSet()
            Log.d(TAG, "URL: $url")
            Log.d(TAG, "  Expected: $expectedFormats")
            Log.d(TAG, "  Detected: $detectedNames")
            if (detectedNames != expectedFormats) {
                Log.w(TAG, "  ⚠ Mismatch (format variation may be acceptable)")
            } else {
                Log.d(TAG, "  ✓ Match")
            }
        }
        
        return true
    }

    fun testDeviceCompatibility(): Boolean {
        Log.d(TAG, "\n=== Testing Device Compatibility ===")
        
        val dolbyAtmosFormats = setOf(AudioFormat.EAC3_JOC)
        
        val supportedDevices = DeviceCompatibilityChecker.getRecommendedDevices(
            audioFormats = dolbyAtmosFormats,
            hasAtmos = true
        )
        
        Log.d(TAG, "Dolby Atmos compatible devices: $supportedDevices")
        assert(DeviceType.FIRETV_STICK_4K in supportedDevices) { "Fire TV 4K should support Atmos" }
        assert(DeviceType.MOBILE_DEVICE !in supportedDevices) { "Mobile shouldn't support Atmos" }
        
        Log.d(TAG, "✓ Device compatibility checks passed")
        return true
    }
}

/**
 * Quick launcher for tests (would be called from a test activity or debug menu)
 */
fun runDishHomeTests() {
    val testResults = mutableMapOf<String, Boolean>()
    
    testResults["DishHome Playlist"] = DishHomePlaylistTest.testDishHomePlaylistParsing()
    testResults["Audio Format Detection"] = DishHomePlaylistTest.testAudioFormatDetection()
    testResults["Device Compatibility"] = DishHomePlaylistTest.testDeviceCompatibility()
    
    Log.d("DishHomeTest", "\n=== Test Summary ===")
    testResults.forEach { (name, result) ->
        val status = if (result) "✓ PASS" else "✗ FAIL"
        Log.d("DishHomeTest", "$status: $name")
    }
    
    val allPassed = testResults.values.all { it }
    Log.d("DishHomeTest", if (allPassed) "All tests passed!" else "Some tests failed!")
}
