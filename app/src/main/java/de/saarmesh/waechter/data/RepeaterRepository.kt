package de.saarmesh.waechter.data

import de.saarmesh.waechter.data.local.AppDatabase
import de.saarmesh.waechter.data.local.FavoriteEntity
import de.saarmesh.waechter.data.local.VerlaufEntity
import de.saarmesh.waechter.data.local.toDomain
import de.saarmesh.waechter.data.local.toEntity
import de.saarmesh.waechter.data.remote.AusfallApi
import de.saarmesh.waechter.data.remote.AusfallParser
import de.saarmesh.waechter.data.remote.Endpoints
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.domain.BatteryInfo
import de.saarmesh.waechter.domain.BatteryLevel
import de.saarmesh.waechter.domain.BatteryProfile
import de.saarmesh.waechter.domain.Favorite
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.RepeaterSnapshot
import de.saarmesh.waechter.domain.SightingHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

const val BATTERY_MIN_AGE_S = 3600L

@Singleton
class RepeaterRepository @Inject constructor(
    private val api: AusfallApi,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    val repeaters: Flow<List<Repeater>> = db.repeaters().observeAll().map { l -> l.map { it.toDomain() } }
    val favorites: Flow<List<Favorite>> = db.favorites().observeAll().map { l -> l.map { it.toDomain() } }
    val favoriteLabels: Flow<Map<String, String?>> = db.favorites().observeAll().map { l -> l.associate { it.publicKey to it.label } }

    fun repeater(pk: String): Flow<Repeater?> = db.repeaters().observe(pk).map { it?.toDomain() }
    fun favorite(pk: String): Flow<Favorite?> = db.favorites().observe(pk).map { it?.toDomain() }
    fun history(pk: String): Flow<SightingHistory?> =
        db.verlauf().observe(pk).map { v -> v?.let { SightingHistory(it.publicKey, it.fromTs, it.step, it.values) } }

    /** Laedt repeater.json und ersetzt den Cache. Wirft bei Netz- oder Formatfehlern. */
    suspend fun refresh(): RepeaterSnapshot = withContext(Dispatchers.IO) {
        val s = settings.current()
        try {
            val text = api.get(Endpoints.repeaterList(s.serverUrl)).use { it.string() }
            val snap = AusfallParser.parseList(text)
            db.repeaters().replaceAll(snap.repeaters.map { it.toEntity() })
            settings.markFetched(snap.generated, System.currentTimeMillis() / 1000)
            snap
        } catch (e: Exception) {
            settings.markError(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    suspend fun refreshHistory(pk: String) = withContext(Dispatchers.IO) {
        val s = settings.current()
        val text = api.get(Endpoints.verlauf(s.serverUrl, pk)).use { it.string() }
        val h = AusfallParser.parseVerlauf(text)
        db.verlauf().upsert(VerlaufEntity(pk, h.from, h.stepSeconds, h.values, System.currentTimeMillis() / 1000))
    }

    fun battery(pk: String): Flow<BatteryInfo?> = db.battery().observe(pk).map { it?.toDomain() }
    val batteries: Flow<Map<String, BatteryInfo>> =
        db.battery().observeAll().map { l -> l.associate { it.publicKey to it.toDomain() } }

    /**
     * Holt den Akku-Verlauf aus CoreScope, aber hoechstens alle [minAgeS] Sekunden je Repeater
     * (die Werte kommen ohnehin nur etwa stuendlich). 404 = Knoten unbekannt, dann null.
     */
    suspend fun refreshBattery(pk: String, minAgeS: Long = BATTERY_MIN_AGE_S): BatteryInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val cached = db.battery().get(pk)
        if (cached != null && now - cached.fetchedAt < minAgeS) return@withContext cached.toDomain()
        val resp = api.getResponse(Endpoints.battery(settings.current().serverUrl, pk))
        if (resp.code() == 404) {
            resp.errorBody()?.close()
            return@withContext null
        }
        if (!resp.isSuccessful) {
            resp.errorBody()?.close()
            throw java.io.IOException("HTTP ${resp.code()}")
        }
        val info = resp.body()!!.use { AusfallParser.parseBattery(it.string()) }
        db.battery().upsert(info.toEntity(now))
        info
    }

    suspend fun setBatteryLevel(pk: String, level: BatteryLevel?) = db.favorites().setBatteryLevel(pk, level?.name)

    /** Akku-Typ/eigene Schwellen setzen; die gemerkte Stufe wird zurueckgesetzt (kein Alarm durch die Umstellung). */
    suspend fun setBatteryProfile(pk: String, profile: BatteryProfile, lowMv: Int?, criticalMv: Int?) {
        db.favorites().get(pk)?.let {
            db.favorites().upsert(
                it.copy(
                    batteryProfile = profile.name.takeIf { profile != BatteryProfile.SERVER },
                    batteryLowMv = lowMv.takeIf { profile == BatteryProfile.CUSTOM },
                    batteryCriticalMv = criticalMv.takeIf { profile == BatteryProfile.CUSTOM },
                    batteryLevel = null,
                ),
            )
        }
    }

    suspend fun setFavorite(pk: String, favorite: Boolean, label: String?) {
        if (favorite) {
            val old = db.favorites().get(pk)
            db.favorites().upsert(old?.copy(label = label ?: old.label) ?: FavoriteEntity(pk, null, true, null, label))
        } else {
            db.favorites().delete(pk)
        }
    }

    suspend fun setThreshold(pk: String, hours: Int?) {
        db.favorites().get(pk)?.let { db.favorites().upsert(it.copy(offlineAfterHours = hours, wasDown = null)) }
    }

    suspend fun setNotify(pk: String, notify: Boolean) {
        db.favorites().get(pk)?.let { db.favorites().upsert(it.copy(notify = notify)) }
    }

    suspend fun allFavorites(): List<Favorite> = db.favorites().all().map { it.toDomain() }
    suspend fun allRepeaters(): List<Repeater> = db.repeaters().all().map { it.toDomain() }
    suspend fun setWasDown(pk: String, wasDown: Boolean?) = db.favorites().setWasDown(pk, wasDown)
}
