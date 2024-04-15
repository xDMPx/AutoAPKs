package com.xdmpx.autoapks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xdmpx.autoapks.ApkUI.AnnotatedClickableText
import com.xdmpx.autoapks.ApkUI.ApkIcon
import com.xdmpx.autoapks.ApkUI.ApkInfo
import com.xdmpx.autoapks.ApkUI.ApkInfoData
import com.xdmpx.autoapks.ApkUI.InstallButton
import com.xdmpx.autoapks.ApkUI.UpdateButton
import com.xdmpx.autoapks.GitHubRepoFetcher.fetchDefaultRepoBranch
import com.xdmpx.autoapks.Utils.CustomDialog
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.ui.theme.getColorSchemeEx
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
            ApkInfo(apkName, apk.repository, apkIcon, modifier.weight(0.75f)) {
                Log.d(TAG_DEBUG, "Fetching new icon {apkName.value}")
                apk.iconURL = null
                fetchIcon()
            }
            ApkVersionControl(modifier.weight(0.25f))
        }

        if (showDialog.value) {
            ApkDialog(onDismissRequest = { showDialog.value = false })
        }

    }

    @Composable
    fun ApkVersionControl(modifier: Modifier = Modifier) {
        val apkLink by remember { apkLink }
        val apkVersion by remember { apkVersion }
        val apkUpdate by remember { apkUpdate }

        Box(
            modifier.fillMaxSize()
        ) {
            if (apkVersion.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize()
                ) {
                    apkLink?.let { InstallButton(it) }
                }
            } else if (apkUpdate == true) {
                Box(
                    contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize()
                ) {
                    apkLink?.let { UpdateButton(it) }
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                ) {
                    apkVersion?.let { Text(it) }
                }
            }
        }
    }

    @Composable
    fun ApkDialog(onDismissRequest: () -> Unit) {
        val apkIcon by remember { apkIcon }

        CustomDialog(onDismissRequest) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    LazyRow(modifier = Modifier.height(30.dp)) {
                        item {
                            ApkIcon(apkIcon) {
                                Log.d(TAG_DEBUG, "Fetching new icon {apkName.value}")
                                apk.iconURL = null
                                fetchIcon()
                            }
                        }
                        item {
                            Text(apk.repository)
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    HorizontalDivider(
                        thickness = 1.dp, color = getColorSchemeEx().colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    ApkDialogButtons(onDismissRequest)
                }
            }
        }
    }

    @Composable
    fun ApkDialogButtons(onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            apk.applicationPackageName?.let {
                TextButton(onClick = {
                    Utils.uninstallApplication(context, it)
                    onDismissRequest()
                }) {
                    Text("Uninstall")
                }
            }
            if (apk.applicationVersionCode != null && apkLink.value != null) {
                TextButton(onClick = {
                    onDismissRequest()
                    apkLink.value?.let { Utils.installApplication(context, it) }
                }) {
                    Text("Reinstall")
                }
            }
            TextButton(onClick = {
                onDismissRequest()
                scope.launch {
                    database.delete(apk)
                    onRemove(this@GitHubAPK)
                }
            }) {
                Text("Remove")
            }
        }
    }

}
