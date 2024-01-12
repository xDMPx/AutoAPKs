package com.xdmpx.autoapks

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest

object GitHubRepoFetcher {
    val TAG_DEBUG = "GitHubRepoFetcher"

    fun fetchDefaultRepoBranch(
        repository: String, context: Context, onResult: (branchName: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/branches"

        Log.d(TAG_DEBUG, "fetchDefaultRepoBranch -> $requestUrl")
        val branchesRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val defaultBranchList =
                response.substringAfter("<h2 class=\"Box-sc-g0xbh4-0 cimJpq TableTitle\" id=\"default\">Default</h2>")
                    .substringBefore("</table>")
            val defaultBranchName = defaultBranchList.substringAfter("class=\"BranchName")
                .substringAfter("<div title=\"").substringAfter(">").substringBefore("</div></a>")

            Log.d(TAG_DEBUG, "fetchDefaultRepoBranch::$requestUrl -> $defaultBranchName ")
            onResult(defaultBranchName)
        }, { error ->
            Log.d(
                TAG_DEBUG, "fetchDefaultRepoBranch::ERROR::$requestUrl -> ${error.message}"
            )
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
                response.substringAfter("data-hovercard-type=\"commit\" data-hovercard-url=\"")
                    .substringBefore("\"")
            Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseCommit")

            val releaseTag = response.substringAfter("aria-label=\"Tag\"")
                .substringAfter("<span class=\"ml-1\">").substringBefore("</span>").trim()
            Log.d(TAG_DEBUG, "requestLatestRelease::$requestUrl -> $releaseTag")

            onResult(releaseCommit, releaseTag)
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
            val apkHref = response.substringBefore(".apk\"").substringAfter("href=\"")
            val apkURL = "https://github.com/$apkHref.apk"

            Log.d(TAG_DEBUG, "requestLatestReleaseAssets::$requestUrl -> $apkURL")
            onResult(apkURL)
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestLatestReleaseAssets::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(assetsRequest)
    }

    fun requestMipmaphdpi(
        repository: String,
        branchName: String,
        context: Context,
        onResult: (iconUrl: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl =
            "https://github.com/$repository/tree-commit-info/$branchName/app/src/main/res/mipmap-hdpi"

        Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl")
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val iconName = response.names()?.get(0).toString()
            Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl -> $iconName")
            val iconUrl =
                "https://github.com/$repository/raw/$branchName/app/src/main/res/mipmap-hdpi/$iconName"

            Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl -> $iconUrl")
            onResult(iconUrl)
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


    enum class GradleType {
        KTS, GRADLE
    }

    fun requestApplicationId(
        repository: String,
        branchName: String,
        context: Context,
        buildType: GradleType = GradleType.KTS,
        onResult: (iconUrl: String) -> Unit,
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
        onResult: (applicationId: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle.kts"

        Log.d(TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            var applicationID = response.substringAfter("applicationId = ").substringBefore("\n")
            applicationID = applicationID.trim('\'', '\"')

            Log.d(TAG_DEBUG, "requestApplicationIdGradleKTS::$requestUrl -> $applicationID")
            onResult(applicationID)
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationIdGradleKTS::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationId(repository, branchName, context, GradleType.GRADLE, onResult)
            // TODO: Handle error
        })

        requestQueue.add(applicationIDRequest)
    }

    private fun requestApplicationIdGradle(
        repository: String,
        branchName: String,
        context: Context,
        onResult: (applicationID: String) -> Unit
    ) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl = "https://github.com/$repository/raw/$branchName/app/build.gradle"

        Log.d(TAG_DEBUG, "requestApplicationIdGradle::$requestUrl")
        val applicationIDRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            var applicationID = response.substringAfter("applicationId ").substringBefore("\n")
            applicationID = applicationID.trim('\'', '\"')

            Log.d(TAG_DEBUG, "requestApplicationIdGradle::$requestUrl -> $applicationID")
            onResult(applicationID)
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
            val application = response.substringAfter("<application").substringBefore(">")
            if ("android:name=\"" in application) {
                val name = application.substringAfter("android:name=\"").substringBefore("\"")

                Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> $name")
                onResult(name)
            } else {
                Log.d(TAG_DEBUG, "requestApplicationNameManifest::$requestUrl -> NO ANDROID:NAME")
                requestApplicationName(
                    repository, branchName, context, AppNameSource.STRINGS, onResult
                )
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestApplicationNameManifest::ERROR::$requestUrl -> ${error.message}"
            )
            requestApplicationName(repository, branchName, context, AppNameSource.STRINGS, onResult)
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
                val name = response.substringAfter("<string name=\"app_name").substringAfter("\">")
                    .substringBefore("</")

                Log.d(TAG_DEBUG, "requestApplicationNameStrings::$requestUrl -> $name")
                onResult(".$name")
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

    fun validateAndroidAPKRepository(
        repository: String, context: Context, onResult: (valid: Boolean) -> Unit
    ) {
        Log.d(TAG_DEBUG, "validateAndroidAPKRepository::$repository")
        fetchDefaultRepoBranch(repository, context) { branchName ->
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

}