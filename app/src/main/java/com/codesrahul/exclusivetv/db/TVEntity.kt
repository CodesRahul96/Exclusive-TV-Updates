package com.codesrahul.exclusivetv.db

import androidx.room.*
import com.codesrahul.exclusivetv.models.Type
import java.io.Serializable

@Entity(tableName = "channels")
data class TVEntity(
    @PrimaryKey(autoGenerate = true)
    var uId: Int = 0,
    var id: Int = 0,
    var apiId: String = "",
    var name: String = "",
    var title: String = "",
    var description: String? = null,
    var logo: String = "",
    var image: String? = null,
    var uris: List<String>,
    var headers: Map<String, String>? = null,
    var group: String = "",
    var type: String = "WEB", // Store enum name
    var drmScheme: String? = null,
    var drmLicenseUrl: String? = null,
    var catchupType: String? = null,
    var catchupDays: String? = null,
    var catchupSource: String? = null,
    var childJson: String? = null // Store children as JSON for simplicity
) : Serializable
