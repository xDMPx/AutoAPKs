package com.xdmpx.autoapks

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest

object GitHubRepoFetcher {
    val TAG_DEBUG = "GitHubRepoFetcher"

    private fun String?.substringAfterOrNull(delimiter: String): String? {
        this?.let { str ->
            str.substringAfter(delimiter, missingDelimiterValue = "").let {
                it.ifBlank { return null }
                return it
            }
        }
        return null
    }

    private fun String?.substringBeforeOrNull(delimiter: String): String? {
        this?.let { str ->
            str.substringBefore(delimiter, missingDelimiterValue = "").let {
                it.ifBlank { return null }
                return it
            }
        }
        return null
    }

    fun fetchDefaultRepoBranch(
        repository: String,
        context: Context,
        onError: () -> Unit = {},
        onResult: (branchName: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/branches"

        Log.d(TAG_DEBUG, "fetchDefaultRepoBranch -> $requestUrl")
        val branchesRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val defaultBranchList =
                response.substringAfterOrNull(">Default<").substringBeforeOrNull("</table>")
            val defaultBranchName = defaultBranchList.substringAfterOrNull("class=\"BranchName")
                .substringAfterOrNull("<div title=\"").substringAfterOrNull(">")
                .substringBeforeOrNull("</div></a>")

            Log.d(TAG_DEBUG, "fetchDefaultRepoBranch::$requestUrl -> $defaultBranchName")
            defaultBranchName?.let { onResult(it) }
        }, { error ->
            Log.d(
                TAG_DEBUG, "fetchDefaultRepoBranch::ERROR::$requestUrl -> ${error.message}"
            )
            onError()
            // TODO: Handle error
        })

        requestQueue.add(branchesRequest)
    }

    fun requestLatestRelease(
        repository: String,
        context: Context,
        onResult: (releaseCommit: String, releaseTag: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/releases/latest"

        Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl")
        val releaseRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val releaseCommit =
                response.substringAfterOrNull("data-hovercard-type=\"commit\" data-hovercard-url=\"")
                    .substringBeforeOrNull("\"")
            val releaseTag = response.substringAfterOrNull("aria-label=\"Tag\"")
                .substringAfterOrNull("<span class=\"ml-1\">").substringBeforeOrNull("</span>")
                ?.trim()

            releaseCommit?.let { releaseCommit ->
                Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseCommit")
                Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseTag")
                releaseTag?.let { releaseTag ->
                    onResult(releaseCommit, releaseTag)
                }
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestLatestRelease::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })

        requestQueue.add(releaseRequest)
    }

    fun requestLatestReleaseAssets(
        repository: String, releaseTag: String, context: Context, onResult: (apkURL: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/releases/expanded_assets/$releaseTag"

        Log.d(TAG_DEBUG, "requestLatestReleaseAssets::$requestUrl")
        val assetsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val apkHref = response.substringBeforeOrNull(".apk\"").substringAfterOrNull("href=\"")
            apkHref?.let { apkHref ->
                val apkURL = "https://github.com/$apkHref.apk"
                Log.d(TAG_DEBUG, "requestLatestReleaseAssets::$requestUrl -> $apkURL")
                onResult(apkURL)
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestLatestReleaseAssets::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(assetsRequest)
    }

    enum class GradleType {
        KTS, GRADLE
    }

    fun requestApplicationId(
        repository: String,
        branchName: String,
        context: Context,
        buildType: GradleType = GradleType.KTS,
        onResult: (applicationId: String) -> Unit,
    ) {
        if (buildType == GradleType.KTS) {
            requestApplicationIdGradleKTS(repository, branchName, context, onResult)
        } else if (buildType == GradleType.GRADLE) {
            requestApplicationIdGradle(repository, branchName, context, onResult)
        }
    }

    enum class AppNameSource {
        MANIFEST, STRINGS
    }

    fun requestApplicationName(
        repository: String,
        branchName: String,
        context: Context,
        appNameSource: AppNameSource = AppNameSource.MANIFEST,
        onResult: (appName: String) -> Unit,
    ) {
        if (appNameSource == AppNameSource.MANIFEST) {
            requestApplicationNameManifest(repository, branchName, context, onResult)
        } else if (appNameSource == AppNameSource.STRINGS) {
            requestApplicationNameStrings(repository, branchName, context, onResult)
        }
    }

    private fun requestApplicationIdGradleKTS(
        repository: String,
        branchName: String,
        context: Context,
        onResult: (applicationId: String) -> Unit,
        source: String = "applicationId",
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle.kts"

        Log.d(TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val applicationID =
                response.substringAfterOrNull("$source = ").substringBeforeOrNull("\n")
            applicationID?.let { it ->
                val applicationID = it.trim('\'', '\"')
                if (applicationID != it) {
                    Log.d(
                        TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl -> $applicationID"
                    )
                    onResult(applicationID)
                } else if (source != "namespace") {
                    requestApplicationIdGradleKTS(
                        repository, branchName, context, onResult, "namespace"
                    )
                }
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationIdGradleKTS::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationId(
                repository, branchName, context, GradleType.GRADLE, onResult
            )
            // TODO: Handle error
        })

        requestQueue.add(applicationIDRequest)
    }

    private fun requestApplicationIdGradle(
        repository: String,
        branchName: String,
        context: Context,
        onResult: (applicationID: String) -> Unit,
        source: String = "applicationId",
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle"

        Log.d(TAG_DEBUG, "requestApplicationIdGradle::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val applicationID =
                response.substringAfterOrNull("$source ").substringBeforeOrNull("\n")
            applicationID?.let { it ->
                val applicationID = it.trim('\'', '\"')
                if (applicationID != it) {
                    Log.d(
                        TAG_DEBUG, "requestApplicationIdGradle::$requestUrl -> $applicationID"
                    )
                    onResult(applicationID)
                } else if (source != "namespace") {
                    requestApplicationIdGradle(
                        repository, branchName, context, onResult, "namespace"
                    )
                }
            }


        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationIdGradle::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })

        requestQueue.add(applicationIDRequest)
    }

    private fun requestApplicationNameManifest(
        repository: String, branchName: String, context: Context, onResult: (name: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl =
            "https://github.com/$repository/raw/$branchName/app/src/main/AndroidManifest.xml"

        Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl")
        val manifestRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val application =
                response.substringAfterOrNull("<application").substringBeforeOrNull(">")
            application?.let { application ->
                if ("android:name=\"" in application) {
                    val name = application.substringAfter("android:name=\"").substringBefore("\"")

                    Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> $name")
                    onResult(name)
                } else {
                    Log.d(
                        TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> NO ANDROID:NAME"
                    )
                    requestApplicationName(
                        repository, branchName, context, AppNameSource.STRINGS, onResult
                    )
                }
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationNameManifest::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationName(
                repository, branchName, context, AppNameSource.STRINGS, onResult
            )
            // TODO: Handle error
        })
        requestQueue.add(manifestRequest)
    }

    private fun requestApplicationNameStrings(
        repository: String, branchName: String, context: Context, onResult: (name: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl =
            "https://github.com/$repository/raw/main/app/src/$branchName/res/values/strings.xml"

        Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl")
        val stringRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            if ("app_name" in response) {
                val name = response.substringAfterOrNull("<string name=\"app_name")
                    .substringAfterOrNull("\">").substringBeforeOrNull("</")
                name?.let { name ->
                    Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl -> $name")
                    onResult(".$name")
                }
            } else {
                Log.d(
                    TAG_DEBUG, "requestApplicationNameStrings::$requestUrl -> NO APP_NAME"
                )
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationNameStrings::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(stringRequest)
    }

    fun validateAndroidAPKRepository(
        repository: String, context: Context, onResult: (valid: Boolean) -> Unit
    ) {
        Log.d(TAG_DEBUG, "validateAndroidAPKRepository::$repository")
        fetchDefaultRepoBranch(repository, context, onError = {
            onResult(false)
        }) { branchName ->
            val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
            val requestUrl =
                "https://github.com/$repository/raw/$branchName/app/src/main/AndroidManifest.xml"
            val manifestRequest = StringRequest(Request.Method.GET, requestUrl, { _ ->
                onResult(true)
            }, { _ ->
                onResult(false)
            })
            requestQueue.add(manifestRequest)
        }
    }


    fun requestIcon(
        repository: String,
        branchName: String,
        context: Context,
        iconFolder: String? = null,
        iconName: String? = null,
        onResult: (iconUrl: String) -> Unit
    ) {
        when {
            (iconFolder == null) -> requestIconFolder(repository, branchName, context, onResult)
            (iconName == null) -> requestIconName(
                repository, branchName, context, iconFolder, onResult
            )

            else -> requestIconUrl(repository, branchName, context, iconFolder, iconName, onResult)
        }
    }

    private fun requestIconFolder(
        repository: String,
        branchName: String,
        context: Context,
        onResult: (iconUrl: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl =
            "https://github.com/$repository/tree-commit-info/$branchName/app/src/main/res"

        Log.d(TAG_DEBUG, "requestIconFolder::$repository -> $requestUrl")
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val length = response.length()
            var folderName: String? = null
            for (i in 0 until length) {
                if (response.names()?.get(i).toString().startsWith("mipmap-")) {
                    folderName = response.names()?.get(i).toString()
                }
            }

            Log.d(TAG_DEBUG, "requestIconFolder::$requestUrl -> $folderName")
            requestIcon(repository, branchName, context, folderName, null, onResult)
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestIconFolder::ERROR::$requestUrl -> ${error.message}"
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

    private fun requestIconName(
        repository: String,
        branchName: String,
        context: Context,
        iconFolder: String,
        onResult: (iconUrl: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl =
            "https://github.com/$repository/raw/$branchName/app/src/main/AndroidManifest.xml"

        Log.d(TAG_DEBUG, "requestIconLocation::$requestUrl")
        val manifestRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val application =
                response.substringAfterOrNull("<application").substringBeforeOrNull(">")
            application?.let { application ->
                if ("android:icon=" in application) {
                    var iconLocation: String? = application.substringAfterOrNull("android:icon=")
                        ?.substringBeforeOrNull("\n")
                    if ((iconLocation != null) && ('$' !in iconLocation)) {
                        iconLocation = iconLocation.trim('"', '\'', '@')
                    } else {
                        iconLocation = ""
                    }
                    val iconName = iconLocation.substringAfterLast("/")

                    Log.d(TAG_DEBUG, "requestIconLocation::$requestUrl -> $iconName")
                    requestIcon(repository, branchName, context, iconFolder, iconName, onResult)
                }
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestIconLocation::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
            requestIcon(repository, branchName, context, iconFolder, "", onResult)
        })
        requestQueue.add(manifestRequest)
    }

    private fun requestIconUrl(
        repository: String,
        branchName: String,
        context: Context,
        iconFolder: String,
        iconName: String? = null,
        onResult: (iconUrl: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl: String =
            "https://github.com/$repository/tree-commit-info/$branchName/app/src/main/res/$iconFolder"

        Log.d(TAG_DEBUG, "requestIconUrl::$repository -> $requestUrl")
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val icon = if (iconName.isNullOrBlank()) response.names()?.get(0).toString() else {
                val length = response.length()
                var name = ""
                for (i in 0 until length) {
                    if (response.names()?.get(i).toString().startsWith("$iconName.")) {
                        name = response.names()?.get(i).toString()
                    }
                }
                name
            }
            val iconUrl =
                "https://github.com/$repository/raw/$branchName/app/src/main/res/$iconFolder/$icon"

            Log.d(TAG_DEBUG, "requestIconUrl::$requestUrl -> $iconUrl")
            onResult(iconUrl)
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestIconUrl::ERROR::$requestUrl -> ${error.message}"
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

}
