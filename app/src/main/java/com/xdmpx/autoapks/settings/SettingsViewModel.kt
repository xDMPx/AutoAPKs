package com.xdmpx.autoapks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.ViewModel
import com.xdmpx.autoapks.datastore.SettingsProto
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
)

class SettingsViewModel : ViewModel() {

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    lateinit var onThemeUpdate: (Boolean) -> Unit

    fun registerOnThemeUpdate(onThemeUpdate: (Boolean) -> Unit) {
        this.onThemeUpdate = onThemeUpdate
    }

    fun toggleUsePureDark() {
        _settingsState.value.let {
            _settingsState.value = it.copy(usePureDark = !it.usePureDark)
        }
        onThemeUpdate(
            _settingsState.value.usePureDark,
        )
    }


    suspend fun loadSettings(context: Context) {
        val settingsData = context.settingsDataStore.data.catch { }.first()
        _settingsState.value.let {
            _settingsState.value = it.copy(
                usePureDark = settingsData.usePureDark,
            )
        }
    }

    suspend fun saveSettings(context: Context) {
        context.settingsDataStore.updateData {
            it.toBuilder().apply {
                usePureDark = this@SettingsViewModel._settingsState.value.usePureDark
            }.build()
        }
    }

}