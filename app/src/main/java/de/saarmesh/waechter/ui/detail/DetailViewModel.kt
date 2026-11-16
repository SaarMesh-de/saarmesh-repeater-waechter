package de.saarmesh.waechter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.saarmesh.waechter.data.RepeaterRepository
import de.saarmesh.waechter.data.settings.AppSettings
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.domain.BatteryInfo
import de.saarmesh.waechter.domain.Favorite
import de.saarmesh.waechter.domain.Region
import de.saarmesh.waechter.domain.RegionResolver
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.RepeaterStatus
import de.saarmesh.waechter.domain.SightingHistory
import de.saarmesh.waechter.domain.StatusLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val pk: String = "",
    val repeater: Repeater? = null,
    val favorite: Favorite? = null,
    val status: RepeaterStatus? = null,
    val region: Region? = null,
    val history: SightingHistory? = null,
    val settings: AppSettings? = null,
    val historyError: Boolean = false,
    val battery: BatteryInfo? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: RepeaterRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val pk: String = checkNotNull(handle["pk"])
    private val historyError = MutableStateFlow(false)

    val state: StateFlow<DetailState> = combine(
        repo.repeater(pk), repo.favorite(pk), repo.history(pk), settingsRepo.settings, historyError,
    ) { r, f, h, s, he ->
        DetailState(
            pk = pk, repeater = r, favorite = f,
            status = r?.let { StatusLogic.evaluate(it, s.lastGenerated, f?.offlineAfterHours) },
            region = r?.let { RegionResolver.resolve(it.name, it.lat, it.lon) },
            history = h, settings = s, historyError = he,
        )
    }.combine(repo.battery(pk)) { st, b -> st.copy(battery = b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState(pk = pk))

    init {
        loadHistory()
        viewModelScope.launch {
            // Beim Oeffnen frischer als im Hintergrund, aber nicht bei jedem Antippen neu.
            try { repo.refreshBattery(pk, minAgeS = 600) } catch (_: Exception) { }
        }
    }

    fun loadHistory() = viewModelScope.launch {
        historyError.value = try {
            repo.refreshHistory(pk); false
        } catch (_: Exception) {
            true
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = state.value
        repo.setFavorite(pk, s.favorite == null, s.repeater?.name)
    }

    fun setThreshold(hours: Int?) = viewModelScope.launch { repo.setThreshold(pk, hours) }
    fun setNotify(b: Boolean) = viewModelScope.launch { repo.setNotify(pk, b) }
}
