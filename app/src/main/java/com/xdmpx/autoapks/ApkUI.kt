package com.xdmpx.autoapks

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.xdmpx.autoapks.settings.Settings
import com.xdmpx.autoapks.settings.SettingsUI.ConfirmationAlertDialog
import com.xdmpx.autoapks.ui.theme.getColorSchemeEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ApkUI {

    private val TAG_DEBUG = "ApkUI"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsInstance = Settings.getInstance()

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ApkCard(gitHubAPKViewModel: GitHubAPK, modifier: Modifier = Modifier) {
        val apkState by gitHubAPKViewModel.apkState.collectAsState()
        val apk = gitHubAPKViewModel.apk

        val showDialog = remember { mutableStateOf(false) }
        val haptics = LocalHapticFeedback.current
        val context = LocalContext.current

        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .combinedClickable(onClick = {
                    apk.applicationPackageName?.let { Utils.openApplicationInfo(context, it) }
                }, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDialog.value = true
                })
        ) {
            ApkInfo(
                apkState.apkName,
                apk.repository,
                apkState.apkIcon,
                modifier.weight(0.75f),
                gitHubAPKViewModel.fetchNewIcon
            )
            ApkVersionControl(
                apkState.apkLink, apkState.apkVersion, apkState.apkUpdate, modifier.weight(0.25f)
            )
        }

        if (showDialog.value) {
            ApkDialog(
                apkState.apkIcon,
                apk.repository,
                apk.applicationPackageName,
                apk.applicationVersionCode,
                apkState.apkLink,
                onDismissRequest = { showDialog.value = false },
                onRemoveRequest = { gitHubAPKViewModel.onRemoveRequest() },
                gitHubAPKViewModel.fetchNewIcon
            )
        }

    }

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
        onRemoveRequest: suspend () -> Unit,
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
        onRemoveRequest: suspend () -> Unit,
    ) {
        val settings by settingsInstance.settingsState.collectAsState()
        val context = LocalContext.current
        var openRemoveAlertDialog by remember { mutableStateOf(false) }

        Column(modifier = modifier) {
            applicationPackageName?.let {
                TextButton(onClick = {
                    Utils.uninstallApplication(context, it)
                    onDismissRequest()
                }) {
                    Text(stringResource(id = R.string.uninstall))
                }
            }
            if (applicationVersionCode != null && apkLink != null) {
                TextButton(onClick = {
                    onDismissRequest()
                    apkLink.let { Utils.installApplication(context, it) }
                }) {
                    Text(stringResource(id = R.string.reinstall))
                }
            }
            TextButton(onClick = {
                if (!settings.confirmationDialogRemove) {
                    onDismissRequest()
                    scope.launch {
                        onRemoveRequest()
                    }
                } else openRemoveAlertDialog = true

            }) {
                Text(stringResource(id = R.string.remove))
            }
        }

        RemoveAlertDialog(
            openRemoveAlertDialog,
            onDismissRequest = { openRemoveAlertDialog = false }) {
            openRemoveAlertDialog = false
            onDismissRequest()
            scope.launch {
                onRemoveRequest()
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
            Text(stringResource(id = R.string.update))
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

    @Composable
    private fun RemoveAlertDialog(
        opened: Boolean, onDismissRequest: () -> Unit, onConfirmation: () -> Unit
    ) {
        if (!opened) return
        ConfirmationAlertDialog(
            stringResource(R.string.confirmation_remove), onDismissRequest, onConfirmation
        )
    }

}
