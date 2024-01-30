package com.xdmpx.autoapks

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.coroutineScope
import com.xdmpx.autoapks.Utils.CustomDialog
import com.xdmpx.autoapks.Utils.ShortToast
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val TAG_DEBUG = "MainActivity"
    private lateinit var database: GitHubAPKDao
    private var apks = mutableStateListOf<GitHubAPK?>()
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            export(
                uri
            )
        }
    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> import(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoAPKsTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = { TopAppBar() },
                    ) { innerPadding ->
                        Main(Modifier.padding(innerPadding))
                    }
                }
            }
        }

        database = GitHubAPKDatabase.getInstance(this).gitHubAPKDatabase

        this.lifecycle.coroutineScope.launch {
            var apks = database.getAll()

            if (apks.isEmpty()) {
                val autoapks = GitHubAPKEntity("xDMPx/AutoAPKs")

                database.insertAll(autoapks)
                apks = database.getAll()
            }

            apks.forEach { it ->
                this@MainActivity.apks.add(GitHubAPK(
                    it, this@MainActivity
                ) { gitHubAPK -> removeAPKRepository(gitHubAPK) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_DEBUG, "onResume")
        apks.forEach {
            it?.refresh()
        }
    }

    private fun addAPKRepository(repository: String, baseDirectory: String) {
        this.lifecycle.coroutineScope.launch {
            val apk = GitHubAPKEntity(repository, baseDirectory = baseDirectory)
            val apkId = database.insert(apk)
            apks.add(GitHubAPK(
                database.get(apkId), this@MainActivity
            ) { gitHubAPK -> removeAPKRepository(gitHubAPK) })
        }
    }

    private fun removeAPKRepository(apk: GitHubAPK) {
        apks[apks.indexOf(apk)] = null
    }

    @Composable
    @Preview
    fun Main(modifier: Modifier = Modifier) {
        val apks = remember { apks }
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
                    apk.ApkCard()
                }
            }
            AddAPKRepository(
                Alignment.BottomCenter,
                Modifier
                    .fillMaxSize()
                    .weight(1f - apksColumnWeight)
                    .padding(16.dp)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopAppBar() {
        TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ), title = { Text("AutoAPKs") }, actions = {
            TopAppBarMenu()
        })
    }

    @Composable
    fun TopAppBarMenu() {
        var expanded by remember { mutableStateOf(false) }

        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Filled.Menu, contentDescription = "Top Bar Menu"
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(text = "Export") }, onClick = {
                expanded = false
                val date = LocalDate.now()
                val year = date.year
                val month = String.format("%02d", date.monthValue)
                val day = date.dayOfMonth
                createDocument.launch("apks_export_${year}_${month}_$day.json")
            })
            DropdownMenuItem(text = { Text(text = "Import") }, onClick = {
                expanded = false
                openDocument.launch(arrayOf("application/json"))
            })
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
            AddAPKRepositoryDialog(onAddRequest = { userInput, baseDirectory ->
                addAPKRepository(
                    userInput, baseDirectory
                )
            }, onDismissRequest = { showDialog.value = !showDialog.value })
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
                label = { Text("Repository URL") },
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
                    Text(text = "Advance")
                }
                if (expanded) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 1,
                        label = { Text("Base directory") },
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
                Text("Cancel")
            }
            AddAPKRepositoryButton(userInput, baseDirectory, onDismissRequest, onAddRequest)
        }
    }

    @Composable
    private fun AddAPKRepositoryButton(
        userInput: String,
        baseDirectory: String,
        onDismissRequest: () -> Unit,
        onAddRequest: (userInput: String, baseDirectory: String) -> Unit

    ) {
        TextButton(
            onClick = {
                val repo = Utils.userInputToAPKRepository(userInput)
                this.lifecycle.coroutineScope.launch {
                    repo?.let { repo ->
                        if (database.getRepositoryByName(repo).isNullOrBlank()) {
                            GitHubRepoFetcher.validateAndroidAPKRepository(
                                repo, baseDirectory, this@MainActivity
                            ) {
                                if (it) {
                                    onAddRequest(repo, baseDirectory)
                                    ShortToast(this@MainActivity, "Added $repo")
                                } else {
                                    ShortToast(this@MainActivity, "Invalid Android APP Repository")
                                }
                            }
                        } else {
                            ShortToast(this@MainActivity, "Android APP Repository already added")
                        }
                    }
                    if (repo.isNullOrBlank()) {
                        ShortToast(this@MainActivity, "Invalid Android APP Repository")
                    }
                }
                onDismissRequest()
            },
            modifier = Modifier.padding(8.dp),
        ) {
            Text("Add")
        }
    }

    private fun export(uri: Uri?) {
        if (uri == null) {
            return
        }

        Log.d(TAG_DEBUG, "export")
        this.lifecycle.coroutineScope.launch {
            val repositories = database.getExportData().map {
                val jsonObject = JSONObject()
                jsonObject.put("repository", it.repository)
                jsonObject.put("base_directory", it.baseDirectory)
            }
            val json = JSONArray(repositories)
            Log.d(TAG_DEBUG, "export -> $json")
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toString().toByteArray())
                }
            } catch (e: Exception) {
                ShortToast(this@MainActivity, "Error exporting data")
            }
        }
    }

    private fun import(uri: Uri?) {
        if (uri == null) {
            return
        }

        Log.d(TAG_DEBUG, "import")
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                this.lifecycle.coroutineScope.launch {
                    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                    val importedJson = JSONArray(bufferedReader.readText())
                    inputStream.close()

                    val repositories = database.getRepositories()

                    val toImport =
                        (0 until importedJson.length()).map { importedJson.getJSONObject(it) }
                            .filter { it.getString("repository") !in repositories }.toList()

                    toImport.forEach {
                        addAPKRepository(it.getString("repository"), it.getString("base_directory"))
                    }

                    Log.d(TAG_DEBUG, "import -> $toImport")
                }
            }
        } catch (e: Exception) {
            ShortToast(this@MainActivity, "Error importing data")
        }
    }

}
