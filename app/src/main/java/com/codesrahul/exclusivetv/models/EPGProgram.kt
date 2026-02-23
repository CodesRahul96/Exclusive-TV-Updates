package com.codesrahul.exclusivetv.models

import java.io.Serializable

data class EPGProgram(
    var title: String,
    val start: Long,
    val stop: Long,
    var description: String = ""
) : Serializable
