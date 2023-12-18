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

private lateinit var requestQueue: RequestQueue

class GitHubAPK(private val repository: String, private val context: Context) {
    private val TAG_DEBUG = "GitHubAPK"
    private var iconBitmap: MutableState<Bitmap?> = mutableStateOf(null)
    private var tag: MutableState<String?> = mutableStateOf(null)
    private var apkLink: MutableState<String?> = mutableStateOf(null)
    private var recomposed = 0

    private var requestQueue: RequestQueue = Volley.newRequestQueue(
        context
    )

    init {
        fetchIcon()
        fetchCurrentRelease()
    }

    private fun fetchIcon() {
        requestMipmaphdpi(repository)
    }

    private fun fetchCurrentRelease() {
        requestCurrentTag(repository)
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

    private fun requestCurrentTag(repository: String) {
        val requestUrl = "https://github.com/$repository/tags"
        Log.d(TAG_DEBUG, "tagsRequest -> $requestUrl")
        val tagsRequest = StringRequest(Request.Method.GET, requestUrl, { response ->
            val tagHref =
                response.substringAfter("<h2 data-view-component=\"true\" class=\"f4 d-inline\">")
                    .substringBefore("</h2>")
            val tag = tagHref.substringAfter(">").substringBefore("</")
            Log.d(TAG_DEBUG, "tagsRequest::$requestUrl -> $tag")
            this.tag.value = tag
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
            apkLink.value = apkURL
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
            Text(repository, modifier)
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
                startActivity(context,browserIntent, null)
            }
        })

    }
}
