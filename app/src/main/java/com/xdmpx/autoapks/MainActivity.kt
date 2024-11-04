package com.xdmpx.autoapks

import android.content.Context
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
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.xdmpx.autoapks.MainUI.MainScreen
import com.xdmpx.autoapks.about.About
import com.xdmpx.autoapks.apk.github.GitHubAPK
import com.xdmpx.autoapks.database.GitHubAPKDao
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.settings.Settings
import com.xdmpx.autoapks.settings.SettingsUI
import com.xdmpx.autoapks.ui.theme.AutoAPKsTheme
import com.xdmpx.autoapks.utils.Utils
import com.xdmpx.autoapks.utils.Utils.ShortToast
import com.xdmpx.autoapks.utils.VRequestQueue
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                                settings.downloading,
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

            val apks = database.getAll()

            if (apks.isEmpty() && settings.autoAddApksRepos) {
                fetchAutoAddApksRepos(this@MainActivity)
            }

            apks.forEach { it ->
                this@MainActivity.mainViewModel.addAPK(GitHubAPK(
                    it, this@MainActivity
                ) { gitHubAPK -> mainViewModel.removeAPK(gitHubAPK) })
            }
        }
    }

    private fun fetchAutoAddApksRepos(context: Context) {
        val requestQueue: RequestQueue = VRequestQueue.getInstance(context)
        val requestUrl: String =
            "https://raw.githubusercontent.com/xDMPx/AutoAPKs/main/ApksRepos.json"

        Log.d(TAG_DEBUG, "fetchAutoAddApksRepos")
        val apksReposJsonRequest = object : JsonArrayRequest(requestUrl, { response ->
            Log.d(TAG_DEBUG, "fetchAutoAddApksRepos::$requestUrl -> $response")
            for (i in 0..<response.length()) {
                val apkRepoData = response.getJSONObject(i)
                val repo = apkRepoData.getString("repository")
                val baseDirectory = apkRepoData.getString("base_directory")

                this@MainActivity.mainViewModel.addAPKRepository(
                    this@MainActivity, repo, baseDirectory
                )
            }
        }, { error ->
            Log.d(TAG_DEBUG, "fetchAutoAddApksRepos::ERROR::$requestUrl -> ${error.message}")
            // TODO: Handle error
        }) {
            override fun getHeaders(): Map<String, String> {
                val headers = HashMap<String, String>()
                headers["Accept"] = "application/json"
                return headers
            }
        }
        requestQueue.add(apksReposJsonRequest)
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
