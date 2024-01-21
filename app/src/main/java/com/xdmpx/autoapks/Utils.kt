package com.xdmpx.autoapks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat.startActivity

object Utils {
    private val TAG_DEBUG = "Utils"

    data class ApplicationVersion(val name: String, val code: Long)

    fun getAppVersion(context: Context, packageName: String): ApplicationVersion? {
        Log.d(TAG_DEBUG, "getAppVersion::$packageName")
        val packageManager: PackageManager = context.packageManager
        val appInfo: PackageInfo? = packageManager.getPackageInfo(packageName, 0)

        appInfo?.let {
            val version = ApplicationVersion(it.versionName, it.longVersionCode)
            Log.d(TAG_DEBUG, "getAppVersionByName::$packageName -> $version")
            return version
        }

        return null
    }

    fun getAppPackageName(context: Context, appName: String): String? {
        Log.d(TAG_DEBUG, "getAppPackageName::$appName")
        val packageManager: PackageManager = context.packageManager
        val installedApplications = packageManager.getInstalledPackages(0)

        for (appInfo in installedApplications) {
            if (appInfo.applicationInfo.name.isNullOrBlank() && appInfo.packageName.isNullOrBlank()) {
                continue
            }
            if (appInfo.applicationInfo.name == appName || appInfo.packageName == appName) {
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

    fun installApplication(context: Context, apkLink: String){
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkLink))
        startActivity(context, browserIntent, null)
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

}