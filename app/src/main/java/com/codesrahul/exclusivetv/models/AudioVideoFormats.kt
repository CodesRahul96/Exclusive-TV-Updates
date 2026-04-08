package com.codesrahul.exclusivetv.models

/**
 * Supported audio codecs and formats for streaming
 * Used for device compatibility and format detection
 */
enum class AudioFormat(val label: String, val mimeType: String? = null, val aliases: Set<String> = emptySet()) {
    // AAC Variants
    AAC("AAC", "audio/aac", setOf("aac", "aac-lc")),
    AAC_LC("AAC-LC", "audio/aac", setOf("aac-lc", "lc")),
    AAC_HE("AAC-HE", "audio/aac", setOf("aac-he", "he-aac", "he")),
    AAC_HE_V2("AAC-HE v2", "audio/aac", setOf("aac-he-v2", "he-aac-v2")),
    
    // Dolby Digital
    AC3("Dolby Digital (AC3)", "audio/ac3", setOf("ac3", "dolby-digital")),
    EAC3("Dolby Digital+ (EAC3)", "audio/eac3", setOf("eac3", "ec3", "dolby-digital-plus")),
    EAC3_JOC("Dolby Atmos (EAC3-JOC)", "audio/eac3", setOf("eac3-joc", "atmos")),
    TRUEHD("Dolby TrueHD", "audio/truehd", setOf("truehd", "true-hd")),
    
    // DTS
    DTS("DTS", "audio/vnd.dts", setOf("dts")),
    DTS_HD("DTS-HD", "audio/vnd.dts", setOf("dts-hd", "dts-hd-ma")),
    DTS_X("DTS:X", "audio/vnd.dts", setOf("dts-x", "dtsx")),
    
    // Other Formats
    OPUS("Opus", "audio/opus", setOf("opus")),
    FLAC("FLAC", "audio/flac", setOf("flac")),
    VORBIS("Vorbis", "audio/vorbis", setOf("vorbis")),
    MP3("MP3", "audio/mpeg", setOf("mp3", "mpeg")),
    ALAC("ALAC", "audio/alac", setOf("alac")),
    MONO("Mono", null, setOf("mono")),
    STEREO("Stereo", null, setOf("stereo", "2.0")),
    SURROUND("5.1", null, setOf("5.1", "surround")),
    SURROUND_71("7.1", null, setOf("7.1"));

    companion object {
        fun fromString(value: String): AudioFormat? {
            return values().find { format ->
                format.name.equals(value, ignoreCase = true) ||
                format.label.equals(value, ignoreCase = true) ||
                format.aliases.any { it.equals(value, ignoreCase = true) }
            }
        }

        fun detectFromUrl(url: String): Set<AudioFormat> {
            val detected = mutableSetOf<AudioFormat>()
            val urlLower = url.lowercase()
            
            values().forEach { format ->
                if (format.aliases.any { alias ->
                        urlLower.contains(".$alias") || urlLower.contains("_$alias") || urlLower.contains("-$alias")
                    }) {
                    detected.add(format)
                }
            }
            
            return detected
        }
    }
}

/**
 * Device compatibility flags
 * Used to determine which devices can play content
 */
enum class DeviceType(val label: String, val supportedAudioFormats: Set<AudioFormat>) {
    FIRETV_STICK_4K(
        "Fire TV Stick 4K",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.AC3, AudioFormat.EAC3, AudioFormat.EAC3_JOC, AudioFormat.TRUEHD,
            AudioFormat.FLAC, AudioFormat.OPUS, AudioFormat.MP3
        )
    ),
    FIRETV_CUBE(
        "Fire TV Cube",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.AC3, AudioFormat.EAC3, AudioFormat.EAC3_JOC, AudioFormat.TRUEHD,
            AudioFormat.DTS, AudioFormat.DTS_HD, AudioFormat.DTS_X,
            AudioFormat.FLAC, AudioFormat.OPUS, AudioFormat.MP3
        )
    ),
    ANDROIDTV(
        "Android TV",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.AC3, AudioFormat.EAC3, AudioFormat.EAC3_JOC,
            AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.FLAC, AudioFormat.MP3
        )
    ),
    ANDROIDTV_11_PLUS(
        "Android TV 11+",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.AC3, AudioFormat.EAC3, AudioFormat.EAC3_JOC, AudioFormat.TRUEHD,
            AudioFormat.DTS, AudioFormat.DTS_HD,
            AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.FLAC, AudioFormat.MP3
        )
    ),
    MOBILE_DEVICE(
        "Mobile Phone/Tablet",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.FLAC, AudioFormat.MP3
        )
    ),
    SMART_TV(
        "Smart TV",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE, AudioFormat.AAC_HE_V2,
            AudioFormat.AC3, AudioFormat.EAC3, AudioFormat.EAC3_JOC,
            AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.FLAC, AudioFormat.MP3
        )
    ),
    WEB_BROWSER(
        "Web Browser",
        setOf(
            AudioFormat.AAC, AudioFormat.AAC_LC, AudioFormat.AAC_HE,
            AudioFormat.OPUS, AudioFormat.VORBIS, AudioFormat.MP3
        )
    );

    fun supportsAudioFormat(format: AudioFormat?): Boolean {
        return format != null && format in supportedAudioFormats
    }

    fun getCompatibleFormats(available: Set<AudioFormat>): Set<AudioFormat> {
        return available.filter { it in supportedAudioFormats }.toSet()
    }
}

/**
 * Video codec information
 */
enum class VideoCodec(val label: String, val mimeType: String? = null) {
    H264("H.264", "video/avc"),
    H265("H.265/HEVC", "video/hevc"),
    AV1("AV1", "video/av1"),
    VP8("VP8", "video/vp8"),
    VP9("VP9", "video/vp9"),
    MPEG2("MPEG-2", "video/mpeg");

    companion object {
        fun fromString(value: String): VideoCodec? {
            return values().find { codec ->
                codec.name.equals(value, ignoreCase = true) ||
                codec.label.equals(value, ignoreCase = true) ||
                value.contains(codec.name, ignoreCase = true)
            }
        }
    }
}

/**
 * Subtitle information for a stream
 */
data class SubtitleTrack(
    val language: String,          // e.g., "en", "es", "fr"
    val languageName: String = "", // e.g., "English", "Spanish"
    val url: String? = null,       // URL to subtitle file
    val format: String? = null,    // e.g., "srt", "vtt", "ass", "ssa", "subrip"
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val mimeType: String? = null   // e.g., "application/vnd.apple.mpegurl"
) : java.io.Serializable {
    companion object {
        fun fromString(input: String): SubtitleTrack? {
            // Parse format: "en:English:url|format" or "en:url" or just "en"
            val parts = input.split(":")
            if (parts.isEmpty()) return null

            val lang = parts[0]
            val langName = if (parts.size > 1) parts[1] else ""
            val urlAndFormat = if (parts.size > 2) parts[2] else ""
            
            val urlParts = urlAndFormat.split("|")
            val url = if (urlParts.isNotEmpty()) urlParts[0].ifEmpty { null } else null
            val format = if (urlParts.size > 1) urlParts[1] else null

            return SubtitleTrack(lang, langName, url, format)
        }
    }
}
