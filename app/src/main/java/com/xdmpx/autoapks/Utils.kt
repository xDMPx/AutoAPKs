package com.xdmpx.autoapks

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object Utils {
    private val TAG_DEBUG = "Utils"

    fun getAppVersionByName(context: Context, appName: String): String? {
        Log.d(TAG_DEBUG, "getAppVersionByName::$appName")
        val packageManager: PackageManager = context.packageManager
        val installedApplications = packageManager.getInstalledPackages(0)

        for (appInfo in installedApplications) {
            if (appInfo.applicationInfo.name == appName) {
                val version = appInfo.versionName
                Log.d(TAG_DEBUG, "getAppVersionByName::$appName $version")
                return version
            }
        }
        return null
    }
}