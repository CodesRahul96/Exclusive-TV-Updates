package com.codesrahul.exclusivetv.models

import java.io.Serializable

data class EPGProgram(
    val title: String,
    val start: Long,
    val stop: Long,
    val description: String = ""
) : Serializable
