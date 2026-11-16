package de.saarmesh.waechter.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.saarmesh.waechter.MainActivity
import de.saarmesh.waechter.R
import de.saarmesh.waechter.domain.Repeater
import de.saarmesh.waechter.domain.StatusEvent
import de.saarmesh.waechter.ui.common.formatDuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val ctx: Context) {

    fun createChannels() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, ctx.getString(R.string.channel_status), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.channel_status_desc)
            },
        )
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun statusChanged(r: Repeater, event: StatusEvent, referenceTime: Long) {
        if (!canNotify()) return
        val (title, icon) = when (event) {
            StatusEvent.WentOffline -> ctx.getString(R.string.notif_offline_title, r.name) to R.drawable.ic_stat_offline
            StatusEvent.BackOnline -> ctx.getString(R.string.notif_online_title, r.name) to R.drawable.ic_stat_online
        }
        val text = ctx.getString(R.string.notif_last_seen, formatDuration(ctx, referenceTime - r.lastSeen))
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_PK, r.publicKey)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, r.publicKey.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(r.publicKey.hashCode(), n)
        } catch (_: SecurityException) {
            // Berechtigung wurde zwischenzeitlich entzogen
        }
    }

    companion object {
        const val CH_STATUS = "status"
    }
}
