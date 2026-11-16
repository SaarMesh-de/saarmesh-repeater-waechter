package de.saarmesh.waechter.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.saarmesh.waechter.R
import de.saarmesh.waechter.domain.Ago
import de.saarmesh.waechter.domain.AgoFormat
import de.saarmesh.waechter.domain.Region
import de.saarmesh.waechter.domain.RepeaterStatus

fun formatAgo(ctx: Context, seconds: Long): String = when (val a = AgoFormat.of(seconds)) {
    Ago.JustNow -> ctx.getString(R.string.ago_now)
    is Ago.Minutes -> ctx.getString(R.string.ago_min, a.n)
    is Ago.Hours -> ctx.getString(R.string.ago_h, a.n)
    is Ago.Days -> ctx.resources.getQuantityString(R.plurals.ago_days, a.n.toInt(), a.n)
}

fun formatDuration(ctx: Context, seconds: Long): String = when (val a = AgoFormat.of(seconds)) {
    Ago.JustNow -> ctx.getString(R.string.dur_lt_min)
    is Ago.Minutes -> ctx.getString(R.string.dur_min, a.n)
    is Ago.Hours -> ctx.getString(R.string.dur_h, a.n)
    is Ago.Days -> ctx.resources.getQuantityString(R.plurals.dur_days, a.n.toInt(), a.n)
}

@Composable
fun agoText(seconds: Long): String = formatAgo(LocalContext.current, seconds)

@Composable
fun durationText(seconds: Long): String = formatDuration(LocalContext.current, seconds)

@StringRes
fun RepeaterStatus.label(): Int = when (this) {
    RepeaterStatus.ONLINE -> R.string.status_online
    RepeaterStatus.UEBERFAELLIG -> R.string.status_overdue
    RepeaterStatus.OFFLINE -> R.string.status_offline
    RepeaterStatus.UNBEKANNT -> R.string.status_unknown
    RepeaterStatus.ERSETZT -> R.string.status_replaced
    RepeaterStatus.WENIG_DATEN -> R.string.status_few
}

@StringRes
fun RepeaterStatus.explanation(): Int = when (this) {
    RepeaterStatus.ONLINE -> R.string.status_online_desc
    RepeaterStatus.UEBERFAELLIG -> R.string.status_overdue_desc
    RepeaterStatus.OFFLINE -> R.string.status_offline_desc
    RepeaterStatus.UNBEKANNT -> R.string.status_unknown_desc
    RepeaterStatus.ERSETZT -> R.string.status_replaced_desc
    RepeaterStatus.WENIG_DATEN -> R.string.status_few_desc
}

/** Ampelfarben, bewusst fest (auch bei dynamischen Farben), damit die Bedeutung eindeutig bleibt. */
fun RepeaterStatus.color(dark: Boolean): Color = when (this) {
    RepeaterStatus.ONLINE -> if (dark) Color(0xFF51CF66) else Color(0xFF2F9E44)
    RepeaterStatus.UEBERFAELLIG -> if (dark) Color(0xFFFFA94D) else Color(0xFFE67700)
    RepeaterStatus.OFFLINE -> if (dark) Color(0xFFF06595) else Color(0xFFD6336C)
    RepeaterStatus.UNBEKANNT -> if (dark) Color(0xFF74C0FC) else Color(0xFF1C7ED6)
    RepeaterStatus.ERSETZT, RepeaterStatus.WENIG_DATEN -> if (dark) Color(0xFFADB5BD) else Color(0xFF868E96)
}

@StringRes
fun Region.label(): Int = when (this) {
    Region.SAARLAND -> R.string.region_sl
    Region.RHEINLAND_PFALZ -> R.string.region_rp
    Region.LUXEMBURG -> R.string.region_lu
    Region.LOTHRINGEN -> R.string.region_lor
    Region.SONSTIGE -> R.string.region_other
}
