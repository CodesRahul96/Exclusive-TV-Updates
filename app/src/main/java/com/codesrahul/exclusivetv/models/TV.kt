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
