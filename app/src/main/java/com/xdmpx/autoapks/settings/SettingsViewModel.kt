package com.xdmpx.autoapks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.ViewModel
import com.xdmpx.autoapks.datastore.SettingsProto
import com.xdmpx.autoapks.datastore.ThemeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

val Context.settingsDataStore: DataStore<SettingsProto> by dataStore(
    fileName = "settings.pb", serializer = SettingsSerializer
)

data class SettingsState(
    val usePureDark: Boolean = false,
    val useDynamicColor: Boolean = true,
    val theme: ThemeType = ThemeType.SYSTEM,
    val confirmationDialogRemove: Boolean = true,
    val autoAddApksRepos: Boolean = true,
    val downloading: Boolean = false,
)

class SettingsViewModel : ViewModel() {

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    lateinit var onDeleteAllClick: () -> Unit
    lateinit var onExportClick: () -> Unit
    lateinit var onImportClick: () -> Unit

    fun registerOnDeleteAllClick(onDeleteAllClick: () -> Unit) {
        this.onDeleteAllClick = onDeleteAllClick
    }

    fun registerOnExportClick(onExportClick: () -> Unit) {
        this.onExportClick = onExportClick
    }

    fun registerOnImportClick(onImportClick: () -> Unit) {
        this.onImportClick = onImportClick
    }

    fun toggleUsePureDark() {
        _settingsState.value.let {
            _settingsState.value = it.copy(usePureDark = !it.usePureDark)
        }
    }

    fun toggleUseDynamicColor() {
        _settingsState.value.let {
            _settingsState.value = it.copy(useDynamicColor = !it.useDynamicColor)
        }
    }

    fun toggleConfirmationDialogRemove() {
        _settingsState.value.let {
            _settingsState.value = it.copy(confirmationDialogRemove = !it.confirmationDialogRemove)
        }
    }

    fun toggleAutoAddApksRepos() {
        _settingsState.value.let {
            _settingsState.value = it.copy(autoAddApksRepos = !it.autoAddApksRepos)
        }
    }

    fun setTheme(theme: ThemeType) {
        _settingsState.value.let {
            _settingsState.value = it.copy(theme = theme)
        }
    }

    fun setDownloading(downloading: Boolean) {
        _settingsState.value.let {
            _settingsState.value = it.copy(downloading = downloading)
        }
    }

    suspend fun loadSettings(context: Context) {
        val settingsData = context.settingsDataStore.data.catch { }.first()
        _settingsState.value.let {
            _settingsState.value = it.copy(
                usePureDark = settingsData.usePureDark,
                useDynamicColor = settingsData.useDynamicColor,
                theme = settingsData.theme,
                confirmationDialogRemove = settingsData.confirmationDialogRemove,
                autoAddApksRepos = settingsData.autoAddApksRepos,
            )
        }
    }

    suspend fun saveSettings(context: Context) {
        context.settingsDataStore.updateData {
            it.toBuilder().apply {
                usePureDark = this@SettingsViewModel._settingsState.value.usePureDark
                useDynamicColor = this@SettingsViewModel._settingsState.value.useDynamicColor
                theme = this@SettingsViewModel._settingsState.value.theme
                confirmationDialogRemove =
                    this@SettingsViewModel._settingsState.value.confirmationDialogRemove
                autoAddApksRepos = this@SettingsViewModel._settingsState.value.autoAddApksRepos
            }.build()
        }
    }

}
