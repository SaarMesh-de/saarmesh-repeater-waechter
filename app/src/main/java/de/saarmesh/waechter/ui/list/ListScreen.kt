package de.saarmesh.waechter.ui.list

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.saarmesh.waechter.R
import de.saarmesh.waechter.domain.Region
import de.saarmesh.waechter.ui.common.StatusDot
import de.saarmesh.waechter.ui.common.agoText
import de.saarmesh.waechter.ui.common.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, vm: ListViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis() / 1000

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = st.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                item { NotificationPermissionBanner() }
                item {
                    OutlinedTextField(
                        value = st.filter.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (st.filter.query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) }
                            }
                        },
                        singleLine = true,
                    )
                }
                item { Filters(st, vm) }
                item { DataAge(st, now) }
                if (st.items.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (st.loaded) R.string.list_empty else R.string.list_loading),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(st.items, key = { it.repeater.publicKey }) { item ->
                    RepeaterRow(item, now, onClick = { onOpen(item.repeater.publicKey) }, onStar = { vm.toggleFavorite(item) })
                    HorizontalDivider(Modifier.padding(start = 44.dp))
                }
            }
        }
    }
}

@Composable
private fun Filters(st: ListState, vm: ListViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = st.filter.onlyFavorites,
            onClick = { vm.setOnlyFavorites(!st.filter.onlyFavorites) },
            label = { Text(stringResource(R.string.filter_mine)) },
            leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.padding(0.dp)) },
        )
        val labels = mapOf(
            StatusFilter.ONLINE to R.string.status_online,
            StatusFilter.UEBERFAELLIG to R.string.status_overdue,
            StatusFilter.OFFLINE to R.string.status_offline,
            StatusFilter.SONSTIGE to R.string.status_other,
        )
        labels.forEach { (f, res) ->
            FilterChip(
                selected = st.filter.status == f,
                onClick = { vm.setStatus(if (st.filter.status == f) StatusFilter.ALLE else f) },
                label = { Text(stringResource(res) + " (" + (st.counts[f] ?: 0) + ")") },
            )
        }
        var open by remember { mutableStateOf(false) }
        Column {
            FilterChip(
                selected = st.filter.region != null,
                onClick = { open = true },
                label = { Text(st.filter.region?.let { stringResource(it.label()) } ?: stringResource(R.string.filter_region)) },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_region_all)) }, onClick = { vm.setRegion(null); open = false })
                Region.entries.forEach { r ->
                    DropdownMenuItem(text = { Text(stringResource(r.label())) }, onClick = { vm.setRegion(r); open = false })
                }
            }
        }
    }
}

@Composable
private fun DataAge(st: ListState, now: Long) {
    val s = st.settings ?: return
    val txt = when {
        s.lastGenerated > 0 -> stringResource(R.string.data_stand, agoText(now - s.lastGenerated))
        else -> stringResource(R.string.data_none)
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(txt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        st.error?.let {
            Text(
                stringResource(R.string.data_error, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RepeaterRow(item: RepeaterItem, now: Long, onClick: () -> Unit, onStar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(item.status)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.repeater.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(item.status.label()) + " · " + agoText(now - item.repeater.lastSeen) + " · " + stringResource(item.region.label()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onStar) {
            if (item.favorite) {
                Icon(Icons.Filled.Star, stringResource(R.string.unfavorite), tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Outlined.StarOutline, stringResource(R.string.favorite))
            }
        }
    }
}

/** Android 13+: Benachrichtigungsberechtigung mit Erklaerung erfragen, statt beim Start ungefragt. */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted || dismissed) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.perm_text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.later)) }
                TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow)) }
            }
        }
    }
}
