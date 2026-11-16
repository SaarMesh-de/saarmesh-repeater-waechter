package de.saarmesh.waechter.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Balkendiagramm der Sichtungen. Werte werden auf [maxPerBar] normiert (Anteil der
 * 5-Minuten-Fenster mit mindestens einer Sichtung), damit 100 % "immer gehoert" bedeutet.
 */
@Composable
fun SightingChart(
    values: List<Int>,
    maxPerBar: Int,
    fromTs: Long,
    stepSeconds: Long,
    description: String,
    modifier: Modifier = Modifier,
) {
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fmt = DateTimeFormatter.ofPattern(if (stepSeconds >= 86400) "dd.MM." else "EE dd.MM.")
        .withZone(ZoneId.systemDefault())
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description },
        ) {
            if (values.isEmpty()) return@Canvas
            val labels = listOf(0f, 0.5f, 1f).map { it to measurer.measure("${(it * 100).toInt()} %", labelStyle) }
            val gutter = labels.maxOf { it.second.size.width } + 6.dp.toPx()
            val top = labels.first().second.size.height / 2f
            val h = size.height - top
            val w = (size.width - gutter) / values.size
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            labels.forEach { (f, l) ->
                val y = size.height - h * f
                if (f > 0f) drawLine(grid, Offset(gutter, y), Offset(size.width, y), pathEffect = dash)
                drawText(l, topLeft = Offset(gutter - 4.dp.toPx() - l.size.width, (y - l.size.height / 2f).coerceAtMost(size.height - l.size.height)))
            }
            values.forEachIndexed { i, v ->
                val frac = (v.toFloat() / maxPerBar).coerceIn(0f, 1f)
                if (v == 0) {
                    // Luecke sichtbar machen: kleiner roter Strich an der Grundlinie
                    drawRect(empty, Offset(gutter + i * w, size.height - 3f), Size((w - 1f).coerceAtLeast(1f), 3f))
                } else {
                    val bh = (h * frac).coerceAtLeast(2f)
                    drawRect(bar, Offset(gutter + i * w, size.height - bh), Size((w * 0.85f).coerceAtLeast(1f), bh))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp)) {
            Text(fmt.format(Instant.ofEpochSecond(fromTs)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                fmt.format(Instant.ofEpochSecond(fromTs + stepSeconds * (values.size - 1))),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
