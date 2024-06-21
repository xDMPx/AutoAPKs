package com.xdmpx.autoapks.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xdmpx.autoapks.R

object SettingsUI {

    private val settingPadding = 10.dp

    @Composable
    fun SettingsUI(settingsViewModel: SettingsViewModel, onNavigateToMain: () -> Unit) {
        val settingsState by settingsViewModel.settingsState.collectAsState()

        Scaffold(
            topBar = { SettingsTopAppBar(onNavigateToMain) },
        ) { innerPadding ->
            Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                LazyColumn(Modifier.padding(10.dp)) {
                    item {
                        Setting(stringResource(R.string.settings_pure_dark),
                            settingsState.usePureDark,
                            icon = { modifier ->
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_invert_colors_24),
                                    contentDescription = null,
                                    modifier = modifier
                                )
                            }) {
                            settingsViewModel.toggleUsePureDark()
                        }
                    }
                }
            }

        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsTopAppBar(onNavigateToMain: () -> Unit) {
        TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ), navigationIcon = {
            IconButton(onClick = { onNavigateToMain() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null
                )
            }
        }, title = {
            Text(
                stringResource(R.string.settings_screen),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }, actions = {})
    }

    @Composable
    fun Setting(
        text: String,
        checked: Boolean,
        icon: @Composable (modifier: Modifier) -> Unit = {},
        onCheckedChange: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) {
                    onCheckedChange()
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(0.75f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modifier = Modifier.padding(settingPadding)
                icon(modifier)
                Text(text = text, modifier = modifier)
            }
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
            }
        }
    }
}