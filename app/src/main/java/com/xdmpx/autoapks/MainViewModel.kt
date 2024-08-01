package com.xdmpx.autoapks

import androidx.lifecycle.ViewModel
import com.xdmpx.autoapks.apk.github.GitHubAPK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainState(
    val apks: List<GitHubAPK?>
)

class MainViewModel(apks: List<GitHubAPK?>) : ViewModel() {

    private val _mainState = MutableStateFlow(MainState(apks))
    val mainState: StateFlow<MainState> = _mainState.asStateFlow()

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

}