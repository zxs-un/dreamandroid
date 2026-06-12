package io.github.dreamandroid.local.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.data.*
import io.github.dreamandroid.local.service.BackendService
import io.github.dreamandroid.local.service.BackgroundGenerationService
import io.github.dreamandroid.local.ui.components.SmoothLinearWavyProgressIndicator
import io.github.dreamandroid.local.utils.saveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GenerateScreen – image generation with flattened advanced settings.
 * All generation parameters are managed by the parent (MainActivity) and passed down.
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val modelRepository = remember { ModelRepository(context) }
    val historyManager = remember { HistoryManager(context) }
    val model = remember(modelId) { modelRepository.models.find { it.id == modelId } }
    val backendState by BackendService.backendState.collectAsState()
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val generationPreferences = remember { GenerationPreferences(context) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Load preferences for this model
    LaunchedEffect(modelId) {
        if (modelId != null) {
            withContext(Dispatchers.IO) {
                val prefs = generationPreferences.getPreferences(modelId).first()
                withContext(Dispatchers.Main) {
                    if (prefs.prompt.isNotEmpty()) onPromptChange(prefs.prompt)
                    if (prefs.negativePrompt.isNotEmpty()) onNegativePromptChange(prefs.negativePrompt)
                    if (prefs.steps > 0) onStepsChange(prefs.steps)
                    if (prefs.cfg > 0) onCfgChange(prefs.cfg)
                    if (prefs.seed.isNotEmpty()) onSeedChange(prefs.seed)
                    if (prefs.batchCounts > 0) onBatchCountsChange(prefs.batchCounts)
                    onSchedulerChange(prefs.scheduler)
                    onDenoiseStrengthChange(prefs.denoiseStrength)
                    onUseOpenCLChange(prefs.useOpenCL)
                    if (prefs.width > 0) onWidthChange(prefs.width)
                    if (prefs.height > 0) onHeightChange(prefs.height)
                }
            }
        }
    }

    fun saveAllFields() {
        if (modelId == null) return
        scope.launch(Dispatchers.IO) {
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
                aspectRatio = "1:1",
            )
        }
    }

    // ---- Generation state ----
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var intermediateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generationStartTime by remember { mutableStateOf<Long?>(null) }
    var returnedSeed by remember { mutableStateOf<Long?>(null) }

    // Track generation service state
    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is BackgroundGenerationService.GenerationState.Progress -> {
                progress = state.progress
                state.bitmap?.let { intermediateBitmap = it }
            }
            is BackgroundGenerationService.GenerationState.Complete -> {
                isRunning = false
                progress = 0f
                intermediateBitmap = null
                state.bitmap?.let { currentBitmap = it }
                state.seed?.let { returnedSeed = it }
                val params = GenerationParameters(
                    steps = steps.roundToInt(),
                    cfg = cfg,
                    seed = seed.toLongOrNull(),
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    generationTime = generationStartTime?.let {
                        ((System.currentTimeMillis() - it) / 1000).toString() + "s"
                    },
                    width = width,
                    height = height,
                    runOnCpu = model?.runOnCpu ?: false,
                    denoiseStrength = denoiseStrength,
                    useOpenCL = useOpenCL,
                    scheduler = scheduler,
                )
                scope.launch(Dispatchers.IO) {
                    state.bitmap?.let { bmp ->
                        historyManager.saveGeneratedImage(
                            modelId = modelId ?: "unknown",
                            bitmap = bmp,
                            params = params,
                        )
                    }
                }
                BackgroundGenerationService.clearCompleteState()
            }
            is BackgroundGenerationService.GenerationState.Error -> {
                isRunning = false
                errorMessage = state.message
                intermediateBitmap = null
                BackgroundGenerationService.resetState()
            }
            is BackgroundGenerationService.GenerationState.Idle -> {}
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
        if (model == null) {
            // No model loaded state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        stringResource(R.string.no_model_loaded),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.no_model_loaded_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            return@Column
        }

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

            // ---- Prompt Fields ----
            Text(
                stringResource(R.string.prompt_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.image_prompt)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )

            OutlinedTextField(
                value = negativePrompt,
                onValueChange = onNegativePromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.negative_prompt)) },
                minLines = 1,
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
            )

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
            if (model.runOnCpu) {
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

            // Batch Count
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.batch_count, batchCounts),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = batchCounts.toFloat(),
                    onValueChange = { onBatchCountsChange(it.roundToInt()); saveAllFields() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
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

            if (returnedSeed != null) {
                FilledTonalButton(
                    onClick = { onSeedChange(returnedSeed.toString()); saveAllFields() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.use_last_seed, returnedSeed.toString()),
                    )
                }
            }

            // Reset button
            OutlinedButton(
                onClick = {
                    onStepsChange(20f)
                    onCfgChange(7f)
                    onSeedChange("")
                    onBatchCountsChange(1)
                    onSchedulerChange("dpm")
                    onPromptChange(model.defaultPrompt)
                    onNegativePromptChange(model.defaultNegativePrompt)
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

            // ---- Error message ----
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                errorMessage?.let { msg ->
                    Card(
                        onClick = { errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Error,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // ---- Running indicator ----
            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.generating),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SmoothLinearWavyProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        intermediateBitmap?.let { bitmap ->
                            Card(
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(bitmap)
                                        .build(),
                                    contentDescription = "Generation Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Result ----
            currentBitmap?.let { bitmap ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.result_tab),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(bitmap)
                                .build(),
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(width.toFloat() / height),
                            contentScale = ContentScale.Fit,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        saveImage(
                                            context, bitmap,
                                            onSuccess = {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        stringResource(R.string.image_saved),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            },
                                            onError = { err ->
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.SaveAlt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.save))
                            }
                            OutlinedButton(
                                onClick = { currentBitmap = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
