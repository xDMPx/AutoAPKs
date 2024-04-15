package com.xdmpx.autoapks

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.xdmpx.autoapks.ui.theme.getColorSchemeEx

object ApkUI {

    private val TAG_DEBUG = "ApkUI"

    @Composable
    fun ApkInfo(
        apkName: String?,
        repository: String,
        apkIcon: String?,
        modifier: Modifier = Modifier,
        fetchNewIcon: () -> Unit
    ) {
        Column(
            modifier.fillMaxSize()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                ApkIcon(apkIcon, fetchNewIcon = fetchNewIcon)
                ApkInfoData(apkName, repository)
            }
        }
    }


    @Composable
    fun ApkInfoData(apkName: String?, repository: String, modifier: Modifier = Modifier) {
        Box(modifier = modifier.fillMaxHeight()) {
            Column {
                val url = "https://github.com/${repository}"
                AnnotatedClickableText(repository, url)
                apkName?.let { Text(it, Modifier) }
            }

        }
    }

    @Composable
    fun ApkIcon(
        apkIcon: String?, modifier: Modifier = Modifier, fetchNewIcon: () -> Unit
    ) {
        Box(
            contentAlignment = Alignment.Center, modifier = modifier
                .fillMaxHeight()
                .padding(5.dp)
        ) {
            if (apkIcon != null) {
                AsyncImage(
                    model = apkIcon, contentDescription = null, onError = {
                        Log.d(TAG_DEBUG, "AsyncImage failed: $apkIcon ${it.result.throwable}")
                        if (it.result.throwable.toString().contains("404")) {
                            fetchNewIcon()
                        }
                    }, modifier = Modifier
                        .clip(CircleShape)
                        .size(30.dp)
                )
            }
        }
    }

    @Composable
    fun InstallButton(apkLink: String, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Button(onClick = {
            Utils.installApplication(context, apkLink)
        }, modifier) {
            Text("Install")
        }
    }

    @Composable
    fun UpdateButton(apkLink: String, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Button(onClick = {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkLink))
            ContextCompat.startActivity(context, browserIntent, null)
        }, modifier) {
            Text("Update")
        }
    }

    @Composable
    fun AnnotatedClickableText(text: String, url: String, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val annotatedText = buildAnnotatedString {
            pushStringAnnotation(
                tag = "URL", annotation = url
            )
            withStyle(
                style = SpanStyle(
                    color = getColorSchemeEx().annotatedText,
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
                ContextCompat.startActivity(context, browserIntent, null)
            }
        }, modifier = modifier)

    }

}