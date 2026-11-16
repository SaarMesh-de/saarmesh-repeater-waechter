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

/** Vorbelegte "Meine Repeater" (Public Keys aus live.saarmesh.de). */
object DefaultFavorites {
    val ALL = listOf(
        "1f869d80948dcb3274a235acbe4739235ce8c2144bfd3ff08e9ec0a737d80152" to "SaarMesh.de RoofRPT",
        "f9d7a6928b2cec4a6ef517c582141dfa05d04fd3a2ef184d82b0f56f4fc9ac8b" to "DE-SL-66127-1",
        "5000e4b74e1595832ac85f342d5d4fa8752642368786e40801854b0bab3bb891" to "DE-SL-66127-2",
        "955b4f831e56ab552c4c01551aac54d25ae624d34ac7a4927d4fd6e0203bec21" to "Saarmesh.de Observer",
    )
}

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

    suspend fun seedFavoritesIfFirstRun() {
        if (settings.consumeSeedFlag()) {
            db.favorites().upsertAll(DefaultFavorites.ALL.map { (pk, name) -> FavoriteEntity(pk, null, true, null, name) })
        }
    }

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
