package com.xdmpx.autoapks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getString
import com.xdmpx.autoapks.apk.ApkUI.ApkCard
import com.xdmpx.autoapks.apk.github.GitHubAPK
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.utils.Utils
import com.xdmpx.autoapks.utils.Utils.CustomDialog
import com.xdmpx.autoapks.utils.Utils.ShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

object MainUI {
    private val scopeIO = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar(
        onNavigateToAbout: () -> Unit,
        onNavigateToSettings: () -> Unit,
    ) {
        androidx.compose.material3.TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ), title = { Text("AutoAPKs") }, actions = {
            TopAppBarMenu(onNavigateToAbout, onNavigateToSettings)
        })
    }

    @Composable
    fun TopAppBarMenu(
        onNavigateToAbout: () -> Unit,
        onNavigateToSettings: () -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }

        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(id = R.string.topappbar_menu_des)
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(text = stringResource(id = R.string.screen_settings)) },
                onClick = {
                    expanded = false
                    onNavigateToSettings()
                })
            DropdownMenuItem(text = { Text(text = stringResource(id = R.string.screen_about)) },
                onClick = {
                    expanded = false
                    onNavigateToAbout()
                })
        }

    }

    @Composable
    fun MainScreen(
        apks: List<GitHubAPK?>,
        downloading: Boolean,
        onNavigateToAbout: () -> Unit,
        onNavigateToSettings: () -> Unit,
        addAPKRepository: (repository: String, baseDirectory: String) -> Unit
    ) {
        Scaffold(
            topBar = { TopAppBar(onNavigateToAbout, onNavigateToSettings) },
        ) { innerPadding ->
            MainUI(apks, Modifier.padding(innerPadding), addAPKRepository)
        }

        if (downloading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = 0.5f))
            ) {
                IndeterminateCircularIndicator()
            }
        }
    }

    @Composable
    fun MainUI(
        apks: List<GitHubAPK?>,
        modifier: Modifier = Modifier,
        addAPKRepository: (repository: String, baseDirectory: String) -> Unit
    ) {
        val apksColumnWeight = 0.83f

        Column(modifier) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .weight(apksColumnWeight)
            ) {
                items(apks) { apk ->
                    if (apk == null) return@items
                    Spacer(modifier = Modifier.size(10.dp))
                    ApkCard(apk)
                }
            }
            AddAPKRepository(
                Alignment.BottomCenter,
                Modifier
                    .fillMaxSize()
                    .weight(1f - apksColumnWeight)
                    .padding(16.dp),
                addAPKRepository
            )
        }
    }

    @Composable
    fun AddAPKRepositoryDialog(
        onDismissRequest: () -> Unit,
        onAddRequest: (userInput: String, baseDirectory: String) -> Unit
    ) {
        var userInput by remember { mutableStateOf("") }
        var baseDirectory by remember { mutableStateOf("app") }

        CustomDialog(onDismissRequest) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        AddAPKRepositoryTextField(userInput, { userInput = it })
                        AddAPKRepositoryAdvance(baseDirectory, { baseDirectory = it })
                    }
                    AddAPKRepositoryDialogButtons(
                        userInput, baseDirectory, onDismissRequest, onAddRequest
                    )
                }
            }
        }
    }

    @Composable
    fun AddAPKRepositoryTextField(
        value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier, contentAlignment = Alignment.Center
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                minLines = 2,
                maxLines = 2,
                label = { Text(stringResource(id = R.string.repo_url)) },
            )
        }
    }

    @Composable
    fun AddAPKRepositoryAdvance(
        value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier
    ) {
        var expanded by remember { mutableStateOf(false) }

        Box(
            modifier = modifier, contentAlignment = Alignment.TopStart
        ) {
            Column {
                TextButton(
                    onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null
                    )
                    Text(text = stringResource(id = R.string.advance))
                }
                if (expanded) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 1,
                        label = { Text(stringResource(id = R.string.base_directory)) },
                    )
                }
            }
        }
    }

    @Composable
    private fun AddAPKRepositoryDialogButtons(
        userInput: String,
        baseDirectory: String,
        onDismissRequest: () -> Unit,
        onAddRequest: (userInput: String, baseDirectory: String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(
                onClick = { onDismissRequest() },
                modifier = Modifier.padding(8.dp),
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
            AddAPKRepositoryButton(userInput, baseDirectory, onDismissRequest, onAddRequest)
        }
    }

    @Composable
    fun AddAPKRepository(
        contentAlignment: Alignment,
        modifier: Modifier = Modifier,
        addAPKRepository: (repository: String, baseDirectory: String) -> Unit
    ) {
        val showDialog = remember { mutableStateOf(false) }

        Box(contentAlignment = contentAlignment, modifier = modifier) {
            FloatingActionButton(
                onClick = { showDialog.value = !showDialog.value },
            ) {
                Icon(Icons.Filled.Add, stringResource(id = R.string.add_github_apk))
            }
        }

        if (showDialog.value) {
            AddAPKRepositoryDialog(onAddRequest = { userInput, baseDirectory ->
                addAPKRepository(
                    userInput, baseDirectory
                )
            }, onDismissRequest = { showDialog.value = !showDialog.value })
        }
    }

    @Composable
    private fun AddAPKRepositoryButton(
        userInput: String,
        baseDirectory: String,
        onDismissRequest: () -> Unit,
        onAddRequest: (userInput: String, baseDirectory: String) -> Unit
    ) {
        val context = LocalContext.current
        val database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
        TextButton(
            onClick = {
                val repo = Utils.userInputToAPKRepository(userInput)
                Utils.isValidRepository(repo, baseDirectory, context) { valid ->
                    if (!valid) {
                        MainScope().launch {
                            ShortToast(
                                context, text = getString(context, R.string.invalid_repo)
                            )
                        }
                        return@isValidRepository
                    }
                    scopeIO.launch {
                        if (database.getRepositoryByName(repo!!).isNullOrBlank()) {
                            onAddRequest(repo, baseDirectory)
                            MainScope().launch {
                                ShortToast(
                                    context, "${getString(context, R.string.added)} $repo"
                                )
                            }
                        } else {
                            MainScope().launch {
                                ShortToast(context, getString(context, R.string.repo_already_added))
                            }
                        }
                    }
                }

                onDismissRequest()
            },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(stringResource(id = R.string.add))
        }
    }

    @Composable
    fun IndeterminateCircularIndicator() {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}