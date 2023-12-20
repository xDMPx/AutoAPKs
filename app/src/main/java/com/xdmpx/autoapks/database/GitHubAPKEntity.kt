package com.xdmpx.autoapks.database

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "GitHubAPK")
data class GitHubAPKEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "repository") var repository: String,
    @ColumnInfo(name = "icon_url") var iconURL: String?,
    @ColumnInfo(name = "application_id") var applicationId: String?,
    @ColumnInfo(name = "application_name") var applicationName: String?,
    @ColumnInfo(name = "release_tag") var releaseTag: String?,
    @ColumnInfo(name = "release_link") var releaseLink: String?,
)
