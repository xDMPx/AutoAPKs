package com.xdmpx.autoapks

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.xdmpx.autoapks.GitHubRepoFetcher.fetchDefaultRepoBranch
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class GitHubAPKState(
    val apkName: String?,
    val apkIcon: String?,
    val apkLink: String?,
    val apkVersion: String?,
    val apkUpdate: Boolean?,
)

class GitHubAPK(
    val apk: GitHubAPKEntity,
    context: Context,
    private val onRemove: (gitHubAPK: GitHubAPK) -> Unit
) : ViewModel() {
    private val _apkState = MutableStateFlow(
        GitHubAPKState(
            deriveAppName(), apk.iconURL, apk.releaseLink, apk.applicationVersionName, apk.toUpdate
        )
    )
    val apkState: StateFlow<GitHubAPKState> = _apkState.asStateFlow()
    private val TAG_DEBUG = "GitHubAPK"
    private var database: GitHubAPKDao

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
        if (apk.repositoryDefaultBranch.isNullOrBlank()) {
            fetchDefaultRepoBranch(apk.repository, context) {
                apk.repositoryDefaultBranch = it
                updateDatabase(context)
                fetchIcon(context)
                fetchCurrentRelease(context)
                fetchAppInfo(context)
            }
        } else {
            fetchIcon(context)
            fetchCurrentRelease(context)
            fetchAppInfo(context)
        }
    }

    private fun fetchIcon(context: Context) {
        val baseDirectory = apk.baseDirectory

        apk.repositoryDefaultBranch?.let { branchName ->
            if (apk.iconURL != null) {
                return
            }
            GitHubRepoFetcher.requestIcon(
                apk.repository, branchName, baseDirectory, context
            ) { iconUrl ->
                if (apk.iconURL != iconUrl) {
                    apk.iconURL = iconUrl
                    updateDatabase(context)
                }
            }
        }
    }

    private fun fetchAppInfo(context: Context) {
        val repository = apk.repository
        val baseDirectory = apk.baseDirectory

        apk.repositoryDefaultBranch?.let { branchName ->
            if (apk.applicationId == null && (apk.applicationName == null || apk.applicationName!!.startsWith(
                    "."
                ))
            ) {
                GitHubRepoFetcher.requestApplicationId(
                    repository, branchName, baseDirectory, context
                ) { applicationID ->
                    if (apk.applicationId != applicationID) {
                        apk.applicationId = applicationID
                        updateDatabase(context)
                    }
                }
            }
            if (apk.applicationName == null) {
                GitHubRepoFetcher.requestApplicationName(
                    repository, branchName, baseDirectory, context
                ) { name ->
                    if (apk.applicationName != name) {
                        apk.applicationName = name
                        updateDatabase(context)
                    }
                }
            }
        }
        setPackageName(context)
        setInstalledApplicationVersion(context)
    }

    private fun fetchCurrentRelease(context: Context) {
        GitHubRepoFetcher.requestLatestRelease(
            apk.repository, context
        ) { releaseCommit, releaseTag ->
            if (apk.releaseCommit != releaseCommit) {
                if (!apk.releaseCommit.isNullOrEmpty()) {
                    apk.toUpdate = true
                }
                apk.releaseCommit = releaseCommit
                updateDatabase(context)
            }
            if (apk.releaseTag != releaseTag) {
                apk.releaseTag = releaseTag
                updateDatabase(context)

                GitHubRepoFetcher.requestLatestReleaseAssets(
                    apk.repository, releaseTag, context
                ) { apkURL ->
                    if (apk.releaseLink != apkURL) {
                        apk.releaseLink = apkURL
                        updateDatabase(context)
                    }
                }
            }
        }
    }

    public fun refresh(context: Context) {
        setPackageName(context)
        setInstalledApplicationVersion(context)
        fetchCurrentRelease(context)
    }

    private fun deriveAppName(): String? {
        apk.applicationName?.let { name ->
            if (name.isBlank()) {
                if (apk.applicationId.isNullOrBlank()) return null
                apk.applicationId?.let {
                    return it
                }

            } else if (!name.startsWith('.')) {
                return name
            }
            apk.applicationId?.let {
                return "$it$name"
            }
        }
        apk.applicationId?.let {
            return it
        }
        return null
    }

    private fun updateDatabase(context: Context) {
        _apkState.value = GitHubAPKState(
            deriveAppName(), apk.iconURL, apk.releaseLink, apk.applicationVersionName, apk.toUpdate
        )
        setPackageName(context)
        setInstalledApplicationVersion(context)
        scope.launch { database.update(apk) }
    }

    private fun setPackageName(context: Context) {
        setInstallState(context)
        if (apk.applicationPackageName != null) {
            return
        }
        val name = _apkState.value.apkName
        Log.d(TAG_DEBUG, "setPackageName::${deriveAppName()}::$name")
        name?.let {
            val packageName = Utils.getAppPackageName(context, it)
            Log.d(TAG_DEBUG, "setPackageName:$name -> $packageName")
            if (apk.applicationPackageName != packageName) {
                apk.applicationPackageName = packageName
                updateDatabase(context)
            }
        }
    }

    private fun setInstallState(context: Context) {
        Log.d(TAG_DEBUG, "setInstallState::${deriveAppName()}")
        apk.applicationPackageName?.let { packageName ->
            val installed = Utils.isAppInstalled(context, packageName)
            Log.d(TAG_DEBUG, "setInstallState::${deriveAppName()} -> $installed")
            if (!installed) {
                apk.applicationPackageName = null
                apk.applicationVersionCode = null
                apk.applicationVersionName = null
                updateDatabase(context)
            }
        }
    }

    private fun setInstalledApplicationVersion(context: Context) {
        val packageName = apk.applicationPackageName
        Log.d(TAG_DEBUG, "setInstalledApplicationVersion::$packageName")
        packageName?.let {
            val version = Utils.getAppVersion(context, it)
            Log.d(TAG_DEBUG, "setInstalledApplicationVersion::$packageName -> $version")
            val versionName = version?.name
            val versionCode = version?.code
            var update = false
            // TODO: Check if the new version is actually newer
            if (apk.applicationVersionCode != versionCode) {
                Log.d(TAG_DEBUG, "setInstalledApplicationVersion::$packageName -> UPDATE DETECTED")
                apk.applicationVersionCode = versionCode
                apk.toUpdate = false
                update = true
            }
            if (apk.applicationVersionName != versionName) {
                apk.applicationVersionName = versionName
                update = true
            }
            if (update) {
                updateDatabase(context)
            }

        }
    }

    val fetchNewIcon = {
        Log.d(TAG_DEBUG, "Fetching new icon {apkName.value}")
        apk.iconURL = null
        fetchIcon(context)
    }
    val onRemoveRequest = {
        scope.launch {
            database.delete(apk)
            onRemove(this@GitHubAPK)
        }
    }

}
