package com.codesrahul.exclusivetv.models

import com.google.gson.annotations.SerializedName

data class JioTVChannel(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("group")
    val group: String? = null,
    
    @SerializedName("logo")
    val logo: String? = null,
    
    @SerializedName("mpd_url")
    val mpdUrl: String? = null,
    
    @SerializedName("license_url")
    val licenseUrl: String? = null,
    
    @SerializedName("headers")
    val headers: Map<String, String>? = null,
    
    @SerializedName("user_agent")
    val userAgent: String? = null,
    
    @SerializedName("type")
    val type: String? = null
)
