package com.xdmpx.autoapks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
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
            if (appInfo.applicationInfo.name.isNullOrBlank()) {
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

    fun userInputToAPKRepository(userInput: String): String? {
        Log.d(TAG_DEBUG, "userInputToAPKRepository::$userInput")
        if (userInput.contains('/')) {
            var repositoryStrStart =
                userInput.substring(0,userInput.lastIndexOf('/')).lastIndexOf('/')
            repositoryStrStart = if (repositoryStrStart > 0) repositoryStrStart+1 else 0
            Log.d(TAG_DEBUG, "userInputToAPKRepository::$userInput -> ${userInput.substring(repositoryStrStart)}")
            return userInput.substring(repositoryStrStart)
        }
        return null
    }
}