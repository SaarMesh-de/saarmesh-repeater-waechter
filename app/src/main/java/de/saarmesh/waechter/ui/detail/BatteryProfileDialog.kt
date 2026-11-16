package de.saarmesh.waechter.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.saarmesh.waechter.R
import de.saarmesh.waechter.domain.BatteryProfile
import de.saarmesh.waechter.domain.BatteryThresholdLogic
import java.util.Locale

@StringRes
fun BatteryProfile.label(): Int = when (this) {
    BatteryProfile.SERVER -> R.string.bprof_server
    BatteryProfile.LI_ION -> R.string.bprof_liion
    BatteryProfile.LIFEPO4 -> R.string.bprof_lifepo4
    BatteryProfile.CUSTOM -> R.string.bprof_custom
    BatteryProfile.NONE -> R.string.bprof_none
    BatteryProfile.INHERO_MR2 -> R.string.bprof_inhero
}

private fun v(mv: Int?) = mv?.let { String.format(Locale.GERMANY, "%.2f", it / 1000.0) } ?: ""

/** Akku-Typ waehlen; bei "eigene Werte" zwei Felder in Volt. */
@Composable
fun BatteryProfileDialog(
    current: BatteryProfile,
    lowMv: Int?,
    criticalMv: Int?,
    onDismiss: () -> Unit,
    onSave: (BatteryProfile, Int?, Int?) -> Unit,
) {
    var sel by rememberSaveable { mutableStateOf(current) }
    var low by rememberSaveable { mutableStateOf(v(lowMv)) }
    var crit by rememberSaveable { mutableStateOf(v(criticalMv)) }
    val lowParsed = BatteryThresholdLogic.parseVoltsOrMv(low)
    val critParsed = BatteryThresholdLogic.parseVoltsOrMv(crit)
    val customOk = BatteryThresholdLogic.isValid(lowParsed, critParsed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bprof_title)) },
        text = {
            Column {
                BatteryProfile.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = sel == p, onClick = { sel = p }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sel == p, onClick = { sel = p })
                        Column {
                            Text(stringResource(p.label()))
                            if (p.showsSoc) {
                                Text(
                                    stringResource(R.string.bprof_inhero_values),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (p.lowMv != null) {
                                Text(
                                    stringResource(R.string.bprof_values, v(p.lowMv), v(p.criticalMv)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (sel == BatteryProfile.CUSTOM) {
                    OutlinedTextField(
                        low, { low = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.bprof_low)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = low.isNotBlank() && lowParsed == null,
                    )
                    OutlinedTextField(
                        crit, { crit = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.bprof_crit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = crit.isNotBlank() && critParsed == null,
                    )
                    if (!customOk && low.isNotBlank() && crit.isNotBlank()) {
                        Text(stringResource(R.string.bprof_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.bprof_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = sel != BatteryProfile.CUSTOM || customOk,
                onClick = { onSave(sel, lowParsed, critParsed) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
