package com.codesrahul.exclusivetv.models

import java.io.Serializable
import com.google.gson.annotations.SerializedName

data class TV(
    @SerializedName("internal_id")
    var id: Int = 0,
    @SerializedName("id")
    var apiId: String = "",
    @SerializedName("name")
    var name: String = "",
    @SerializedName("title")
    var title: String = "",
    @SerializedName("description")
    var description: String? = null,
    @SerializedName("logo")
    var logo: String = "",
    @SerializedName("image")
    var image: String? = null,
    @SerializedName("uris")
    var uris: List<String>,
    @SerializedName("headers")
    var headers: Map<String, String>? = null,
    @SerializedName("group")
    var group: String = "",
    @SerializedName("type")
    var type: Type = Type.WEB,
    @SerializedName("drm_scheme")
    var drmScheme: String? = null,
    @SerializedName("drm_license_url")
    var drmLicenseUrl: String? = null,
    @SerializedName("child")
    var child: List<TV>,
    @SerializedName("catchup_type")
    var catchupType: String? = null,
    @SerializedName("catchup_days")
    var catchupDays: String? = null,
    @SerializedName("catchup_source")
    var catchupSource: String? = null,
    @SerializedName("language")
    var language: String? = null,
    @SerializedName("country")
    var country: String? = null,
    @SerializedName("genre")
    var genre: String? = null,
    @SerializedName("is_audio_only")
    var isAudioOnly: Boolean = false,
    @SerializedName("is_webview_embed")
    var isWebViewEmbed: Boolean = false,
    
    // Audio/Video Format Support (Non-Breaking Addition)
    @SerializedName("audio_formats")
    var audioFormats: Set<String> = emptySet(),  // e.g., ["AAC", "AC3", "EAC3-JOC"]
    @SerializedName("audio_codec")
    var audioCodec: String? = null,              // Specific codec name
    @SerializedName("dolby_digital")
    var dolbyDigital: Boolean = false,           // Has Dolby Digital (AC3/EAC3)
    @SerializedName("dolby_atmos")
    var dolbyAtmos: Boolean = false,             // Has Dolby Atmos (EAC3-JOC)
    @SerializedName("dolby_truehd")
    var dolbyTrueHD: Boolean = false,            // Has Dolby TrueHD
    @SerializedName("video_codec")
    var videoCodec: String? = null,              // e.g., "H.264", "H.265", "AV1"
    @SerializedName("resolution")
    var resolution: String? = null,              // e.g., "1080p", "4K", "720p"
    @SerializedName("bitrate")
    var bitrate: String? = null,                 // e.g., "5000k", "10Mbps"
    @SerializedName("frame_rate")
    var frameRate: String? = null,               // e.g., "29.97", "60"
    @SerializedName("subtitles")
    var subtitles: List<SubtitleTrack> = emptyList(),  // Available subtitles
    @SerializedName("compatible_devices")
    var compatibleDevices: Set<String> = setOf(
        "firetv", "androidtv", "mobile", "web"
    ),  // Default compatible with most devices
    @SerializedName("hdr_enabled")
    var hdrEnabled: Boolean = false,             // HDR support
    @SerializedName("hdr_type")
    var hdrType: String? = null,                 // "HDR10", "Dolby Vision", "HLG", etc.
    @SerializedName("frame_packing")
    var framePacking: String? = null,            // "3D" support info
) : Serializable {

    override fun toString(): String {
        return "TV{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", logo='" + logo + '\'' +
                ", image='" + image + '\'' +
                ", uris='" + uris + '\'' +
                ", headers='" + headers + '\'' +
                ", group='" + group + '\'' +
                ", type='" + type + '\'' +
                '}'
    }
}
