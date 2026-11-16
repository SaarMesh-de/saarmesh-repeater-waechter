package de.saarmesh.waechter.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import de.saarmesh.waechter.data.RepeaterRepository
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.domain.StatusLogic
import de.saarmesh.waechter.domain.TransitionDetector
import de.saarmesh.waechter.notify.Notifier
import de.saarmesh.waechter.widget.FavoritesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hintergrund-Abruf: laedt repeater.json, bewertet die Favoriten, meldet Statuswechsel
 * und aktualisiert das Widget. Nur HTTP-GET, nie etwas ins Mesh.
 */
@HiltWorker
class PollWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: RepeaterRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val snap = try {
            repo.refresh()
        } catch (e: Exception) {
            FavoritesWidget().updateAll(applicationContext)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        val byKey = snap.repeaters.associateBy { it.publicKey }
        val notifyOn = settings.current().notificationsEnabled
        for (fav in repo.allFavorites()) {
            val r = byKey[fav.publicKey] ?: continue      // nicht (mehr) in der Liste: nichts melden
            val status = StatusLogic.evaluate(r, snap.generated, fav.offlineAfterHours)
            val t = TransitionDetector.detect(fav.wasDown, status)
            if (t.event != null && fav.notify && notifyOn) notifier.statusChanged(r, t.event, snap.generated)
            if (t.wasDownNow != fav.wasDown) repo.setWasDown(fav.publicKey, t.wasDownNow)
        }
        FavoritesWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val PERIODIC = "poll-periodic"
        const val ONCE = "poll-once"
    }
}

@Singleton
class PollScheduler @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Plant den periodischen Abruf und plant neu, wenn sich das Intervall aendert. */
    fun ensureScheduled() {
        scope.launch {
            schedule(settings.current().pollMinutes, ExistingPeriodicWorkPolicy.KEEP)
            settings.settings.distinctUntilChangedBy { it.pollMinutes }.drop(1).collect {
                schedule(it.pollMinutes, ExistingPeriodicWorkPolicy.UPDATE)
            }
        }
    }

    private fun schedule(minutes: Int, policy: ExistingPeriodicWorkPolicy) {
        val req = PeriodicWorkRequestBuilder<PollWorker>(
            minutes.coerceAtLeast(SettingsRepository.MIN_POLL_MINUTES).toLong(), TimeUnit.MINUTES,
        ).setConstraints(constraints).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PollWorker.PERIODIC, policy, req)
    }

    fun runNow() {
        val req = OneTimeWorkRequestBuilder<PollWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(PollWorker.ONCE, ExistingWorkPolicy.REPLACE, req)
    }
}
