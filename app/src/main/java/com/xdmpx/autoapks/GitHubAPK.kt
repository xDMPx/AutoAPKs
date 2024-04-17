package com.xdmpx.autoapks

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xdmpx.autoapks.ApkUI.ApkDialog
import com.xdmpx.autoapks.ApkUI.ApkInfo
import com.xdmpx.autoapks.ApkUI.ApkVersionControl
import com.xdmpx.autoapks.GitHubRepoFetcher.fetchDefaultRepoBranch
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GitHubAPK(
    private val apk: GitHubAPKEntity,
    private val context: Context,
    private val onRemove: (gitHubAPK: GitHubAPK) -> Unit
) {
    private val TAG_DEBUG = "GitHubAPK"
    private var database: GitHubAPKDao
    private var apkIcon: MutableState<String?> = mutableStateOf(apk.iconURL)
    private var apkLink: MutableState<String?> = mutableStateOf(apk.releaseLink)
    private var apkVersion: MutableState<String?> = mutableStateOf(apk.applicationVersionName)
    private var apkUpdate: MutableState<Boolean?> = mutableStateOf(apk.toUpdate)
    private var apkName: MutableState<String?> = mutableStateOf(deriveAppName())
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
        if (apk.repositoryDefaultBranch.isNullOrBlank()) {
            fetchDefaultRepoBranch(apk.repository, context) {
                apk.repositoryDefaultBranch = it
                updateDatabase()
                fetchIcon()
                fetchCurrentRelease()
                fetchAppInfo()
            }
        } else {
            fetchIcon()
            fetchCurrentRelease()
            fetchAppInfo()
        }
    }

    private fun fetchIcon() {
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
                    updateDatabase()
                }
            }
        }
    }

    private fun fetchAppInfo() {
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
                        updateDatabase()
                    }
                }
            }
            if (apk.applicationName == null) {
                GitHubRepoFetcher.requestApplicationName(
                    repository, branchName, baseDirectory, context
                ) { name ->
                    if (apk.applicationName != name) {
                        apk.applicationName = name
                        updateDatabase()
                    }
                }
            }
        }
        setPackageName()
        setInstalledApplicationVersion()
    }

    private fun fetchCurrentRelease() {
        GitHubRepoFetcher.requestLatestRelease(
            apk.repository, context
        ) { releaseCommit, releaseTag ->
            if (apk.releaseCommit != releaseCommit) {
                if (!apk.releaseCommit.isNullOrEmpty()) {
                    apk.toUpdate = true
                }
                apk.releaseCommit = releaseCommit
                updateDatabase()
            }
            if (apk.releaseTag != releaseTag) {
                apk.releaseTag = releaseTag
                updateDatabase()

                GitHubRepoFetcher.requestLatestReleaseAssets(
                    apk.repository, releaseTag, context
                ) { apkURL ->
                    if (apk.releaseLink != apkURL) {
                        apk.releaseLink = apkURL
                        updateDatabase()
                    }
                }
            }
        }
    }

    public fun refresh() {
        setPackageName()
        setInstalledApplicationVersion()
        fetchCurrentRelease()
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

    private fun updateDatabase() {
        apkIcon.value = apk.iconURL
        apkLink.value = apk.releaseLink
        apkName.value = deriveAppName()
        apkVersion.value = apk.applicationVersionName
        apkUpdate.value = apk.toUpdate
        setPackageName()
        setInstalledApplicationVersion()
        scope.launch { database.update(apk) }
    }

    private fun setPackageName() {
        setInstallState()
        if (apk.applicationPackageName != null) {
            return
        }
        val name = apkName.value
        Log.d(TAG_DEBUG, "setPackageName::${deriveAppName()}::$name")
        name?.let {
            val packageName = Utils.getAppPackageName(context, it)
            Log.d(TAG_DEBUG, "setPackageName:$name -> $packageName")
            if (apk.applicationPackageName != packageName) {
                apk.applicationPackageName = packageName
                updateDatabase()
            }
        }
    }

    private fun setInstallState() {
        Log.d(TAG_DEBUG, "setInstallState::${deriveAppName()}")
        apk.applicationPackageName?.let { packageName ->
            val installed = Utils.isAppInstalled(context, packageName)
            Log.d(TAG_DEBUG, "setInstallState::${deriveAppName()} -> $installed")
            if (!installed) {
                apk.applicationPackageName = null
                apk.applicationVersionCode = null
                apk.applicationVersionName = null
                updateDatabase()
            }
        }
    }

    private fun setInstalledApplicationVersion() {
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
                updateDatabase()
            }

        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ApkCard(modifier: Modifier = Modifier) {
        val showDialog = remember { mutableStateOf(false) }
        val haptics = LocalHapticFeedback.current
        val apkName by remember { apkName }
        val apkIcon by remember { apkIcon }
        val apkLink by remember { apkLink }
        val apkVersion by remember { apkVersion }
        val apkUpdate by remember { apkUpdate }
        val fetchNewIcon = {
            Log.d(TAG_DEBUG, "Fetching new icon {apkName.value}")
            apk.iconURL = null
            fetchIcon()
        }
        val onRemoveRequest = {
            scope.launch {
                database.delete(apk)
                onRemove(this@GitHubAPK)
            }
        }

        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .combinedClickable(onClick = {
                    apk.applicationPackageName?.let { Utils.openApplicationInfo(context, it) }
                }, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDialog.value = true
                })
        ) {
            ApkInfo(apkName, apk.repository, apkIcon, modifier.weight(0.75f), fetchNewIcon)
            ApkVersionControl(apkLink, apkVersion, apkUpdate, modifier.weight(0.25f))
        }

        if (showDialog.value) {
            ApkDialog(
                apkIcon,
                apk.repository,
                apk.applicationPackageName,
                apk.applicationVersionCode,
                apkLink,
                onDismissRequest = { showDialog.value = false },
                onRemoveRequest = onRemoveRequest,
                fetchNewIcon
            )
        }

    }


}
