package de.saarmesh.waechter.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.saarmesh.waechter.R
import de.saarmesh.waechter.domain.BatteryInfo
import de.saarmesh.waechter.domain.BatteryLevel
import de.saarmesh.waechter.domain.BatteryLogic
import de.saarmesh.waechter.domain.BatteryProfile
import de.saarmesh.waechter.domain.BatterySample
import de.saarmesh.waechter.domain.BatteryThresholdLogic
import de.saarmesh.waechter.domain.InheroSoc
import de.saarmesh.waechter.domain.VoltageAxisLogic
import de.saarmesh.waechter.ui.common.agoText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun BatteryLevel.color(dark: Boolean): Color = when (this) {
    BatteryLevel.OK -> if (dark) Color(0xFF51CF66) else Color(0xFF2F9E44)
    BatteryLevel.LOW -> if (dark) Color(0xFFFFA94D) else Color(0xFFE67700)
    BatteryLevel.CRITICAL -> if (dark) Color(0xFFF06595) else Color(0xFFD6336C)
}

private fun volts(mv: Int) = String.format(Locale.GERMANY, "%.2f V", mv / 1000.0)


private fun percent(v: Double) = String.format(Locale.GERMANY, "%.0f %%", v)

/** Wert so, wie er angezeigt wird: Millivolt oder (Inhero MR2) Ladestand in Zehntelprozent. */
private fun displayValue(profile: BatteryProfile, mv: Int): Int =
    if (profile.showsSoc) Math.round(InheroSoc.mvToSoc(mv) * 10).toInt() else mv

private fun formatValue(profile: BatteryProfile, mv: Int): String =
    if (profile.showsSoc) percent(InheroSoc.mvToSoc(mv)) else volts(mv)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryCard(b: BatteryInfo, profile: BatteryProfile = BatteryProfile.SERVER, onEditProfile: (() -> Unit)? = null) {
    val now = System.currentTimeMillis() / 1000
    val mv = b.latestMv ?: return
    val hasBattery = profile.hasBattery
    val assessable = BatteryThresholdLogic.assessable(profile, mv)
    val level = BatteryLogic.level(mv, b.lowMv, b.criticalMv)
    val dark = isSystemInDarkTheme()
    val stale = BatteryLogic.isStale(b, now)
    var range by rememberSaveable { mutableIntStateOf(0) }
    val days = if (range == 0) 7 else 30

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    when {
                        !hasBattery -> R.string.batt_title_supply
                        profile.showsSoc -> R.string.batt_title_soc
                        else -> R.string.batt_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatValue(profile, mv), style = MaterialTheme.typography.headlineMedium,
                    color = if (assessable) level.color(dark) else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                if (hasBattery) Text(
                    stringResource(
                        if (!assessable) R.string.batt_not_assessable else when (level) {
                            BatteryLevel.OK -> R.string.batt_ok
                            BatteryLevel.LOW -> R.string.batt_low
                            BatteryLevel.CRITICAL -> R.string.batt_critical
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (profile.showsSoc) {
                Text(
                    stringResource(R.string.batt_soc_reported, volts(mv)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.batt_measured, agoText(now - (b.latestTs ?: now))) +
                    if (stale) " · " + stringResource(R.string.batt_stale) else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(trendText(b, profile, now), style = MaterialTheme.typography.bodySmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.batt_7d), stringResource(R.string.batt_30d)).forEachIndexed { i, l ->
                    SegmentedButton(selected = range == i, onClick = { range = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            val shown = b.samples.filter { it.ts >= now - days * 86400L }
            if (shown.size >= 2) {
                BatteryChart(b, shown, now - days * 86400L, now, profile, assessable, stringResource(R.string.batt_title))
            } else {
                Text(stringResource(R.string.batt_few), style = MaterialTheme.typography.bodySmall)
            }
            if (BatteryThresholdLogic.implausible(profile, mv)) {
                Text(
                    stringResource(R.string.batt_implausible, volts(mv), stringResource(profile.label()), volts(profile.maxPlausibleMv!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !hasBattery -> stringResource(R.string.batt_no_battery)
                        profile.showsSoc -> stringResource(
                            R.string.batt_thresholds, stringResource(profile.label()),
                            percent(InheroSoc.LOW_SOC), percent(InheroSoc.CRITICAL_SOC),
                        )
                        else -> stringResource(R.string.batt_thresholds, stringResource(profile.label()), volts(b.lowMv), volts(b.criticalMv))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onEditProfile != null) {
                    TextButton(onClick = onEditProfile) { Text(stringResource(R.string.batt_change)) }
                }
            }
            if (profile.showsSoc) {
                Text(stringResource(R.string.batt_soc_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.batt_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun trendText(b: BatteryInfo, profile: BatteryProfile, now: Long): String {
    if (profile.showsSoc) {
        val soc = b.samples.map { BatterySample(it.ts, Math.round(InheroSoc.mvToSoc(it.mv) * 10).toInt()) }
        val t = BatteryLogic.trendMvPerDay(soc, now)?.div(10.0)
        return when {
            t == null -> stringResource(R.string.batt_trend_none)
            abs(t) < 1.0 -> stringResource(R.string.batt_trend_stable)
            else -> stringResource(R.string.batt_trend_soc, String.format(Locale.GERMANY, "%+.0f", t))
        }
    }
    val t = BatteryLogic.trendMvPerDay(b.samples, now)
    return when {
        t == null -> stringResource(R.string.batt_trend_none)
        abs(t) < 10 -> stringResource(R.string.batt_trend_stable)
        else -> stringResource(R.string.batt_trend, String.format(Locale.GERMANY, "%+.0f", t))
    }
}

@Composable
private fun BatteryChart(
    b: BatteryInfo,
    s: List<BatterySample>,
    from: Long,
    to: Long,
    profile: BatteryProfile,
    showThresholds: Boolean,
    desc: String,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val dark = isSystemInDarkTheme()
    val lowC = BatteryLevel.LOW.color(dark)
    val critC = BatteryLevel.CRITICAL.color(dark)
    val soc = profile.showsSoc
    // Ladestand: Werte in ganzen Prozent; Spannung: Millivolt
    val values = s.map { if (soc) Math.round(InheroSoc.mvToSoc(it.mv)).toInt() else it.mv }
    val thresholds = when {
        !showThresholds -> emptyList()
        soc -> listOf(InheroSoc.LOW_SOC.toInt(), InheroSoc.CRITICAL_SOC.toInt())
        else -> listOf(b.lowMv, b.criticalMv)
    }
    val axis = if (soc) VoltageAxisLogic.socAxis(values, thresholds) else VoltageAxisLogic.axis(values, thresholds)
    val decimals = if (axis.stepMv % 100 == 0) 1 else 2
    val measurer = rememberTextMeasurer()
    val fmt = DateTimeFormatter.ofPattern("dd.MM.").withZone(ZoneId.systemDefault())
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = desc }) {
            val labels = axis.ticksMv.map { v ->
                measurer.measure(if (soc) "$v %" else String.format(Locale.GERMANY, "%.${decimals}f", v / 1000.0), labelStyle)
            }
            val gutter = (labels.maxOf { it.size.width } + 6.dp.toPx())
            val top = labels.first().size.height / 2f
            val bottom = size.height - labels.first().size.height / 2f
            val w = size.width - gutter
            fun x(ts: Long) = gutter + ((ts - from).toFloat() / (to - from)) * w
            fun y(v: Int) = bottom - ((v - axis.minMv).toFloat() / (axis.maxMv - axis.minMv)) * (bottom - top)
            // Skala + Gitter
            axis.ticksMv.forEachIndexed { i, v ->
                val yy = y(v)
                drawLine(grid, Offset(gutter, yy), Offset(size.width, yy), strokeWidth = 1f)
                val l = labels[i]
                drawText(l, topLeft = Offset(gutter - 4.dp.toPx() - l.size.width, yy - l.size.height / 2f))
            }
            // Schwellen nur, wenn im sichtbaren Bereich
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            thresholds.zip(listOf(lowC, critC)).forEach { (v, c) ->
                if (v in axis.minMv..axis.maxMv) drawLine(c, Offset(gutter, y(v)), Offset(size.width, y(v)), strokeWidth = 2f, pathEffect = dash)
            }
            val p = Path()
            s.forEachIndexed { i, smp ->
                // Luecken > 6 h nicht verbinden (Abfrage-Station war weg)
                val yy = y(values[i])
                if (i == 0 || smp.ts - s[i - 1].ts > 6 * 3600) p.moveTo(x(smp.ts), yy) else p.lineTo(x(smp.ts), yy)
            }
            drawPath(p, line, style = Stroke(width = 4f))
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp)) {
            Text(fmt.format(Instant.ofEpochSecond(from)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(if (soc) R.string.batt_axis_soc else R.string.batt_axis_unit), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(fmt.format(Instant.ofEpochSecond(to)), style = MaterialTheme.typography.labelSmall)
        }
    }
}
