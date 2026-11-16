package de.saarmesh.waechter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.saarmesh.waechter.BuildConfig
import de.saarmesh.waechter.R
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.work.PollScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val scheduler: PollScheduler,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setServer(url: String) = viewModelScope.launch { repo.setServerUrl(url); scheduler.runNow() }
    fun setPoll(m: Int) = viewModelScope.launch { repo.setPollMinutes(m) }
    fun setNotify(b: Boolean) = viewModelScope.launch { repo.setNotificationsEnabled(b) }
}

/** Akzeptiert nur http(s)-URLs ohne Pfad-Muell; leer = Standard. */
fun isValidServerUrl(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty()) return true
    return Regex("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?/?$").matches(t)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    LaunchedEffect(s?.serverUrl) { s?.serverUrl?.let { url = it } }
    var pollOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { pad ->
        val cur = s ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.set_server), style = MaterialTheme.typography.titleMedium)
            val valid = isValidServerUrl(url)
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                isError = !valid,
                supportingText = { Text(stringResource(if (valid) R.string.set_server_hint else R.string.set_server_invalid)) },
            )
            Row {
                TextButton(enabled = valid && url.trim() != cur.serverUrl, onClick = { vm.setServer(url.ifBlank { BuildConfig.DEFAULT_SERVER_URL }) }) {
                    Text(stringResource(R.string.save))
                }
                TextButton(onClick = { url = BuildConfig.DEFAULT_SERVER_URL; vm.setServer(BuildConfig.DEFAULT_SERVER_URL) }) {
                    Text(stringResource(R.string.set_server_reset))
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_poll), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.set_poll_hint), style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    AssistChip(onClick = { pollOpen = true }, label = { Text(stringResource(R.string.minutes, cur.pollMinutes)) })
                    DropdownMenu(pollOpen, { pollOpen = false }) {
                        SettingsRepository.POLL_CHOICES.forEach { m ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.minutes, m)) }, onClick = { vm.setPoll(m); pollOpen = false })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_notify), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.set_notify_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(cur.notificationsEnabled, vm::setNotify)
            }
            HorizontalDivider()
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall)
        }
    }
}
