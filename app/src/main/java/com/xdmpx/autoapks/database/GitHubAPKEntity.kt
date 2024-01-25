package com.xdmpx.autoapks.database

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Update

@Entity(tableName = "GitHubAPK")
data class GitHubAPKEntity(
    @ColumnInfo(name = "repository") var repository: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "base_directory") var baseDirectory: String = "app",
    @ColumnInfo(name = "repository_default_branch") var repositoryDefaultBranch: String? = null,
    @ColumnInfo(name = "icon_url") var iconURL: String? = null,
    @ColumnInfo(name = "application_id") var applicationId: String? = null,
    @ColumnInfo(name = "application_name") var applicationName: String? = null,
    @ColumnInfo(name = "application_version_name") var applicationVersionName: String? = null,
    @ColumnInfo(name = "application_package_name") var applicationPackageName: String? = null,
    @ColumnInfo(name = "application_version_code") var applicationVersionCode: Long? = null,
    @ColumnInfo(name = "release_commit") var releaseCommit: String? = null,
    @ColumnInfo(name = "release_tag") var releaseTag: String? = null,
    @ColumnInfo(name = "release_link") var releaseLink: String? = null,
    @ColumnInfo(name = "to_update") var toUpdate: Boolean = false,
)

data class RepositoryExportData(
    @ColumnInfo(name = "repository") val repository: String,
    @ColumnInfo(name = "base_directory") val baseDirectory: String,
)
