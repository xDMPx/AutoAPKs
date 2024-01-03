package com.xdmpx.autoapks.database

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "GitHubAPK")
data class GitHubAPKEntity(
    @ColumnInfo(name = "repository") var repository: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "icon_url") var iconURL: String? = null,
    @ColumnInfo(name = "application_id") var applicationId: String? = null,
    @ColumnInfo(name = "application_name") var applicationName: String? = null,
    @ColumnInfo(name = "application_version_name") var applicationVersionName: String? = null,
    @ColumnInfo(name = "release_commit") var releaseCommit: String? = null,
    @ColumnInfo(name = "release_tag") var releaseTag: String? = null,
    @ColumnInfo(name = "release_link") var releaseLink: String? = null,
)
