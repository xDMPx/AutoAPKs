package com.xdmpx.autoapks

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.android.volley.RequestQueue
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

private lateinit var requestQueue: RequestQueue

class GitHubAPK(private val repository: String, private val context: Context) {
    private val TAG_DEBUG = "GitHubAPK"
    private var iconBitmap: MutableState<Bitmap?> = mutableStateOf(null)
    private var recomposed = 0

    private var requestQueue: RequestQueue = Volley.newRequestQueue(
        context
    )

    init {
        fetchIcon()
    }

    private fun fetchIcon() {
        requestMipmaphdpi(repository)
    }

    private fun requestMipmaphdpi(repository: String) {
        val requestUrl =
            "https://github.com/$repository/tree-commit-info/main/app/src/main/res/mipmap-hdpi"
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val iconName = response.names()?.get(0).toString()
            Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl -> $iconName")
            requestIcon(repository, iconName)
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

    private fun requestIcon(repository: String, iconName: String) {
        val requestUrl =
            "https://github.com/$repository/raw/main/app/src/main/res/mipmap-hdpi/$iconName"

        val iconRequest = ImageRequest(requestUrl, { response ->
            Log.d(TAG_DEBUG, "requestIcon::$requestUrl -> $response")
            iconBitmap.value = response
        }, 0, 0, null, null, { error ->
            Log.d(
                TAG_DEBUG, "requestIcon::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(iconRequest)
    }

    @Composable
    fun ApkCard(modifier: Modifier = Modifier) {
        val iconBitmap by remember { iconBitmap }
        Column {
            Text(repository, modifier)
            iconBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = "", modifier
                )
            }
        }
        recomposed++
        Log.d(TAG_DEBUG, "$recomposed")
    }
}
