package com.xdmpx.autoapks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import coil.compose.AsyncImage
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
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

    private var requestQueue: RequestQueue = Volley.newRequestQueue(
        context
    )

    init {
        database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
        if (apk.repositoryDefaultBranch.isNullOrBlank()) {
            fetchDefaultRepoBranch {
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
        requestMipmaphdpi()
    }

    private fun fetchAppInfo() {
        requestApplicationId()
        requestApplicationName()
        setPackageName()
        setInstalledApplicationVersion()
    }

    private fun fetchCurrentRelease() {
        requestLatestRelease()
    }

    public fun refresh() {
        setPackageName()
        setInstalledApplicationVersion()
        fetchCurrentRelease()
    }

    private fun fetchDefaultRepoBranch(onResult: (branchName: String) -> Unit) {
        val repository = apk.repository
        val requestUrl = "https://github.com/$repository/branches"

        Log.d(TAG_DEBUG, "fetchDefaultRepoBranch -> $requestUrl")
        val branchesRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val defaultBranchList =
                response.substringAfter("<h3 class=\"Box-title\">Default branch</h3>")
                    .substringBefore("</ul>")
            val defaultBranchName =
                defaultBranchList.substringAfter("class=\"branch-name").substringAfter(">")
                    .substringBefore("</a>")
            Log.d(TAG_DEBUG, "fetchDefaultRepoBranch::$requestUrl -> $defaultBranchName ")
            onResult(defaultBranchName)
        }, { error ->
            Log.d(
                TAG_DEBUG, "fetchDefaultRepoBranch::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationId(GradleType.GRADLE)
            // TODO: Handle error
        })

        requestQueue.add(branchesRequest)
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

    private fun requestMipmaphdpi() {
        val repository = apk.repository
        val branchName = apk.repositoryDefaultBranch
        val requestUrl =
            "https://github.com/$repository/tree-commit-info/$branchName/app/src/main/res/mipmap-hdpi"

        Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl")
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val iconName = response.names()?.get(0).toString()
            Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl -> $iconName")
            val iconUrl =
                "https://github.com/$repository/raw/$branchName/app/src/main/res/mipmap-hdpi/$iconName"
            if (apk.iconURL != iconUrl) {
                apk.iconURL = iconUrl
                updateDatabase()
            }

        }, { error ->
            Log.d(
                TAG_DEBUG, "requestMipmaphdpi::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        }) {
            override fun getHeaders(): Map<String, String> {
                val headers = HashMap<String, String>()
                headers["Accept"] = "application/json"
                return headers
            }

        }
        requestQueue.add(treeInfoRequest)
    }

    private enum class GradleType {
        KTS, GRADLE
    }

    private fun requestApplicationId(buildType: GradleType = GradleType.KTS) {
        if (buildType == GradleType.KTS) {
            requestApplicationIdGradleKTS()
        } else if (buildType == GradleType.GRADLE) {
            requestApplicationIdGradle()
        }
    }

    private enum class AppNameSource {
        MANIFEST, STRINGS
    }

    private fun requestApplicationName(
        appNameSource: AppNameSource = AppNameSource.MANIFEST
    ) {
        if (appNameSource == AppNameSource.MANIFEST) {
            requestApplicationNameManifest()
        } else if (appNameSource == AppNameSource.STRINGS) {
            requestApplicationNameStrings()
        }
    }

    private fun requestApplicationIdGradleKTS() {
        val repository = apk.repository
        val branchName = apk.repositoryDefaultBranch
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle.kts"

        Log.d(TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            var applicationID = response.substringAfter("applicationId = ").substringBefore("\n")
            applicationID = applicationID.trim('\'', '\"')
            Log.d(TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl -> $applicationID")
            if (apk.applicationId != applicationID) {
                apk.applicationId = applicationID
                updateDatabase()
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationIdGradleKTS::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationId(GradleType.GRADLE)
            // TODO: Handle error
        })

        requestQueue.add(applicationIDRequest)
    }

    private fun requestApplicationIdGradle() {
        val repository = apk.repository
        val branchName = apk.repositoryDefaultBranch
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle"

        Log.d(TAG_DEBUG, "requestApplicationIdGradle::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            var applicationID = response.substringAfter("applicationId ").substringBefore("\n")
            applicationID = applicationID.trim('\'', '\"')
            Log.d(TAG_DEBUG, "requestApplicationIdGradle::$requestUrl -> $applicationID")
            if (apk.applicationId != applicationID) {
                apk.applicationId = applicationID
                updateDatabase()
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationIdGradle::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })

        requestQueue.add(applicationIDRequest)
    }

    private fun requestLatestRelease() {
        val repository = apk.repository
        val requestUrl = "https://github.com/$repository/releases/latest"

        Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl")
        val releaseRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val releaseCommit =
                response.substringAfter("data-hovercard-type=\"commit\" data-hovercard-url=\"")
                    .substringBefore("\"")
            Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseCommit")
            if (apk.releaseCommit != releaseCommit) {
                if (!apk.releaseCommit.isNullOrEmpty()) {
                    apk.toUpdate = true
                }
                apk.releaseCommit = releaseCommit
                updateDatabase()
            }
            val releaseTag = response.substringAfter("aria-label=\"Tag\"")
                .substringAfter("<span class=\"ml-1\">").substringBefore("</span>").trim()
            Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseTag")
            if (apk.releaseTag != releaseTag) {
                apk.releaseTag = releaseTag
                updateDatabase()
            }
            requestLatestReleaseAssets()
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestLatestRelease::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })

        requestQueue.add(releaseRequest)
    }

    private fun requestLatestReleaseAssets() {
        val repository = apk.repository
        val tag = apk.releaseTag
        val requestUrl = "https://github.com/$repository/releases/expanded_assets/$tag"

        Log.d(TAG_DEBUG, "requestLatestReleaseAssets::$requestUrl")
        val assetsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val apkHref = response.substringBefore(".apk\"").substringAfter("href=\"")
            val apkURL = "https://github.com/$apkHref.apk"
            Log.d(TAG_DEBUG, "requestLatestReleaseAssets::$requestUrl -> $apkURL")
            if (apk.releaseLink != apkURL) {
                apk.releaseLink = apkURL
                updateDatabase()
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestLatestReleaseAssets::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(assetsRequest)
    }

    private fun requestApplicationNameManifest() {
        val repository = apk.repository
        val branchName = apk.repositoryDefaultBranch
        val requestUrl =
            "https://github.com/$repository/raw/$branchName/app/src/main/AndroidManifest.xml"

        Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl")
        val tagsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val application = response.substringAfter("<application").substringBefore(">")
            if ("android:name=\"" in application) {
                val name = application.substringAfter("android:name=\"").substringBefore("\"")
                Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> $name")
                if (apk.applicationName != name) {
                    apk.applicationName = name
                    updateDatabase()
                }
            } else {
                Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> NO ANDROID:NAME")
                requestApplicationName(AppNameSource.STRINGS)
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationNameManifest::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationName(AppNameSource.STRINGS)
            // TODO: Handle error
        })
        requestQueue.add(tagsRequest)
    }

    private fun requestApplicationNameStrings() {
        val repository = apk.repository
        val branchName = apk.repositoryDefaultBranch
        val requestUrl =
            "https://github.com/$repository/raw/main/app/src/$branchName/res/values/strings.xml"

        Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl")
        val stringRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            if ("app_name" in response) {
                val name = response.substringAfter("<string name=\"app_name").substringAfter("\">")
                    .substringBefore("</")
                Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl -> $name")
                if (apk.applicationName != name) {
                    apk.applicationName = ".$name"
                    updateDatabase()
                }
            } else {
                Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl -> NO APP_NAME")
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationNameStrings::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(stringRequest)
    }

    private fun setPackageName() {
        val name = apkName.value
        Log.d(TAG_DEBUG, "setPackageName::${deriveAppName()}::$name")
        name?.let {
            val packageName = Utils.getAppPackageName(context, it)
            Log.d(TAG_DEBUG, ":setPackageName:$name -> $packageName")
            if (apk.applicationPackageName != packageName) {
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

    @Composable
    fun ApkCard(modifier: Modifier = Modifier) {
        val apkIcon by remember { apkIcon }
        val apkLink by remember { apkLink }
        val apkName by remember { apkName }
        val apkVersion by remember { apkVersion }
        val apkUpdate by remember { apkUpdate }

        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max).clickable {
                    apk.applicationPackageName?.let { Utils.openApplicationInfo(context, it) }
                }
        ) {
            Column(
                modifier
                    .fillMaxHeight()
                    .weight(0.75f)
            ) {
                Text(apk.repository, modifier)
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

    /*
    @Composable
    fun AnnotatedClickableText(text: String, url: String, modifier: Modifier = Modifier) {
        val annotatedText = buildAnnotatedString {
            pushStringAnnotation(
                tag = "URL", annotation = url
            )
            withStyle(
                style = SpanStyle(
                    color = Color.Blue,
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
    */

}
