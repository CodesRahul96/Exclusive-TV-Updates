package com.codesrahul.exclusivetv.models

import com.google.gson.annotations.SerializedName

enum class Type {
    @SerializedName("WEB")
    WEB,
    @SerializedName("HLS")
    HLS,
    @SerializedName("STREAM")
    STREAM,
}
