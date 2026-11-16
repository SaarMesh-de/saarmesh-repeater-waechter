package de.saarmesh.waechter.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.saarmesh.waechter.data.RepeaterRepository
import de.saarmesh.waechter.data.settings.AppSettings
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.domain.Region
import de.saarmesh.waechter.domain.RegionResolver
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.RepeaterStatus
import de.saarmesh.waechter.domain.StatusLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepeaterItem(
    val repeater: Repeater,
    val status: RepeaterStatus,
    val region: Region,
    val favorite: Boolean,
)

/** Filter "Status" fasst die Ampel zusammen: online / ueberfaellig / offline / sonstige. */
enum class StatusFilter { ALLE, ONLINE, UEBERFAELLIG, OFFLINE, SONSTIGE }

data class ListFilter(
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALLE,
    val region: Region? = null,
    val onlyFavorites: Boolean = false,
)

data class ListState(
    val items: List<RepeaterItem> = emptyList(),
    val counts: Map<StatusFilter, Int> = emptyMap(),
    val filter: ListFilter = ListFilter(),
    val settings: AppSettings? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val favoriteCount: Int = 0,
)

fun RepeaterStatus.toFilter(): StatusFilter = when (this) {
    RepeaterStatus.ONLINE -> StatusFilter.ONLINE
    RepeaterStatus.UEBERFAELLIG -> StatusFilter.UEBERFAELLIG
    RepeaterStatus.OFFLINE -> StatusFilter.OFFLINE
    else -> StatusFilter.SONSTIGE
}

/** Filtern und Sortieren als reine Funktion (Favoriten zuerst, dann Schwere, dann Name). */
fun applyFilter(items: List<RepeaterItem>, f: ListFilter): List<RepeaterItem> {
    val q = f.query.trim().lowercase()
    return items.asSequence()
        .filter { !f.onlyFavorites || it.favorite }
        .filter { f.status == StatusFilter.ALLE || it.status.toFilter() == f.status }
        .filter { f.region == null || it.region == f.region }
        .filter { q.isEmpty() || it.repeater.name.lowercase().contains(q) || it.repeater.publicKey.startsWith(q) }
        .sortedWith(
            compareByDescending<RepeaterItem> { it.favorite }
                .thenBy { severityRank(it.status) }
                .thenBy { it.repeater.name.lowercase() },
        )
        .toList()
}

private fun severityRank(s: RepeaterStatus) = when (s) {
    RepeaterStatus.OFFLINE -> 0
    RepeaterStatus.UEBERFAELLIG -> 1
    RepeaterStatus.UNBEKANNT -> 2
    RepeaterStatus.ONLINE -> 3
    RepeaterStatus.WENIG_DATEN -> 4
    RepeaterStatus.ERSETZT -> 5
}

@HiltViewModel
class ListViewModel @Inject constructor(
    private val repo: RepeaterRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(ListFilter())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val all = combine(repo.repeaters, repo.favorites, settingsRepo.settings) { reps, favs, s ->
        val fav = favs.associateBy { it.publicKey }
        val items = reps.map { r ->
            val f = fav[r.publicKey]
            RepeaterItem(r, StatusLogic.evaluate(r, s.lastGenerated, f?.offlineAfterHours), RegionResolver.resolve(r.name, r.lat, r.lon), f != null)
        }
        Triple(items, s, favs.size)
    }

    val state: StateFlow<ListState> = combine(all, filter, refreshing, error) { (items, s, favCount), f, rf, err ->
        ListState(
            items = applyFilter(items, f),
            counts = items.groupingBy { it.status.toFilter() }.eachCount(),
            filter = f, settings = s, refreshing = rf, error = err, loaded = s.lastFetch > 0,
            favoriteCount = favCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = try {
                repo.refresh(); null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            refreshing.value = false
        }
    }

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setStatus(s: StatusFilter) = filter.update { it.copy(status = s) }
    fun setRegion(r: Region?) = filter.update { it.copy(region = r) }
    fun setOnlyFavorites(b: Boolean) = filter.update { it.copy(onlyFavorites = b) }
    fun toggleFavorite(item: RepeaterItem) = viewModelScope.launch {
        repo.setFavorite(item.repeater.publicKey, !item.favorite, item.repeater.name)
    }
    fun dismissError() { error.value = null }
}
