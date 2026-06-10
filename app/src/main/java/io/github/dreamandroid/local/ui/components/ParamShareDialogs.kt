package io.github.dreamandroid.local.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.data.GenerationMode
import io.github.dreamandroid.local.ui.screens.GenerationParameters
import io.github.dreamandroid.local.utils.ImportedParams
import io.github.dreamandroid.local.utils.ParamShareField
import io.github.dreamandroid.local.utils.schedulerDisplayName

@Composable
private fun fieldLabel(field: ParamShareField): String = when (field) {
    ParamShareField.PROMPT -> stringResource(R.string.image_prompt)
    ParamShareField.NEGATIVE_PROMPT -> stringResource(R.string.negative_prompt)
    ParamShareField.STEPS -> stringResource(R.string.share_field_steps)
    ParamShareField.CFG -> stringResource(R.string.share_field_cfg)
    ParamShareField.SEED -> stringResource(R.string.share_field_seed)
    ParamShareField.SCHEDULER -> stringResource(R.string.scheduler)
    ParamShareField.DENOISE_STRENGTH -> stringResource(R.string.share_field_denoise)
    ParamShareField.MODE -> stringResource(R.string.share_field_mode)
}

@Composable
private fun FieldRow(field: ParamShareField, checked: Boolean, preview: String?, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                fieldLabel(field),
                style = MaterialTheme.typography.titleSmall,
            )
            preview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun ShareParametersDialog(
    availableFields: List<ParamShareField>,
    fieldPreview: (ParamShareField) -> String?,
    useBase64Initial: Boolean,
    onUseBase64Changed: (Boolean) -> Unit,
    onConfirm: (selected: Set<ParamShareField>, useBase64: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember {
        mutableStateOf<Set<ParamShareField>>(availableFields.toSet())
    }
    var useBase64 by remember(useBase64Initial) { mutableStateOf(useBase64Initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_params_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.share_params_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                availableFields.forEach { field ->
                    FieldRow(
                        field = field,
                        checked = field in selected.value,
                        preview = fieldPreview(field),
                        onToggle = {
                            selected.value = if (field in selected.value) {
                                selected.value - field
                            } else {
                                selected.value + field
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.share_use_base64),
                    hint = stringResource(R.string.share_use_base64_hint),
                    checked = useBase64,
                    onCheckedChange = {
                        useBase64 = it
                        onUseBase64Changed(it)
                    },
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allSelected = selected.value.size == availableFields.size
                TextButton(
                    onClick = {
                        selected.value =
                            if (allSelected) emptySet() else availableFields.toSet()
                    },
                ) {
                    Text(
                        stringResource(
                            if (allSelected) R.string.deselect_all else R.string.select_all,
                        ),
                    )
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(selected.value, useBase64) },
                        enabled = selected.value.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.share_copy_to_clipboard))
                    }
                }
            }
        },
    )
}

@Composable
fun ReproduceParametersDialog(
    params: GenerationParameters,
    onApply: (selected: Set<ParamShareField>) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = remember(params) {
        buildList {
            add(ParamShareField.PROMPT)
            add(ParamShareField.NEGATIVE_PROMPT)
            add(ParamShareField.STEPS)
            add(ParamShareField.CFG)
            if (params.seed != null) add(ParamShareField.SEED)
            add(ParamShareField.SCHEDULER)
            if (params.mode != GenerationMode.TXT2IMG) {
                add(ParamShareField.DENOISE_STRENGTH)
            }
        }
    }
    val selected = remember(params) {
        mutableStateOf<Set<ParamShareField>>(available.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reproduce_params_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.reproduce_params_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                available.forEach { field ->
                    val preview = when (field) {
                        ParamShareField.PROMPT -> params.prompt

                        ParamShareField.NEGATIVE_PROMPT -> params.negativePrompt

                        ParamShareField.STEPS -> params.steps.toString()

                        ParamShareField.CFG -> "%.1f".format(params.cfg)

                        ParamShareField.SEED -> params.seed?.toString()

                        ParamShareField.SCHEDULER -> schedulerDisplayName(params.scheduler)

                        ParamShareField.DENOISE_STRENGTH ->
                            "%.2f".format(params.denoiseStrength)

                        ParamShareField.MODE -> null
                    }
                    FieldRow(
                        field = field,
                        checked = field in selected.value,
                        preview = preview,
                        onToggle = {
                            selected.value = if (field in selected.value) {
                                selected.value - field
                            } else {
                                selected.value + field
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allSelected = selected.value.size == available.size
                TextButton(
                    onClick = {
                        selected.value =
                            if (allSelected) emptySet() else available.toSet()
                    },
                ) {
                    Text(
                        stringResource(
                            if (allSelected) R.string.deselect_all else R.string.select_all,
                        ),
                    )
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = { onApply(selected.value) },
                        enabled = selected.value.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.import_apply))
                    }
                }
            }
        },
    )
}

@Composable
fun ImportParametersDialog(
    imported: ImportedParams,
    clearClipboardInitial: Boolean,
    onClearClipboardChanged: (Boolean) -> Unit,
    onApply: (selected: Set<ParamShareField>, clearClipboard: Boolean) -> Unit,
    onDismiss: (clearClipboard: Boolean) -> Unit,
) {
    val available = remember(imported) { imported.availableFields().toList() }
    val selected = remember(imported) {
        mutableStateOf<Set<ParamShareField>>(available.toSet())
    }
    var clearClipboard by remember(clearClipboardInitial) {
        mutableStateOf(clearClipboardInitial)
    }

    AlertDialog(
        onDismissRequest = { onDismiss(clearClipboard) },
        title = { Text(stringResource(R.string.import_params_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.import_params_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                available.forEach { field ->
                    val preview = when (field) {
                        ParamShareField.PROMPT -> imported.prompt

                        ParamShareField.NEGATIVE_PROMPT -> imported.negativePrompt

                        ParamShareField.STEPS -> imported.steps?.toString()

                        ParamShareField.CFG -> imported.cfg?.let { "%.1f".format(it) }

                        ParamShareField.SEED -> imported.seed?.toString()

                        ParamShareField.SCHEDULER -> schedulerDisplayName(imported.scheduler)

                        ParamShareField.DENOISE_STRENGTH ->
                            imported.denoiseStrength?.let { "%.2f".format(it) }

                        ParamShareField.MODE -> imported.mode?.name?.lowercase()
                    }
                    FieldRow(
                        field = field,
                        checked = field in selected.value,
                        preview = preview,
                        onToggle = {
                            selected.value = if (field in selected.value) {
                                selected.value - field
                            } else {
                                selected.value + field
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.import_clear_clipboard),
                    hint = stringResource(R.string.import_clear_clipboard_hint),
                    checked = clearClipboard,
                    onCheckedChange = {
                        clearClipboard = it
                        onClearClipboardChanged(it)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selected.value, clearClipboard) },
                enabled = selected.value.isNotEmpty(),
            ) {
                Text(stringResource(R.string.import_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(clearClipboard) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
