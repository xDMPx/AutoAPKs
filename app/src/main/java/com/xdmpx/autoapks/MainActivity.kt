package com.xdmpx.autoapks

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val TAG_DEBUG = "MainActivity"
    private lateinit var database: GitHubAPKDao
    private var apks = mutableStateListOf<GitHubAPK>()

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

        database = GitHubAPKDatabase.getInstance(this).gitHubAPKDatabase

        this.lifecycle.coroutineScope.launch {
            var apks = database.getAll()

            if (apks.isEmpty()) {
                val element = GitHubAPKEntity("element-hq/element-x-android")
                val wikipedia = GitHubAPKEntity("wikimedia/apps-android-wikipedia")
                val duckduckgo = GitHubAPKEntity("duckduckgo/Android")
                val syncthing = GitHubAPKEntity("syncthing/syncthing-android")

                database.insertAll(element, wikipedia, duckduckgo, syncthing)
                apks = database.getAll()
            }

            apks.forEach {
                this@MainActivity.apks.add(GitHubAPK(it, this@MainActivity))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_DEBUG, "onResume")
        apks.forEach{
            it.refresh()
        }
    }

    @Composable
    fun Main(modifier: Modifier = Modifier) {
        val apks = remember { apks }
        Column(modifier) {
            Column(modifier) {
                for (apk in apks) {
                    Spacer(modifier = Modifier.size(10.dp))
                    apk.ApkCard(modifier)
                }
            }
        }
    }
}
