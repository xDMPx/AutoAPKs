package com.xdmpx.autoapks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat
import com.xdmpx.autoapks.ui.theme.getColorSchemeEx

object ApkUI {

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