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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xdmpx.autoapks.ApkUI.ApkCard
import com.xdmpx.autoapks.Utils.CustomDialog
import com.xdmpx.autoapks.Utils.ShortToast
import com.xdmpx.autoapks.about.About
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.datastore.ThemeType
import com.xdmpx.autoapks.settings.Settings
import com.xdmpx.autoapks.settings.SettingsUI
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val TAG_DEBUG = "MainActivity"
    private val settingsInstance = Settings.getInstance()
    private var usePureDark = mutableStateOf(false)
    private var useDynamicColor = mutableStateOf(false)
    private var theme = mutableStateOf(ThemeType.SYSTEM)
    private lateinit var database: GitHubAPKDao
    private var apks = mutableStateListOf<GitHubAPK?>()
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            exportToJSONCallback(
                uri
            )
        }
    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            importFromJSONCallback(
                uri
            )
        }

    init {
        settingsInstance.registerOnDeleteAllClick { this@MainActivity.deleteAll() }
        settingsInstance.registerOnExportClick { this@MainActivity.exportToJSON() }
        settingsInstance.registerOnImportClick { this@MainActivity.importFromJSON() }
        settingsInstance.registerOnDeleteAllClick { this@MainActivity.deleteAll() }
        settingsInstance.registerOnThemeUpdate { usePureDark, useDynamicColor, theme ->
            this@MainActivity.usePureDark.value = usePureDark
            this@MainActivity.useDynamicColor.value = useDynamicColor
            this@MainActivity.theme.value = theme
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoAPKsTheme(
                pureDarkTheme = usePureDark.value,
                dynamicColor = useDynamicColor.value,
                theme = theme.value
            ) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainUI(onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToAbout = {
                                    navController.navigate("about")
                                })
                        }
                        composable("settings") {
                            SettingsUI.SettingsUI(settingsInstance) {
                                navController.navigate("main")
                            }
                        }
                        composable("about") {
                            About.AboutUI {
                                navController.navigate("main")
                            }
                        }
                    }
                }
            }
        }

        database = GitHubAPKDatabase.getInstance(this).gitHubAPKDatabase

        this.lifecycle.coroutineScope.launch {
            settingsInstance.loadSettings(this@MainActivity)
            val settings = settingsInstance.settingsState.value
            usePureDark.value = settings.usePureDark
            useDynamicColor.value = settings.useDynamicColor
            theme.value = settings.theme

            var apks = database.getAll()

            if (apks.isEmpty()) {
                val autoapks = GitHubAPKEntity("xDMPx/AutoAPKs")
                val normscount = GitHubAPKEntity("xDMPx/NormsCount")

                database.insertAll(autoapks, normscount)
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
            it?.refresh(this@MainActivity)
        }
    }

    override fun onStop() {
        this.lifecycle.coroutineScope.launch {
            settingsInstance.saveSettings(this@MainActivity)
        }
        super.onStop()
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
    fun MainUI(
        onNavigateToAbout: () -> Unit,
        onNavigateToSettings: () -> Unit,
    ) {
        Scaffold(
            topBar = { TopAppBar(onNavigateToAbout, onNavigateToSettings) },
        ) { innerPadding ->
            Main(Modifier.padding(innerPadding))
        }
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
                    ApkCard(apk)
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
    fun TopAppBar(
        onNavigateToAbout: () -> Unit,
        onNavigateToSettings: () -> Unit,
    ) {
        TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
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
                imageVector = Icons.Filled.Menu, contentDescription = "Top Bar Menu"
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(text = "Settings") }, onClick = {
                expanded = false
                onNavigateToSettings()
            })
            DropdownMenuItem(text = { Text(text = "About") }, onClick = {
                expanded = false
                onNavigateToAbout()
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

    private fun deleteAll() {
        this.lifecycle.coroutineScope.launch {
            for (i in apks.indices) {
                val apk = apks[i]
                apk?.onRemoveRequest()
                apks[i] = null
            }
        }
    }

    private fun exportToJSON() {
        val date = LocalDate.now()
        val year = date.year
        val month = String.format(null, "%02d", date.monthValue)
        val day = date.dayOfMonth
        createDocument.launch("apks_export_${year}_${month}_$day.json")
    }


    private fun exportToJSONCallback(uri: Uri?) {
        if (uri == null) return

        this.lifecycle.coroutineScope.launch {
            if (Utils.exportToJSON(this@MainActivity, uri)) {
                runOnUiThread {
                    ShortToast(
                        this@MainActivity, resources.getString(R.string.export_successful)
                    )
                }
            } else {
                runOnUiThread {
                    ShortToast(
                        this@MainActivity, resources.getString(R.string.export_failed)
                    )
                }
            }
        }
    }

    private fun importFromJSON() {
        openDocument.launch(arrayOf("application/json"))
    }

    private fun importFromJSONCallback(uri: Uri?) {
        if (uri == null) return

        this.lifecycle.coroutineScope.launch {
            if (Utils.importFromJSON(this@MainActivity, uri, addAPK = { repository, baseDirectory ->
                    addAPKRepository(
                        repository, baseDirectory
                    )
                })) {
                runOnUiThread {
                    ShortToast(
                        this@MainActivity, resources.getString(R.string.import_successful)
                    )
                }
            } else {
                runOnUiThread {
                    ShortToast(
                        this@MainActivity, resources.getString(R.string.import_failed)
                    )
                }
            }
        }
    }

}
