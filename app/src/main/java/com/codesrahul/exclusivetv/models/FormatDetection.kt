package com.codesrahul.exclusivetv.models

import android.util.Log
import java.util.regex.Pattern

/**
 * Detects audio and video formats from:
 * - Stream URLs
 * - Manifest content (HLS/DASH)
 * - Metadata tags
 * - Header information
 */
object AudioFormatDetector {
    private const val TAG = "AudioFormatDetector"

    /**
     * Detect audio formats from stream URL
     */
    fun detectFromUrl(url: String): Set<AudioFormat> {
        val detected = mutableSetOf<AudioFormat>()
        val urlLower = url.lowercase()

        // Direct detection from file extensions and patterns
        AudioFormat.values().forEach { format ->
            format.aliases.forEach { alias ->
                if (urlLower.contains(".$alias") || urlLower.contains("_$alias")|| 
                    urlLower.contains("-$alias") || urlLower.contains("codec=$alias") ||
                    urlLower.contains("audio=$alias")) {
                    detected.add(format)
                }
            }
        }

        // Common audio format patterns
        when {
            urlLower.contains("ac3") || urlLower.contains("dolby-digital") -> detected.add(AudioFormat.AC3)
            urlLower.contains("eac3") || urlLower.contains("ec3") -> detected.add(AudioFormat.EAC3)
            urlLower.contains("atmos") || urlLower.contains("eac3-joc") -> {
                detected.add(AudioFormat.EAC3_JOC)
                detected.add(AudioFormat.EAC3)
            }
            urlLower.contains("truehd") -> detected.add(AudioFormat.TRUEHD)
            urlLower.contains("dts-x") || urlLower.contains("dtsx") -> {
                detected.add(AudioFormat.DTS_X)
                detected.add(AudioFormat.DTS_HD)
            }
            urlLower.contains("dts-hd") -> detected.add(AudioFormat.DTS_HD)
            urlLower.contains("dts") -> detected.add(AudioFormat.DTS)
            urlLower.contains("aac") -> {
                detected.add(AudioFormat.AAC)
                if (urlLower.contains("he")) detected.add(AudioFormat.AAC_HE)
                if (urlLower.contains("lc")) detected.add(AudioFormat.AAC_LC)
            }
            urlLower.contains("opus") -> detected.add(AudioFormat.OPUS)
            urlLower.contains("flac") -> detected.add(AudioFormat.FLAC)
            urlLower.contains("vorbis") -> detected.add(AudioFormat.VORBIS)
            urlLower.contains("mp3") || urlLower.contains("mpeg") -> detected.add(AudioFormat.MP3)
            urlLower.contains("alac") -> detected.add(AudioFormat.ALAC)
        }

        // Surround sound channels detection
        when {
            urlLower.contains("7.1") || urlLower.contains("7_1") -> detected.add(AudioFormat.SURROUND_71)
            urlLower.contains("5.1") || urlLower.contains("5_1") -> detected.add(AudioFormat.SURROUND)
            urlLower.contains("stereo") || urlLower.contains("2.0") -> detected.add(AudioFormat.STEREO)
            urlLower.contains("mono") || urlLower.contains("1.0") -> detected.add(AudioFormat.MONO)
        }

        return detected
    }

    /**
     * Detect audio formats from HLS/DASH manifest content
     */
    fun detectFromManifest(manifestContent: String): Set<AudioFormat> {
        val detected = mutableSetOf<AudioFormat>()
        val contentLower = manifestContent.lowercase()

        // HLS #EXT-X-MEDIA tags with audio codec info
        val mediaPattern = Pattern.compile("""#EXT-X-MEDIA:[^\n]*TYPE=AUDIO[^\n]*CODECS="([^"]*)""")
        var matcher = mediaPattern.matcher(manifestContent)
        while (matcher.find()) {
            val codec = matcher.group(1)?.let { it } ?: continue
            detectFromCodecString(codec).forEach { detected.add(it) }
        }

        // DASH AdaptationSet with audio codec info
        val dashAudioPattern = Pattern.compile("""mimeType="audio/[^"]*"[^>]*codecs="([^"]*)""")
        matcher = dashAudioPattern.matcher(manifestContent)
        while (matcher.find()) {
            val codec = matcher.group(1)?.let { it } ?: continue
            detectFromCodecString(codec).forEach { detected.add(it) }
        }

        // Generic codec detection in manifest
        val codecPattern = Pattern.compile("""codecs="([^"]*)""")
        matcher = codecPattern.matcher(manifestContent)
        while (matcher.find()) {
            val codec = matcher.group(1)?.lowercase() ?: continue
            if (codec.startsWith("a")) {  // Audio codec
                detectFromCodecString(codec).forEach { detected.add(it) }
            }
        }

        // Look for known strings
        when {
            contentLower.contains("ac-3") || contentLower.contains("\"ac-3\"") || 
            contentLower.contains("ac3") -> detected.add(AudioFormat.AC3)
            contentLower.contains("ec-3") || contentLower.contains("\"ec-3\"") ||
            contentLower.contains("eac3") -> detected.add(AudioFormat.EAC3)
            contentLower.contains("alac") -> detected.add(AudioFormat.ALAC)
            contentLower.contains("opus") -> detected.add(AudioFormat.OPUS)
            contentLower.contains("flac") -> detected.add(AudioFormat.FLAC)
            contentLower.contains("vorbis") -> detected.add(AudioFormat.VORBIS)
        }

        // Dolby specific detection
        if (contentLower.contains("dolby-atmos") || contentLower.contains("dolby atmos") ||
            contentLower.contains("joc")) {
            detected.add(AudioFormat.EAC3_JOC)
        }
        if (contentLower.contains("dolby") && detected.isEmpty()) {
            detected.add(AudioFormat.AC3)
        }

        // DTS detection
        when {
            contentLower.contains("dts-x") -> {
                detected.add(AudioFormat.DTS_X)
                detected.add(AudioFormat.DTS_HD)
            }
            contentLower.contains("dts-hd") -> detected.add(AudioFormat.DTS_HD)
            contentLower.contains("\"dts\"") || contentLower.contains("dts") -> detected.add(AudioFormat.DTS)
        }

        return detected
    }

    /**
     * Detect from codec string (e.g., "mp4a.40.2", "ac-3", "ec-3")
     */
    fun detectFromCodecString(codecString: String): Set<AudioFormat> {
        val detected = mutableSetOf<AudioFormat>()
        val codec = codecString.lowercase().trim().trim('"')

        when {
            codec.startsWith("ac-3") || codec == "ac3" -> detected.add(AudioFormat.AC3)
            codec.startsWith("ec-3") || codec == "ec3" || codec == "eac3" -> {
                detected.add(AudioFormat.EAC3)
                if (codec.contains("joc")) detected.add(AudioFormat.EAC3_JOC)
            }
            codec.startsWith("mp4a") -> {
                // mp4a.40.2 = AAC-LC, mp4a.40.5 = AAC-HE
                when {
                    codec.contains(".40.2") -> detected.add(AudioFormat.AAC_LC)
                    codec.contains(".40.5") -> detected.add(AudioFormat.AAC_HE)
                    codec.contains(".40.29") -> {
                        detected.add(AudioFormat.AAC_HE)
                        detected.add(AudioFormat.AAC_HE_V2)
                    }
                    else -> {
                        detected.add(AudioFormat.AAC)
                        if (codec.contains("he")) detected.add(AudioFormat.AAC_HE)
                    }
                }
            }
            codec.contains("opus") -> detected.add(AudioFormat.OPUS)
            codec.contains("vorbis") -> detected.add(AudioFormat.VORBIS)
            codec.contains("flac") -> detected.add(AudioFormat.FLAC)
            codec.contains("alac") -> detected.add(AudioFormat.ALAC)
            codec.contains("dts-x") || codec.contains("dtsx") -> {
                detected.add(AudioFormat.DTS_X)
                detected.add(AudioFormat.DTS_HD)
            }
            codec.contains("dts-hd") || codec.contains("dts-hd-ma") -> detected.add(AudioFormat.DTS_HD)
            codec.contains("dts") -> detected.add(AudioFormat.DTS)
            codec == "mp3" || codec.startsWith("mp3") -> detected.add(AudioFormat.MP3)
        }

        return detected
    }

    /**
     * Detect video codec from codec string
     */
    fun detectVideoCodecFromString(codecString: String): VideoCodec? {
        val codec = codecString.lowercase()
        return when {
            codec.startsWith("avc") || codec.startsWith("h264") || codec == "avc1" -> VideoCodec.H264
            codec.startsWith("hev") || codec.startsWith("h265") || codec == "hvc1" -> VideoCodec.H265
            codec.startsWith("av1") -> VideoCodec.AV1
            codec.startsWith("vp8") -> VideoCodec.VP8
            codec.startsWith("vp9") -> VideoCodec.VP9
            codec.startsWith("mp2v") -> VideoCodec.MPEG2
            else -> null
        }
    }

    /**
     * Detect from metadata tags in M3U content
     */
    fun detectFromM3UMetadata(extinf: String, kodipropLines: List<String>): Set<AudioFormat> {
        val detected = mutableSetOf<AudioFormat>()

        // Check EXTINF for audio info
        when {
            extinf.contains("dolby-atmos", ignoreCase = true) -> {
                detected.add(AudioFormat.EAC3_JOC)
                detected.add(AudioFormat.EAC3)
            }
            extinf.contains("ac3", ignoreCase = true) -> detected.add(AudioFormat.AC3)
            extinf.contains("eac3", ignoreCase = true) -> detected.add(AudioFormat.EAC3)
            extinf.contains("aac", ignoreCase = true) -> detected.add(AudioFormat.AAC)
            extinf.contains("opus", ignoreCase = true) -> detected.add(AudioFormat.OPUS)
        }

        // Check KODIPROP lines
        kodipropLines.forEach { line ->
            when {
                line.contains("inputstream.adaptive.license_type", ignoreCase = true) &&
                line.contains("clearkey", ignoreCase = true) -> {
                    // May indicate Dolby if in clearkey DRM
                }
            }
        }

        return detected
    }
}

/**
 * Checks device compatibility for audio and video formats
 */
object DeviceCompatibilityChecker {
    private const val TAG = "DeviceCompatibilityChecker"

    /**
     * Get compatible audio formats for a specific device
     */
    fun getCompatibleFormats(
        deviceType: DeviceType,
        availableFormats: Set<AudioFormat>
    ): Set<AudioFormat> {
        return availableFormats.filter { it in deviceType.supportedAudioFormats }.toSet()
    }

    /**
     * Check if a device supports the content
     */
    fun isDeviceCompatible(
        deviceType: DeviceType,
        audioFormats: Set<AudioFormat>
    ): Boolean {
        if (audioFormats.isEmpty()) return true  // Default compatible

        return audioFormats.any { it in deviceType.supportedAudioFormats }
    }

    /**
     * Get recommended device types for content with specific formats
     */
    fun getRecommendedDevices(
        audioFormats: Set<AudioFormat>,
        hasAtmos: Boolean = false
    ): Set<DeviceType> {
        val recommended = mutableSetOf<DeviceType>()

        DeviceType.values().forEach { device ->
            if (audioFormats.isEmpty() || audioFormats.any { it in device.supportedAudioFormats }) {
                recommended.add(device)
            }
        }

        // Filter based on advanced features
        if (hasAtmos) {
            // Only devices that support Dolby Atmos
            return recommended.filter { device ->
                device in setOf(
                    DeviceType.FIRETV_STICK_4K,
                    DeviceType.FIRETV_CUBE,
                    DeviceType.ANDROIDTV_11_PLUS,
                    DeviceType.SMART_TV
                )
            }.toSet()
        }

        return recommended
    }

    /**
     * Analyze content and return compatibility score (0-100)
     */
    fun getCompatibilityScore(
        deviceType: DeviceType,
        tv: TV
    ): Int {
        var score = 0

        // Base score for device in compatible list
        if ("${deviceType.name.lowercase()}" in tv.compatibleDevices) {
            score += 20
        }

        // Audio format compatibility
        if (tv.audioFormats.isNotEmpty()) {
            val audioSet = tv.audioFormats.mapNotNull { AudioFormat.fromString(it) }.toSet()
            val compatible = getCompatibleFormats(deviceType, audioSet)
            score += (compatible.size * 100) / audioSet.size
        } else {
            score += 30  // Default compatibility
        }

        // Special features
        if (tv.dolbyAtmos && AudioFormat.EAC3_JOC in deviceType.supportedAudioFormats) {
            score += 20
        }
        if (tv.hdrEnabled && deviceType in setOf(
            DeviceType.FIRETV_STICK_4K,
            DeviceType.FIRETV_CUBE,
            DeviceType.ANDROIDTV,
            DeviceType.SMART_TV
        )) {
            score += 10
        }

        return minOf(score, 100)
    }
}
