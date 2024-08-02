package com.xdmpx.autoapks

import android.content.Context
import androidx.lifecycle.ViewModel
import com.xdmpx.autoapks.apk.github.GitHubAPK
import com.xdmpx.autoapks.database.GitHubAPKDatabase
import com.xdmpx.autoapks.database.GitHubAPKEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainState(
    val apks: List<GitHubAPK?>
)

class MainViewModel(apks: List<GitHubAPK?>) : ViewModel() {

    private val _mainState = MutableStateFlow(MainState(apks))
    val mainState: StateFlow<MainState> = _mainState.asStateFlow()

    private val scopeIO = CoroutineScope(Dispatchers.IO)

    fun addAPK(apk: GitHubAPK) {
        _mainState.value.let {
            _mainState.value = it.copy(apks = it.apks.plus(apk))
        }
    }

    fun removeAPK(apk: GitHubAPK) {
        _mainState.value.let {
            val apkIndex = it.apks.indexOf(apk)
            val apks = it.apks.mapIndexed { i, a -> if (i == apkIndex) null else a }
            _mainState.value = it.copy(apks = apks)
        }
    }

    fun addAPKRepository(context: Context, repository: String, baseDirectory: String) {
        val database = GitHubAPKDatabase.getInstance(context).gitHubAPKDatabase
        scopeIO.launch {
            val apk = GitHubAPKEntity(repository, baseDirectory = baseDirectory)
            val apkId = database.insert(apk)
            addAPK(GitHubAPK(
                database.get(apkId), context
            ) { gitHubAPK -> removeAPK(gitHubAPK) })
        }
    }

    fun deleteAll() {
        scopeIO.launch {
            for (i in mainState.value.apks.indices) {
                val apk = mainState.value.apks[i]
                apk?.onRemoveRequest()
                if (apk != null) {
                    removeAPK(apk)
                }
            }
        }
    }

}