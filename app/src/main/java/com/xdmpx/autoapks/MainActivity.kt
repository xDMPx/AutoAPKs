package com.xdmpx.autoapks

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xdmpx.autoapks.MainUI.AddAPKRepository
import com.xdmpx.autoapks.about.About
import com.xdmpx.autoapks.apk.ApkUI.ApkCard
import com.xdmpx.autoapks.apk.github.GitHubAPK
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import com.xdmpx.autoapks.datastore.ThemeType
import com.xdmpx.autoapks.settings.Settings
import com.xdmpx.autoapks.settings.SettingsUI
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme
import com.xdmpx.autoapks.utils.Utils
import com.xdmpx.autoapks.utils.Utils.ShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val TAG_DEBUG = "MainActivity"
    private val settingsInstance = Settings.getInstance()
    private var usePureDark = mutableStateOf(false)
    private var useDynamicColor = mutableStateOf(false)
    private var theme = mutableStateOf(ThemeType.SYSTEM)
    private lateinit var database: GitHubAPKDao
    private var apks = mutableStateListOf<GitHubAPK?>()
    private val scopeIO = CoroutineScope(Dispatchers.IO)

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

        scopeIO.launch {
            settingsInstance.loadSettings(this@MainActivity)
            val settings = settingsInstance.settingsState.value
            usePureDark.value = settings.usePureDark
            useDynamicColor.value = settings.useDynamicColor
            theme.value = settings.theme

            var apks = database.getAll()

            if (apks.isEmpty() && settings.autoAddApksRepos) {
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
        scopeIO.launch {
            settingsInstance.saveSettings(this@MainActivity)
            Log.d(TAG_DEBUG, "onStop")
        }
        super.onStop()
    }

    private fun addAPKRepository(repository: String, baseDirectory: String) {
        scopeIO.launch {
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
            topBar = { MainUI.TopAppBar(onNavigateToAbout, onNavigateToSettings) },
        ) { innerPadding ->
            Main(Modifier.padding(innerPadding))
        }
    }

    @Composable
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
            ) { r, b -> addAPKRepository(r, b) }
        }
    }


    private fun deleteAll() {
        scopeIO.launch {
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

        scopeIO.launch {
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

        scopeIO.launch {
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
