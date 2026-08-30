package si.jakobkreft.ontime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import si.jakobkreft.ontime.R
import si.jakobkreft.ontime.data.Preset
import si.jakobkreft.ontime.domain.DurationText

/**
 * Editing a preset. Each duration field shows what it understood — `= 25:00` — so text that the
 * parser cannot read is visible immediately instead of quietly becoming zero.
 */
@Composable
fun PresetEditorDialog(
    preset: Preset,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Preset) -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var total by remember(preset.id) { mutableStateOf(DurationText.format(preset.totalMillis)) }
    var yellow by remember(preset.id) { mutableStateOf(DurationText.format(preset.yellowMillis)) }
    var red by remember(preset.id) { mutableStateOf(DurationText.format(preset.redMillis)) }

    val totalMillis = DurationText.parse(total)
    val yellowMillis = DurationText.parse(yellow)
    val redMillis = DurationText.parse(red)

    val totalError = when {
        totalMillis == null -> stringResource(R.string.error_unreadable)
        totalMillis <= 0L -> stringResource(R.string.error_total_zero)
        else -> null
    }
    val yellowError = when {
        yellowMillis == null -> stringResource(R.string.error_unreadable)
        totalMillis != null && yellowMillis >= totalMillis ->
            stringResource(R.string.error_yellow_not_less)
        else -> null
    }
    val redError = when {
        redMillis == null -> stringResource(R.string.error_unreadable)
        yellowMillis != null && redMillis > yellowMillis ->
            stringResource(R.string.error_red_above_yellow)
        else -> null
    }
    val nameError = stringResource(R.string.error_name_blank).takeIf { name.isBlank() }

    val edited = if (totalMillis != null && yellowMillis != null && redMillis != null) {
        preset.copy(
            name = name.trim(),
            totalMillis = totalMillis,
            yellowMillis = yellowMillis,
            redMillis = redMillis,
        )
    } else {
        null
    }
    val canSave = edited != null && edited.isValid && nameError == null &&
        totalError == null && yellowError == null && redError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_timer)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                DurationField(
                    label = stringResource(R.string.total_time),
                    value = total,
                    onValueChange = { total = it },
                    parsed = totalMillis,
                    error = totalError,
                    imeAction = ImeAction.Next,
                )
                DurationField(
                    label = stringResource(R.string.yellow_warning),
                    value = yellow,
                    onValueChange = { yellow = it },
                    parsed = yellowMillis,
                    error = yellowError,
                    imeAction = ImeAction.Next,
                )
                DurationField(
                    label = stringResource(R.string.red_warning),
                    value = red,
                    onValueChange = { red = it },
                    parsed = redMillis,
                    error = redError,
                    imeAction = ImeAction.Done,
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.editing_resets_run),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { edited?.let(onConfirm) }, enabled = canSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    parsed: Long?,
    error: String?,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.duration_hint)) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            val message = error ?: parsed?.let {
                stringResource(R.string.parsed_as, DurationText.format(it))
            }
            if (message != null) Text(message)
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}
