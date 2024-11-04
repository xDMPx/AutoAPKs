package com.xdmpx.autoapks.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import com.xdmpx.autoapks.apk.github.GitHubRepoFetcher
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object Utils {
    private const val TAG_DEBUG = "Utils"

    data class ApplicationVersion(val name: String, val code: Long)

    fun getAppVersion(context: Context, packageName: String): ApplicationVersion? {
        Log.d(TAG_DEBUG, "getAppVersion::$packageName")
        val packageManager: PackageManager = context.packageManager
        val appInfo: PackageInfo? = packageManager.getPackageInfo(packageName, 0)

        appInfo?.let {
            val version = it.versionName?.let { it1 -> ApplicationVersion(it1, it.longVersionCode) }
            Log.d(TAG_DEBUG, "getAppVersionByName::$packageName -> $version")
            return version
        }

        return null
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        Log.d(TAG_DEBUG, "isAppInstalled::$packageName")
        val packageManager: PackageManager = context.packageManager

        val appInstalled = try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        Log.d(TAG_DEBUG, "isAppInstalled::$packageName -> $appInstalled")

        return appInstalled
    }

    fun getAppPackageName(context: Context, appName: String): String? {
        Log.d(TAG_DEBUG, "getAppPackageName::$appName")
        val packageManager: PackageManager = context.packageManager
        val installedApplications = packageManager.getInstalledPackages(0)
        val appID = appName.substringBeforeLast('.')

        for (appInfo in installedApplications) {
            val applicationInfo = appInfo.applicationInfo ?: continue
            if (applicationInfo.name.isNullOrBlank() && appInfo.packageName.isNullOrBlank()) {
                continue
            }
            if (applicationInfo.name == appName || appInfo.packageName == appName) {
                Log.d(TAG_DEBUG, "getAppPackageName::$appName ${appInfo.packageName}")
                return appInfo.packageName
            } else if (applicationInfo.name == appID || appInfo.packageName == appID) {
                Log.d(TAG_DEBUG, "getAppPackageName::$appName ${appInfo.packageName}")
                return appInfo.packageName
            }
        }
        return null
    }

    fun openApplicationInfo(context: Context, packageName: String) {
        Log.d(TAG_DEBUG, "openApplicationInfo::$packageName")
        val intent = Intent()
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        startActivity(context, intent, null)
    }

    fun uninstallApplication(context: Context, packageName: String) {
        Log.d(TAG_DEBUG, "uninstallApplication::$packageName")
        val intent = Intent()
        intent.setAction(Intent.ACTION_DELETE)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        startActivity(context, intent, null)
    }

    fun installApplication(context: Context, apkLink: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkLink)).setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        val id = downloadManager.enqueue(request)
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val apkUri = downloadManager.getUriForDownloadedFile(id)
                if (context != null) {

                    Log.d("onReceive", apkUri.toString())
                    val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                    installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(context, installIntent, null)
                }

            }
        }

        ContextCompat.registerReceiver(
            context,
            broadcast,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

    }

    fun userInputToAPKRepository(userInput: String): String? {
        Log.d(TAG_DEBUG, "userInputToAPKRepository::$userInput")
        var userInput =
            userInput.substringBefore('#').substringBefore('?').substringAfter("://").trim('/')
        userInput = if (userInput.contains('.')) userInput.substringAfter('/') else userInput
        if (userInput.contains('/')) {
            val repoOwner = userInput.substringBefore('/')
            val repoName = userInput.substringAfter('/').substringBefore('/')
            val repository = "$repoOwner/$repoName"
            Log.d(TAG_DEBUG, "userInputToAPKRepository::$userInput -> $repository")
            return repository
        }
        return null
    }

    @Composable
    fun CustomDialog(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                content = content
            )
        }
    }

    fun ShortToast(context: Context, text: CharSequence) {
        Toast.makeText(
            context, text, Toast.LENGTH_SHORT
        ).show()
    }

    suspend fun exportToJSON(context: Context, uri: Uri): Boolean {
        val database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase

        Log.d(TAG_DEBUG, "export")
        val repositories = database.getExportData().map {
            val jsonObject = JSONObject()
            jsonObject.put("repository", it.repository)
            jsonObject.put("base_directory", it.baseDirectory)
        }
        val json = JSONArray(repositories)

        Log.d(TAG_DEBUG, "export -> $json")
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importFromJSON(
        context: Context, uri: Uri, addAPK: (String, String) -> Unit
    ): Boolean {
        try {
            val database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
            Log.d(TAG_DEBUG, "import")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val importedJson = JSONArray(bufferedReader.readText())
                inputStream.close()

                val repositories = database.getRepositories()

                val toImport =
                    (0 until importedJson.length()).map { importedJson.getJSONObject(it) }
                        .filter { it.getString("repository") !in repositories }.toList()

                toImport.forEach {
                    addAPK(it.getString("repository"), it.getString("base_directory"))
                }

                Log.d(TAG_DEBUG, "import -> $toImport")
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun isValidRepository(
        repo: String?, baseDirectory: String, context: Context, onResult: (valid: Boolean) -> Unit
    ) {
        if (repo.isNullOrBlank()) {
            onResult(false)
            return
        }
        GitHubRepoFetcher.validateAndroidAPKRepository(
            repo, baseDirectory, context
        ) { onResult(it) }

    }
}