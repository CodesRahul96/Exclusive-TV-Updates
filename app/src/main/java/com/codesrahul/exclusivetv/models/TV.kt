package com.codesrahul.exclusivetv.models

import java.io.Serializable
import com.google.gson.annotations.SerializedName

data class TV(
    @SerializedName("id")
    var id: Int = 0,
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
    @SerializedName("child")
    var child: List<TV>,
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
