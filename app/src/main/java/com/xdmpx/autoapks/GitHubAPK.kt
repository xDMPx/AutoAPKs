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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import coil.compose.AsyncImage
import com.xdmpx.autoapks.GitHubRepoFetcher.fetchDefaultRepoBranch
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.ui.theme.getColorSchemeEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GitHubAPK(private val apk: GitHubAPKEntity, private val context: Context) {
    private val TAG_DEBUG = "GitHubAPK"
    private var database: GitHubAPKDao
    private var apkIcon: MutableState<String?> = mutableStateOf(apk.iconURL)
    private var apkLink: MutableState<String?> = mutableStateOf(apk.releaseLink)
    private var apkVersion: MutableState<String?> = mutableStateOf(apk.applicationVersionName)
    private var apkUpdate: MutableState<Boolean?> = mutableStateOf(apk.toUpdate)
    private var apkName: MutableState<String?> = mutableStateOf(deriveAppName())
    private var recomposed = 0
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
        apk.repositoryDefaultBranch?.let { branchName ->
            GitHubRepoFetcher.requestMipmaphdpi(apk.repository, branchName, context) { iconUrl ->
                if (apk.iconURL != iconUrl) {
                    apk.iconURL = iconUrl
                    updateDatabase()
                }
            }

        }
    }

    private fun fetchAppInfo() {
        val repository = apk.repository
        apk.repositoryDefaultBranch?.let { branchName ->
            GitHubRepoFetcher.requestApplicationId(
                repository, branchName, context
            ) { applicationID ->
                if (apk.applicationId != applicationID) {
                    apk.applicationId = applicationID
                    updateDatabase()
                }
            }
            GitHubRepoFetcher.requestApplicationName(repository, branchName, context) { name ->
                if (apk.applicationName != name) {
                    apk.applicationName = name
                    updateDatabase()
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
            if (!name.startsWith('.')) {
                return name
            }
            apk.applicationId?.let {
                return "$it$name"
            }
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
        val name = apkName.value
        Log.d(TAG_DEBUG, "setPackageName::${deriveAppName()}::$name")
        name?.let {
            val packageName = Utils.getAppPackageName(context, it)
            Log.d(TAG_DEBUG, "setPackageName:$name -> $packageName")
            if (apk.applicationPackageName != packageName) {
                if (packageName.isNullOrBlank()) {
                    Log.d(
                        TAG_DEBUG,
                        "setPackageName -> $packageName -> ${packageName.isNullOrBlank()}"
                    )
                    apk.applicationVersionCode = null
                    apk.applicationVersionName = null
                }
                apk.applicationPackageName = packageName
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
        val apkIcon by remember { apkIcon }
        val apkLink by remember { apkLink }
        val apkName by remember { apkName }
        val apkVersion by remember { apkVersion }
        val apkUpdate by remember { apkUpdate }

        val haptics = LocalHapticFeedback.current

        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .combinedClickable(onClick = {
                    apk.applicationPackageName?.let { Utils.openApplicationInfo(context, it) }
                }, onLongClick = {
                    apk.applicationPackageName?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Utils.uninstallApplication(context, it)
                    }
                })
        ) {
            Column(
                modifier
                    .fillMaxHeight()
                    .weight(0.75f)
            ) {
                val url = "https://github.com/${apk.repository}"
                AnnotatedClickableText(apk.repository, url)
                apkName?.let { Text(it, modifier) }
                Spacer(modifier = modifier.size(5.dp))
                apkIcon?.let {
                    AsyncImage(
                        model = apk.iconURL,
                        contentDescription = null,
                        modifier = modifier
                            .clip(CircleShape)
                            .size(25.dp)
                    )
                }
            }
            Box(Modifier.weight(0.25f)) {
                if (apkVersion.isNullOrEmpty()) {
                    Box(
                        contentAlignment = Alignment.CenterEnd, modifier = modifier.fillMaxSize()
                    ) {
                        apkLink?.let { InstallButton(it, modifier) }
                    }
                } else if (apkUpdate == true) {
                    Box(
                        contentAlignment = Alignment.CenterEnd, modifier = modifier.fillMaxSize()
                    ) {
                        apkLink?.let { UpdateButton(it, modifier) }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()
                    ) {
                        apkVersion?.let { Text(it, modifier) }
                    }
                }
            }
        }

        recomposed++
        Log.d(TAG_DEBUG, "$recomposed")
    }

    @Composable
    fun InstallButton(apkLink: String, modifier: Modifier = Modifier) {
        Button(onClick = {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkLink))
            startActivity(context, browserIntent, null)
        }, modifier) {
            Text("Install")
        }
    }

    @Composable
    fun UpdateButton(apkLink: String, modifier: Modifier = Modifier) {
        Button(onClick = {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkLink))
            startActivity(context, browserIntent, null)
        }, modifier) {
            Text("Update")
        }
    }

    @Composable
    fun AnnotatedClickableText(text: String, url: String, modifier: Modifier = Modifier) {
        val annotatedText = buildAnnotatedString {
            pushStringAnnotation(
                tag = "URL", annotation = url
            )
            withStyle(
                style = SpanStyle(
                    color = getColorSchemeEx().annotatedText,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(text)
            }
            pop()
        }

        ClickableText(text = annotatedText, onClick = { offset ->
            annotatedText.getStringAnnotations(
                tag = "URL", start = offset, end = offset
            ).firstOrNull()?.let { annotation ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                startActivity(context, browserIntent, null)
            }
        }, modifier = modifier)

    }

}
