package com.xdmpx.autoapks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme

class MainActivity : ComponentActivity() {
    private val TAG_DEBUG = "MainActivity"
    private lateinit var apks: SnapshotStateList<GitHubAPK>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoAPKsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Main()
                }
            }
        }

        apks = mutableStateListOf(
            GitHubAPK("element-hq/element-x-android", this),
            GitHubAPK("wikimedia/apps-android-wikipedia", this)
        )
    }

    @Composable
    fun Main(modifier: Modifier = Modifier) {
        val apks = remember { apks }
        Column {
            Column {
                for (apk in apks) {
                    apk.ApkCard(modifier)
                }
            }
        }
    }
}
