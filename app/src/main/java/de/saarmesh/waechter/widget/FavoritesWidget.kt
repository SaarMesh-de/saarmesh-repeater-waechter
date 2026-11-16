package de.saarmesh.waechter.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.saarmesh.waechter.MainActivity
import de.saarmesh.waechter.R
import de.saarmesh.waechter.data.RepeaterRepository
import de.saarmesh.waechter.data.settings.SettingsRepository
import de.saarmesh.waechter.domain.RepeaterStatus
import de.saarmesh.waechter.domain.StatusLogic
import de.saarmesh.waechter.ui.common.color
import de.saarmesh.waechter.ui.common.formatAgo

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repo(): RepeaterRepository
    fun settings(): SettingsRepository
}

private data class WidgetRow(val name: String, val status: RepeaterStatus?, val ago: String)

/** Kompakte Ampel fuer die Favoriten. Liest nur den lokalen Cache. */
class FavoritesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repo = ep.repo()
        val s = ep.settings().current()
        val all = repo.allRepeaters().associateBy { it.publicKey }
        val now = System.currentTimeMillis() / 1000
        val rows = repo.allFavorites().map { f ->
            val r = all[f.publicKey]
            if (r == null) {
                WidgetRow(f.publicKey.take(8), null, "–")
            } else {
                WidgetRow(r.name, StatusLogic.evaluate(r, s.lastGenerated, f.offlineAfterHours), formatAgo(context, now - r.lastSeen))
            }
        }.sortedWith(compareBy({ it.status?.ordinal?.let { o -> -severity(o) } ?: 0 }, { it.name.lowercase() }))
        val stand = if (s.lastGenerated > 0) context.getString(R.string.widget_stand, formatAgo(context, now - s.lastGenerated)) else context.getString(R.string.widget_no_data)
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        provideContent {
            GlanceTheme {
                Content(context, rows, stand, dark)
            }
        }
    }

    private fun severity(ordinal: Int): Int = when (RepeaterStatus.entries[ordinal]) {
        RepeaterStatus.OFFLINE -> 3
        RepeaterStatus.UEBERFAELLIG -> 2
        RepeaterStatus.UNBEKANNT -> 1
        else -> 0
    }

    @Composable
    private fun Content(context: Context, rows: List<WidgetRow>, stand: String, dark: Boolean) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp).padding(10.dp).clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                context.getString(R.string.widget_title),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            )
            Spacer(GlanceModifier.height(4.dp))
            if (rows.isEmpty()) {
                Text(context.getString(R.string.widget_empty), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                LazyColumn(GlanceModifier.defaultWeight()) {
                    items(rows) { row ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            val c = row.status?.color(dark) ?: Color.Gray
                            Box(GlanceModifier.size(12.dp).cornerRadius(6.dp).background(ColorProvider(c))) {}
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                row.name, maxLines = 1,
                                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text(row.ago, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                        }
                    }
                }
            }
            Text(stand, style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
