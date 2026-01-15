package com.codesrahul.exclusivetv.requests


data class TimeResponse(
    val data: Time
) {
    data class Time(
        val t: String
    )
}
