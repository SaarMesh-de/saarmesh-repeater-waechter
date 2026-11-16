package de.saarmesh.waechter.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.saarmesh.waechter.R
import de.saarmesh.waechter.data.remote.Endpoints
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.ui.common.StatusDot
import de.saarmesh.waechter.ui.common.agoText
import de.saarmesh.waechter.ui.common.durationText
import de.saarmesh.waechter.ui.common.explanation
import de.saarmesh.waechter.ui.common.label
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val THRESHOLD_CHOICES = listOf(2, 4, 6, 12, 24, 48, 72)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val r = st.repeater
    val base = st.settings?.serverUrl ?: ""
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r?.name ?: st.pk.take(8)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        if (st.favorite != null) {
                            Icon(Icons.Filled.Star, stringResource(R.string.unfavorite), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.StarOutline, stringResource(R.string.favorite))
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (r == null) {
                Text(stringResource(R.string.detail_not_in_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { uri.openUri(Endpoints.node(base, st.pk)) }) { Text(stringResource(R.string.link_node)) }
                return@Column
            }
            StatusCard(st, r, onOpen)
            if (st.favorite != null) AlarmCard(st, vm)
            HistoryCard(st, r, vm)
            if (r.lat != null && r.lon != null) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.detail_position), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        RepeaterMap(
                            r.lat, r.lon, r.name,
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(
                            "%.5f, %.5f".format(r.lat, r.lon) + " · " + stringResource(R.string.osm_attribution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LinksCard(base, st.pk) { uri.openUri(it) }
            Text(
                stringResource(R.string.detail_key, r.publicKey),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(st: DetailState, r: Repeater, onOpen: (String) -> Unit) {
    val now = System.currentTimeMillis() / 1000
    val status = st.status ?: return
    val df = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(status.label()), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(status.explanation()), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            KV(stringResource(R.string.kv_last_seen), agoText(now - r.lastSeen) + " (" + df.format(Instant.ofEpochSecond(r.lastSeen)) + ")")
            r.art?.let { KV(stringResource(R.string.kv_kind), it) }
            r.via?.let { KV(stringResource(R.string.kv_via), it) }
            st.region?.let { KV(stringResource(R.string.kv_region), stringResource(it.label())) }
            r.typicalGap?.let { g50 ->
                KV(stringResource(R.string.kv_gap), durationText(g50) + (r.rareGap?.let { " / " + durationText(it) } ?: ""))
            }
            val custom = st.favorite?.offlineAfterHours
            val down = custom?.let { it * 3600L } ?: r.downAfter
            down?.let { KV(stringResource(R.string.kv_down_after), durationText(it) + if (custom != null) " " + stringResource(R.string.kv_custom) else "") }
            if (r.replacedByKey != null) {
                TextButton(onClick = { onOpen(r.replacedByKey) }) {
                    Text(stringResource(R.string.replaced_by, r.replacedByName ?: r.replacedByKey.take(8)))
                }
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(v, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AlarmCard(st: DetailState, vm: DetailViewModel) {
    val fav = st.favorite ?: return
    var open by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alarm_notify), Modifier.weight(1f))
                Switch(checked = fav.notify, onCheckedChange = vm::setNotify)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.alarm_threshold), Modifier.weight(1f))
                Column {
                    AssistChip(
                        onClick = { open = true },
                        label = {
                            Text(fav.offlineAfterHours?.let { stringResource(R.string.alarm_hours, it) } ?: stringResource(R.string.alarm_auto))
                        },
                    )
                    DropdownMenu(open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.alarm_auto)) }, onClick = { vm.setThreshold(null); open = false })
                        THRESHOLD_CHOICES.forEach { h ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_hours, h)) }, onClick = { vm.setThreshold(h); open = false })
                        }
                    }
                }
            }
            Text(stringResource(R.string.alarm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCard(st: DetailState, r: Repeater, vm: DetailViewModel) {
    var mode by rememberSaveable { mutableStateOf(0) }
    val windowDays = r.daily.size
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.history_days, windowDays), stringResource(R.string.history_hours7)).forEachIndexed { i, l ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            val h = st.history
            if (mode == 0) {
                val from = (st.settings?.lastGenerated ?: 0) - windowDays * 86400L
                SightingChart(r.daily, 288, from, 86400, stringResource(R.string.history_title))
            } else if (h != null && h.values.isNotEmpty()) {
                val last = h.values.takeLast(7 * 24)
                val from = h.from + (h.values.size - last.size) * h.stepSeconds
                SightingChart(last, (h.stepSeconds / 300).toInt().coerceAtLeast(1), from, h.stepSeconds, stringResource(R.string.history_title))
            } else if (st.historyError) {
                TextButton(onClick = { vm.loadHistory() }) { Text(stringResource(R.string.history_retry)) }
            } else {
                Text(stringResource(R.string.list_loading), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksCard(base: String, pk: String, open: (String) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.links_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.link_node to Endpoints.node(base, pk),
                    R.string.link_ausfall to Endpoints.ausfall(base),
                    R.string.link_relevanz to Endpoints.relevanz(base),
                    R.string.link_clock to Endpoints.clock(base),
                    R.string.link_positionen to Endpoints.positionen(base),
                    R.string.link_statistik to Endpoints.statistik(base),
                ).forEach { (res, url) ->
                    AssistChip(
                        onClick = { open(url) },
                        label = { Text(stringResource(res)) },
                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.width(16.dp)) },
                    )
                }
            }
        }
    }
}
