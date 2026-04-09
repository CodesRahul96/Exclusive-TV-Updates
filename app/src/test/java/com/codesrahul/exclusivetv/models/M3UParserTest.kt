package com.codesrahul.exclusivetv.models

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
class M3UParserTest {

    @Test
    fun testConsecutiveExtHttpChannels() {
    val testM3u = """
#EXTM3U
#EXTINF:-1 tvg-id="1368" tvg-language="Hindi" group-title="JIO ⭕|Entertainment" tvg-logo="https://jiotv.catchup.cdn.jio.com/dare_images/images/Colors_SD.png",Colors SD
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=https://keys.vodep39240327.workers.dev/key/1368
#EXTVLCOPT:http-user-agent=plaYtv/7.1.3 (Linux;Android 13) ygx/824.1 ExoPlayerLib/824.0
#EXTHTTP:{"Origin":"https://jiotv.com/","Referer":"https://jiotv.com/"}
https://keys.vodep39240327.workers.dev/mpd/1368

#EXTINF:-1 tvg-id="144" tvg-language="Hindi" group-title="JIO ⭕|Entertainment" tvg-logo="https://jiotv.catchup.cdn.jio.com/dare_images/images/ColorsHD.png",Colors HD
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=https://keys.vodep39240327.workers.dev/key/144
#EXTVLCOPT:http-user-agent=plaYtv/7.1.3 (Linux;Android 13) ygx/824.1 ExoPlayerLib/824.0
#EXTHTTP:{"Origin":"https://jiotv.com/","Referer":"https://jiotv.com/"}
https://keys.vodep39240327.workers.dev/mpd/144
    """.trimIndent()

    val reader = BufferedReader(StringReader(testM3u))
    val channels = M3UParser.parse(reader)
    
    assertEquals(2, channels.size)
    
    val ch1 = channels[0]
    assertEquals("Colors SD", ch1.name)
    assertEquals("https://keys.vodep39240327.workers.dev/mpd/1368", ch1.uris[0])
    assertEquals("clearkey", ch1.drmScheme)
    assertEquals("https://keys.vodep39240327.workers.dev/key/1368", ch1.drmLicenseUrl)
    assertNotNull(ch1.headers)
    assertEquals("https://jiotv.com/", ch1.headers!!["Origin"])
    assertEquals("plaYtv/7.1.3 (Linux;Android 13) ygx/824.1 ExoPlayerLib/824.0", ch1.headers!!["User-Agent"])

    val ch2 = channels[1]
    assertEquals("Colors HD", ch2.name)
    assertEquals("https://keys.vodep39240327.workers.dev/mpd/144", ch2.uris[0])
    assertEquals("clearkey", ch2.drmScheme)
    assertEquals("https://keys.vodep39240327.workers.dev/key/144", ch2.drmLicenseUrl)
    assertNotNull(ch2.headers)
    assertEquals("https://jiotv.com/", ch2.headers!!["Referer"])
    assertEquals("plaYtv/7.1.3 (Linux;Android 13) ygx/824.1 ExoPlayerLib/824.0", ch2.headers!!["User-Agent"])
}

    @Test
    fun testDishhomeFormat() {
        val testM3u = """
# EXTVLCOPT:http-referrer=https://dishhomego.com.np/
https://ottlive.dishhome.com.np/protected/Ybdn8poB2gHDw8GGqcPT/dash/manifest.mpd

# EXTVLCOPT:http-referrer=https://dishhomego.com.np/
https://ottlive.dishhome.com.np/protected/5gTOzZkB0RDJ74Ryniqo/dash/manifest.mpd
        """.trimIndent()

        val reader = BufferedReader(StringReader(testM3u))
        val channels = M3UParser.parse(reader)

        assertEquals(2, channels.size)
        
        assertEquals("ExclusiveTV 1", channels[0].name)
        assertEquals("https://ottlive.dishhome.com.np/protected/Ybdn8poB2gHDw8GGqcPT/dash/manifest.mpd", channels[0].uris[0])
        assertEquals("https://dishhomego.com.np/", channels[0].headers!!["Referer"])

        assertEquals("ExclusiveTV 2", channels[1].name)
        assertEquals("https://ottlive.dishhome.com.np/protected/5gTOzZkB0RDJ74Ryniqo/dash/manifest.mpd", channels[1].uris[0])
        assertEquals("https://dishhomego.com.np/", channels[1].headers!!["Referer"])
    }

    @Test
    fun testMaxTvFormat() {
        val testM3u = """
# EXTVLCOPT:http-user-agent=SecureTV OkHttp
http://103.154.47.247/x-media/C94/master.m3u8

# EXTVLCOPT:http-user-agent=SecureTV OkHttp
http://103.154.47.247/x-media/C207/master.m3u8
        """.trimIndent()

        val reader = BufferedReader(StringReader(testM3u))
        val channels = M3UParser.parse(reader)

        assertEquals(2, channels.size)
        // 1st channel
        assertEquals("C94 master", channels[0].name) // due to URL extraction
        assertEquals("http://103.154.47.247/x-media/C94/master.m3u8", channels[0].uris[0])
        assertEquals("SecureTV OkHttp", channels[0].headers!!["User-Agent"])

        // 2nd channel
        assertEquals("C207 master", channels[1].name) // due to URL extraction
        assertEquals("http://103.154.47.247/x-media/C207/master.m3u8", channels[1].uris[0])
        assertEquals("SecureTV OkHttp", channels[1].headers!!["User-Agent"])
    }
}
