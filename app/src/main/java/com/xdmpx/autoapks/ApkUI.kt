package com.xdmpx.autoapks

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Job

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
    fun ApkDialog(
        apkIcon: String?,
        repository: String,
        applicationPackageName: String?,
        applicationVersionCode: Long?,
        apkLink: String?,
        onDismissRequest: () -> Unit,
        onRemoveRequest: () -> Job,
        fetchNewIcon: () -> Unit
    ) {

        Utils.CustomDialog(onDismissRequest) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    LazyRow(modifier = Modifier.height(30.dp)) {
                        item {
                            ApkIcon(apkIcon, fetchNewIcon = fetchNewIcon)
                        }
                        item {
                            Text(repository)
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    HorizontalDivider(
                        thickness = 1.dp, color = getColorSchemeEx().colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    ApkDialogButtons(
                        applicationPackageName,
                        applicationVersionCode,
                        apkLink,
                        onDismissRequest,
                        onRemoveRequest = onRemoveRequest
                    )
                }
            }
        }
    }

    @Composable
    fun ApkDialogButtons(
        applicationPackageName: String?,
        applicationVersionCode: Long?,
        apkLink: String?,
        onDismissRequest: () -> Unit,
        modifier: Modifier = Modifier,
        onRemoveRequest: () -> Job,
    ) {
        val context = LocalContext.current

        Column(modifier = modifier) {
            applicationPackageName?.let {
                TextButton(onClick = {
                    Utils.uninstallApplication(context, it)
                    onDismissRequest()
                }) {
                    Text("Uninstall")
                }
            }
            if (applicationVersionCode != null && apkLink != null) {
                TextButton(onClick = {
                    onDismissRequest()
                    apkLink.let { Utils.installApplication(context, it) }
                }) {
                    Text("Reinstall")
                }
            }
            TextButton(onClick = {
                onDismissRequest()
                onRemoveRequest()
            }) {
                Text("Remove")
            }
        }
    }

    @Composable
    fun ApkVersionControl(
        apkLink: String?, apkVersion: String?, apkUpdate: Boolean?, modifier: Modifier = Modifier
    ) {
        Box(
            modifier.fillMaxSize()
        ) {
            if (apkVersion.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize()
                ) {
                    apkLink?.let { InstallButton(it) }
                }
            } else if (apkUpdate == true) {
                Box(
                    contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize()
                ) {
                    apkLink?.let { UpdateButton(it) }
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                ) {
                    Text(apkVersion)
                }
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