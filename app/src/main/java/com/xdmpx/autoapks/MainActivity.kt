package com.xdmpx.autoapks

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xdmpx.autoapks.MainUI.MainScreen
import com.xdmpx.autoapks.about.About
import com.xdmpx.autoapks.apk.github.GitHubAPK
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
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
    private val mainViewModel = MainViewModel(listOf())
    private val settingsInstance = Settings.getInstance()
    private lateinit var database: GitHubAPKDao
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
        settingsInstance.registerOnExportClick { this@MainActivity.exportToJSON() }
        settingsInstance.registerOnImportClick { this@MainActivity.importFromJSON() }
        settingsInstance.registerOnDeleteAllClick { this@MainActivity.mainViewModel.deleteAll() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsInstance.settingsState.collectAsState()
            val mainState by mainViewModel.mainState.collectAsState()

            AutoAPKsTheme(
                pureDarkTheme = settings.usePureDark,
                dynamicColor = settings.useDynamicColor,
                theme = settings.theme
            ) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(mainState.apks.toList(),
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToAbout = {
                                    navController.navigate("about")
                                }) { r, b ->
                                mainViewModel.addAPKRepository(this@MainActivity, r, b)
                            }
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

            var apks = database.getAll()

            if (apks.isEmpty() && settings.autoAddApksRepos) {
                val autoapks = GitHubAPKEntity("xDMPx/AutoAPKs")
                val normscount = GitHubAPKEntity("xDMPx/NormsCount")

                database.insertAll(autoapks, normscount)
                apks = database.getAll()
            }

            apks.forEach { it ->
                this@MainActivity.mainViewModel.addAPK(GitHubAPK(
                    it, this@MainActivity
                ) { gitHubAPK -> mainViewModel.removeAPK(gitHubAPK) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_DEBUG, "onResume")
        this@MainActivity.mainViewModel.mainState.value.apks.forEach {
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
                    mainViewModel.addAPKRepository(
                        this@MainActivity, repository, baseDirectory
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
