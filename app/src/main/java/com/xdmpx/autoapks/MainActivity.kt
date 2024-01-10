package com.xdmpx.autoapks

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
        apks.forEach {
            it.refresh()
        }
    }

    private fun addAPKRepository(repository: String) {
        this.lifecycle.coroutineScope.launch {
            val apk = GitHubAPKEntity(repository)
            database.insertAll(apk)
            apks.add(GitHubAPK(apk, this@MainActivity))
        }
    }

    @Composable
    @Preview
    fun Main(modifier: Modifier = Modifier) {
        val apks = remember { apks }
        val apksColumnWeight = 0.80f
        val addGitHubAPKWeight = 0.175f

        Column(modifier) {
            Column(
                modifier
                    .fillMaxSize()
                    .weight(apksColumnWeight)
            ) {
                for (apk in apks) {
                    Spacer(modifier = Modifier.size(10.dp))
                    apk.ApkCard(modifier)
                }
            }
            AddAPKRepository(
                Alignment.BottomCenter,
                modifier
                    .fillMaxSize()
                    .weight(addGitHubAPKWeight)
            )
            Spacer(
                modifier = modifier
                    .fillMaxSize()
                    .weight(1f - apksColumnWeight - addGitHubAPKWeight)
            )

        }
    }

    @Composable
    fun AddAPKRepository(contentAlignment: Alignment, modifier: Modifier = Modifier) {
        val showDialog = remember { mutableStateOf(false) }

        Box(contentAlignment = contentAlignment, modifier = modifier) {
            FloatingActionButton(
                onClick = { showDialog.value = !showDialog.value },
            ) {
                Icon(Icons.Filled.Add, "Add GitHub apk repository")
            }
        }

        if (showDialog.value) {
            AddAPKRepositoryDialog(onAddRequest = { userInput -> addAPKRepository(userInput) },
                onDismissRequest = { showDialog.value = !showDialog.value })
        }
    }

    @Composable
    fun AddAPKRepositoryDialog(
        onDismissRequest: () -> Unit, onAddRequest: (userInput: String) -> Unit
    ) {
        var userInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedTextField(
                                value = userInput,
                                onValueChange = { userInput = it },
                                label = { Text("Repository URL") },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(
                                onClick = { onDismissRequest() },
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("Cancel")
                            }
                            TextButton(
                                onClick = {
                                    Utils.userInputToAPKRepository(userInput)
                                        ?.let { onAddRequest(it) }
                                    onDismissRequest()
                                },
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("Add")
                            }
                        }

                    }
                }
            }
        }
    }
}
