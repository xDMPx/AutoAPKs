package com.xdmpx.autoapks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat.startActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.ImageRequest
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
    private var iconBitmap: MutableState<Bitmap?> = mutableStateOf(null)
    private var tag: MutableState<String?> = mutableStateOf(apk.releaseTag)
    private var apkLink: MutableState<String?> = mutableStateOf(apk.releaseLink)
    private var recomposed = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    private var requestQueue: RequestQueue = Volley.newRequestQueue(
        context
    )

    init {
        fetchIcon()
        fetchCurrentRelease()
        database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
    }

    private fun fetchIcon() {
        requestMipmaphdpi(apk.repository)
    }

    private fun fetchCurrentRelease() {
        requestCurrentTag(apk.repository)
    }

    private fun updateDatabase() {
        tag.value = apk.releaseTag
        apkLink.value = apk.releaseLink
        scope.launch { database.update(apk) }
    }

    private fun requestMipmaphdpi(repository: String) {
        val requestUrl =
            "https://github.com/$repository/tree-commit-info/main/app/src/main/res/mipmap-hdpi"
        val treeInfoRequest = object : JsonObjectRequest(requestUrl, { response ->
            val iconName = response.names()?.get(0).toString()
            Log.d(TAG_DEBUG, "requestMipmaphdpi::$requestUrl -> $iconName")
            val iconUrl = "https://github.com/$repository/raw/main/app/src/main/res/mipmap-hdpi/$iconName"
            if (apk.iconURL != iconUrl) {
                apk.iconURL = iconUrl
                updateDatabase()
            }

            requestIcon()
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

    private fun requestIcon() {
        val requestUrl = apk.iconURL

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

    private fun requestCurrentTag(repository: String) {
        val requestUrl = "https://github.com/$repository/tags"
        Log.d(TAG_DEBUG, "tagsRequest -> $requestUrl")
        val tagsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val tagHref =
                response.substringAfter("<h2 data-view-component=\"true\" class=\"f4 d-inline\">")
                    .substringBefore("</h2>")
            val tag = tagHref.substringAfter(">").substringBefore("</")
            Log.d(TAG_DEBUG, "tagsRequest::$requestUrl -> $tag")
            if (apk.releaseTag != tag) {
                apk.releaseTag = tag
                updateDatabase()
            }
            requestRelease(repository, tag)
        }, { error ->
            Log.d(
                TAG_DEBUG, "tagsRequest::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })

        requestQueue.add(tagsRequest)
    }

    private fun requestRelease(repository: String, tag: String) {
        val requestUrl = "https://github.com/$repository/releases/expanded_assets/$tag"
        val tagsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val apkHref = response.substringBefore(".apk\"").substringAfter("href=\"")
            val apkURL = "https://github.com/$apkHref.apk"
            Log.d(TAG_DEBUG, "requestRelease::$requestUrl -> $apkURL")
            if (apk.releaseLink != apkURL) {
                apk.releaseLink = apkURL
                updateDatabase()
            }
        }, { error ->
            Log.d(
                TAG_DEBUG, "requestRelease::ERROR::$requestUrl -> ${error.message}"
            )
            // TODO: Handle error
        })
        requestQueue.add(tagsRequest)
    }

    @Composable
    fun ApkCard(modifier: Modifier = Modifier) {
        val iconBitmap by remember { iconBitmap }
        val tag by remember { tag }
        val apkLink by remember { apkLink }

        Column {
            Text(apk.repository, modifier)
            iconBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = "", modifier
                )
            }
            tag?.let { tag ->
                apkLink?.let { AnnotatedClickableText(tag, it) }
            }
        }
        recomposed++

        Log.d(TAG_DEBUG, "$recomposed")
    }

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
}
