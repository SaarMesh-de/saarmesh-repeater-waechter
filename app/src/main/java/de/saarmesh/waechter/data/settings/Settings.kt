package de.saarmesh.waechter.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.saarmesh.waechter.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val serverUrl: String,
    val pollMinutes: Int,
    val notificationsEnabled: Boolean,
    /** Erzeugungszeit der zuletzt erfolgreich geladenen Serverdaten (Unix-s), 0 = nie. */
    val lastGenerated: Long,
    /** Zeitpunkt des letzten erfolgreichen Abrufs (Unix-s). */
    val lastFetch: Long,
    val lastError: String?,
)

@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private object K {
        val server = stringPreferencesKey("server_url")
        val poll = intPreferencesKey("poll_minutes")
        val notify = booleanPreferencesKey("notifications_enabled")
        val generated = longPreferencesKey("last_generated")
        val fetch = longPreferencesKey("last_fetch")
        val error = stringPreferencesKey("last_error")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        serverUrl = this[K.server] ?: BuildConfig.DEFAULT_SERVER_URL,
        pollMinutes = this[K.poll] ?: DEFAULT_POLL_MINUTES,
        notificationsEnabled = this[K.notify] ?: true,
        lastGenerated = this[K.generated] ?: 0,
        lastFetch = this[K.fetch] ?: 0,
        lastError = this[K.error],
    )

    suspend fun setServerUrl(url: String) = context.dataStore.edit { it[K.server] = url.trim() }
    suspend fun setPollMinutes(m: Int) = context.dataStore.edit { it[K.poll] = m.coerceAtLeast(MIN_POLL_MINUTES) }
    suspend fun setNotificationsEnabled(b: Boolean) = context.dataStore.edit { it[K.notify] = b }

    suspend fun markFetched(generated: Long, at: Long) = context.dataStore.edit {
        it[K.generated] = generated
        it[K.fetch] = at
        it.remove(K.error)
    }

    suspend fun markError(msg: String) = context.dataStore.edit { it[K.error] = msg }


    companion object {
        const val DEFAULT_POLL_MINUTES = 15
        /** WorkManager erlaubt periodische Arbeit nicht haeufiger als alle 15 Minuten. */
        const val MIN_POLL_MINUTES = 15
        val POLL_CHOICES = listOf(15, 30, 60, 120, 360)
    }
}
