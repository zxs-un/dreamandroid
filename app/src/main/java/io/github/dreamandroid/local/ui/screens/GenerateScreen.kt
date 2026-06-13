package io.github.dreamandroid.local.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.dreamandroid.local.DreamAndroidApplication
import io.github.dreamandroid.local.service.backend.BackendManager

/**
 * GenerateScreen – image generation parameter configuration.
 * All generation parameters are managed by the parent (MainActivity) and passed down.
 * When the user clicks Generate, parameters are sent to the Queue for background processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    modelId: String?,
    modifier: Modifier = Modifier,
    // Generation parameters passed from parent
    prompt: String,
    onPromptChange: (String) -> Unit,
    negativePrompt: String,
    onNegativePromptChange: (String) -> Unit,
    steps: Float,
    onStepsChange: (Float) -> Unit,
    cfg: Float,
    onCfgChange: (Float) -> Unit,
    seed: String,
    onSeedChange: (String) -> Unit,
    batchCounts: Int,
    onBatchCountsChange: (Int) -> Unit,
    scheduler: String,
    onSchedulerChange: (String) -> Unit,
    denoiseStrength: Float,
    onDenoiseStrengthChange: (Float) -> Unit,
    useOpenCL: Boolean,
    onUseOpenCLChange: (Boolean) -> Unit,
    width: Int,
    onWidthChange: (Int) -> Unit,
    height: Int,
    onHeightChange: (Int) -> Unit,
    // Queue interaction — sends current params to the queue
    onAddToQueue: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val modelRepository = remember { ModelRepository(context) }
    val model = remember(modelId) { modelRepository.models.find { it.id == modelId } }
    val generationPreferences = remember { GenerationPreferences(context) }
    val backendManager = remember { (context.applicationContext as DreamAndroidApplication).backendManager }

    // ---- Token count / CLIP limit (77 tokens) ----
    var promptTokenCount by remember { mutableIntStateOf(2) }
    var negativePromptTokenCount by remember { mutableIntStateOf(2) }
    var promptTokenMax by remember { mutableIntStateOf(77) }
    var negativePromptTokenMax by remember { mutableIntStateOf(77) }
    var promptOverflowOffset by remember { mutableIntStateOf(-1) }
    var negativePromptOverflowOffset by remember { mutableIntStateOf(-1) }

    // ---- Queue add feedback ----
    var queueAddMessage by remember { mutableStateOf<String?>(null) }

    // ---- No model warning ----
    var showNoModelWarning by remember { mutableStateOf(false) }
    if (showNoModelWarning) {
        AlertDialog(
            onDismissRequest = { showNoModelWarning = false },
            title = { Text(stringResource(R.string.no_model_loaded)) },
            text = { Text(stringResource(R.string.no_model_loaded_hint)) },
            confirmButton = {
                TextButton(onClick = { showNoModelWarning = false }) {
                    Text(stringResource(R.string.got_it))
                }
            },
        )
    }

    // Load preferences for this model
    LaunchedEffect(modelId) {
        if (modelId != null) {
            withContext(Dispatchers.IO) {
                val prefs = generationPreferences.getPreferences(modelId).first()
                withContext(Dispatchers.Main) {
                    if (prefs.steps > 0) onStepsChange(prefs.steps)
                    if (prefs.cfg > 0) onCfgChange(prefs.cfg)
                    if (prefs.seed.isNotEmpty()) onSeedChange(prefs.seed)
                    onSchedulerChange(prefs.scheduler)
                    onDenoiseStrengthChange(prefs.denoiseStrength)
                    onUseOpenCLChange(prefs.useOpenCL)
                    if (prefs.width != -1) onWidthChange(prefs.width)
                    if (prefs.height != -1) onHeightChange(prefs.height)
                }
            }
        }
    }

    // ---- Token count / CLIP limit debounced requests ----
    LaunchedEffect(prompt) {
        delay(400)
        try {
            val result = withContext(Dispatchers.IO) { backendManager.tokenize(prompt) }
            promptTokenCount = result.count
            promptTokenMax = result.maxLength
            promptOverflowOffset = result.overflowOffset
        } catch (_: Exception) {
            // Silently ignore tokenize failures — non-critical UX
        }
    }
    LaunchedEffect(negativePrompt) {
        delay(400)
        try {
            val result = withContext(Dispatchers.IO) { backendManager.tokenize(negativePrompt) }
            negativePromptTokenCount = result.count
            negativePromptTokenMax = result.maxLength
            negativePromptOverflowOffset = result.overflowOffset
        } catch (_: Exception) {
            // Silently ignore tokenize failures — non-critical UX
        }
    }

    // Clear queue feedback message after a delay
    LaunchedEffect(queueAddMessage) {
        if (queueAddMessage != null) {
            delay(3000)
            queueAddMessage = null
        }
    }

    fun saveAllFields() {
        scope.launch(Dispatchers.IO) {
            // Screen-level fields — persist regardless of model
            generationPreferences.saveGlobalFields(
                prompt = prompt,
                negativePrompt = negativePrompt,
                batchCounts = batchCounts,
                width = width,
                height = height,
            )
            // Per-model fields
            if (modelId != null) {
                generationPreferences.saveAllFields(
                    modelId = modelId,
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    steps = steps,
                    cfg = cfg,
                    seed = seed,
                    width = width,
                    height = height,
                    denoiseStrength = denoiseStrength,
                    useOpenCL = useOpenCL,
                    batchCounts = batchCounts,
                    scheduler = scheduler,
                    aspectRatio = inferAspectRatioString(width, height),
                )
            }
        }
    }

    // ---- UI ----
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- Batch Count (moved to top, above prompt) ----
            var batchText by remember(batchCounts) { mutableStateOf(batchCounts.toString()) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.batch_count_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledIconButton(
                        onClick = {
                            val newVal = (batchCounts - 1).coerceAtLeast(1)
                            onBatchCountsChange(newVal)
                            batchText = newVal.toString()
                            saveAllFields()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Spacer(Modifier.width(12.dp))
                    var batchFieldFocused by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = batchText,
                        onValueChange = { newText ->
                            // Allow typing digits (including empty)
                            if (newText.isEmpty()) {
                                batchText = newText
                                return@OutlinedTextField
                            }
                            val digits = newText.filter { it.isDigit() }
                            // While focused, show raw digits; clamp only on commit
                            batchText = digits
                            val num = digits.toIntOrNull()
                            if (num != null && !batchFieldFocused) {
                                val clamped = num.coerceIn(1, 60)
                                batchText = clamped.toString()
                                onBatchCountsChange(clamped)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .width(80.dp)
                            .onFocusChanged { state ->
                                batchFieldFocused = state.isFocused
                                if (!state.isFocused) {
                                    // On focus lost, clamp the value
                                    val num = batchText.toIntOrNull() ?: batchCounts
                                    val clamped = num.coerceIn(1, 60)
                                    batchText = clamped.toString()
                                    onBatchCountsChange(clamped)
                                    saveAllFields()
                                }
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            val newVal = (batchCounts + 1).coerceAtMost(60)
                            onBatchCountsChange(newVal)
                            batchText = newVal.toString()
                            saveAllFields()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }
            }

            HorizontalDivider()

            // ---- Prompt Fields ----
            Text(
                stringResource(R.string.prompt_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Grey-out overflow for prompt: characters past the 77-token CLIP limit
            // are rendered at 38% opacity.
            val promptOverflowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            val promptOverflowTransformation = remember(promptOverflowOffset, promptOverflowColor) {
                VisualTransformation { text ->
                    if (promptOverflowOffset in 0 until text.length) {
                        val styled = buildAnnotatedString {
                            append(text.subSequence(0, promptOverflowOffset))
                            withStyle(SpanStyle(color = promptOverflowColor)) {
                                append(text.subSequence(promptOverflowOffset, text.length))
                            }
                        }
                        TransformedText(styled, OffsetMapping.Identity)
                    } else {
                        TransformedText(text, OffsetMapping.Identity)
                    }
                }
            }

            val negativePromptOverflowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            val negativePromptOverflowTransformation = remember(negativePromptOverflowOffset, negativePromptOverflowColor) {
                VisualTransformation { text ->
                    if (negativePromptOverflowOffset in 0 until text.length) {
                        val styled = buildAnnotatedString {
                            append(text.subSequence(0, negativePromptOverflowOffset))
                            withStyle(SpanStyle(color = negativePromptOverflowColor)) {
                                append(text.subSequence(negativePromptOverflowOffset, text.length))
                            }
                        }
                        TransformedText(styled, OffsetMapping.Identity)
                    } else {
                        TransformedText(text, OffsetMapping.Identity)
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.image_prompt))
                        if (prompt.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("$promptTokenCount/$promptTokenMax")
                        }
                        if (promptOverflowOffset >= 0) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Report,
                                contentDescription = stringResource(R.string.prompt_token_overflow),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                visualTransformation = promptOverflowTransformation,
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )

            OutlinedTextField(
                value = negativePrompt,
                onValueChange = onNegativePromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.negative_prompt))
                        if (negativePrompt.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("$negativePromptTokenCount/$negativePromptTokenMax")
                        }
                        if (negativePromptOverflowOffset >= 0) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Report,
                                contentDescription = stringResource(R.string.prompt_token_overflow),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                visualTransformation = negativePromptOverflowTransformation,
                minLines = 1,
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
            )

            // ---- Width / Height (screen-level, below negative prompt) ----/
            Spacer(Modifier.height(8.dp))

            var widthText by remember(width) { mutableStateOf(width.toString()) }
            var heightText by remember(height) { mutableStateOf(height.toString()) }
            var widthFocused by remember { mutableStateOf(false) }
            var heightFocused by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { newText ->
                        // Allow free typing; clamp only on focus loss
                        val digits = newText.filter { it.isDigit() }
                        widthText = digits
                        val num = digits.toIntOrNull()
                        if (num != null && !widthFocused) {
                            val clamped = num.coerceIn(64, 4096)
                            widthText = clamped.toString()
                            onWidthChange(clamped)
                            saveAllFields()
                        }
                    },
                    label = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            widthFocused = state.isFocused
                            if (!state.isFocused) {
                                val num = widthText.toIntOrNull() ?: width
                                val clamped = num.coerceIn(64, 4096)
                                widthText = clamped.toString()
                                onWidthChange(clamped)
                                saveAllFields()
                            }
                        },
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { newText ->
                        // Allow free typing; clamp only on focus loss
                        val digits = newText.filter { it.isDigit() }
                        heightText = digits
                        val num = digits.toIntOrNull()
                        if (num != null && !heightFocused) {
                            val clamped = num.coerceIn(64, 4096)
                            heightText = clamped.toString()
                            onHeightChange(clamped)
                            saveAllFields()
                        }
                    },
                    label = { Text("H") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            heightFocused = state.isFocused
                            if (!state.isFocused) {
                                val num = heightText.toIntOrNull() ?: height
                                val clamped = num.coerceIn(64, 4096)
                                heightText = clamped.toString()
                                onHeightChange(clamped)
                                saveAllFields()
                            }
                        },
                    shape = MaterialTheme.shapes.medium,
                )
            }

            HorizontalDivider()

            // ---- Flattened Advanced Settings ----
            Text(
                stringResource(R.string.advanced_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Steps
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.steps, steps.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = steps,
                    onValueChange = { onStepsChange(it); saveAllFields() },
                    valueRange = 1f..50f,
                    steps = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // CFG Scale
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "CFG Scale: %.1f".format(cfg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = cfg,
                    onValueChange = { onCfgChange(it); saveAllFields() },
                    valueRange = 1f..30f,
                    steps = 57,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Scheduler
            val baseId = scheduler.removeSuffix("_karras")
            val karras = scheduler.endsWith("_karras")
            val karrasSupported = baseId != "lcm"
            val baseOptions = listOf(
                "dpm" to "DPM++ 2M",
                "dpm_sde" to "DPM++ 2M SDE",
                "euler_a" to "Euler A",
                "euler" to "Euler",
                "lcm" to "LCM",
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.scheduler),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Karras",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(if (karrasSupported) 1f else 0.4f),
                    )
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        Switch(
                            checked = karras && karrasSupported,
                            enabled = karrasSupported,
                            onCheckedChange = { enable ->
                                onSchedulerChange(if (enable) "${baseId}_karras" else baseId)
                                saveAllFields()
                            },
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    baseOptions.forEach { (id, label) ->
                        FilterChip(
                            selected = baseId == id,
                            onClick = {
                                if (baseId != id) {
                                    onSchedulerChange(
                                        if (karras && id != "lcm") "${id}_karras" else id,
                                    )
                                    saveAllFields()
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Width/Height for CPU models
            if (model?.runOnCpu == true) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.image_size, width, height),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Width: $width", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = width.toFloat(),
                                onValueChange = {
                                    onWidthChange(it.roundToInt())
                                    saveAllFields()
                                },
                                valueRange = 128f..512f,
                                steps = 5,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Height: $height", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = height.toFloat(),
                                onValueChange = {
                                    onHeightChange(it.roundToInt())
                                    saveAllFields()
                                },
                                valueRange = 128f..512f,
                                steps = 5,
                            )
                        }
                    }
                }

                // Runtime (CPU/GPU) for CPU models
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Runtime", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = !useOpenCL,
                            onClick = { onUseOpenCLChange(false); saveAllFields() },
                            label = { Text("CPU") },
                        )
                        FilterChip(
                            selected = useOpenCL,
                            onClick = { onUseOpenCLChange(true); saveAllFields() },
                            label = { Text("GPU") },
                        )
                    }
                }
            }

            // Seed
            OutlinedTextField(
                value = seed,
                onValueChange = { onSeedChange(it); saveAllFields() },
                label = { Text(stringResource(R.string.random_seed)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                trailingIcon = {
                    if (seed.isNotEmpty()) {
                        IconButton(onClick = { onSeedChange(""); saveAllFields() }) {
                            Icon(Icons.Default.Clear, "clear")
                        }
                    }
                },
            )

            // ---- Add to Queue button ----
            Button(
                onClick = {
                    if (modelId == null) {
                        showNoModelWarning = true
                        return@Button
                    }
                    val count = if (seed.isNotBlank()) 1 else batchCounts.coerceAtLeast(1)
                    onAddToQueue(count)
                    saveAllFields()
                    queueAddMessage = "Added $count to queue"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.generate_image))
            }

            // Reset button
            OutlinedButton(
                onClick = {
                    onStepsChange(20f)
                    onCfgChange(7f)
                    onSeedChange("")
                    onBatchCountsChange(1)
                    onSchedulerChange("dpm")
                    onPromptChange(model?.defaultPrompt ?: "")
                    onNegativePromptChange(model?.defaultNegativePrompt ?: "")
                    onDenoiseStrengthChange(0.6f)
                    saveAllFields()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reset))
            }

            HorizontalDivider()

            // ---- Queue feedback message ----
            AnimatedVisibility(
                visible = queueAddMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                queueAddMessage?.let { msg ->
                    Card(
                        onClick = { queueAddMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
