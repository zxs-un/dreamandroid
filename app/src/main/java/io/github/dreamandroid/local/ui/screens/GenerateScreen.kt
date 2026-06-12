package io.github.dreamandroid.local.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
// import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
// import androidx.compose.ui.focus.FocusRequester
// import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class GenerateTokenizeResult(val count: Int, val maxLength: Int, val overflowOffset: Int)

private val generateScreenTokenizeClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
}

private suspend fun tokenizePromptForGenerate(text: String): GenerateTokenizeResult? = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply { put("prompt", text) }
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("http://localhost:8081/tokenize")
            .post(body)
            .build()
        generateScreenTokenizeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val payload = response.body?.string() ?: return@withContext null
            val json = JSONObject(payload)
            GenerateTokenizeResult(
                count = json.optInt("count", 0),
                maxLength = json.optInt("max_length", 77),
                overflowOffset = json.optInt("overflow_offset", -1),
            )
        }
    } catch (_: Exception) {
        null
    }
}

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
    // Batch generation control (from parent)
    generateTrigger: Int = 0,
    onBatchIndexChange: (Int) -> Unit = {},
    onBatchJobChange: (Job?) -> Unit = {},
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

    // ---- Token count / CLIP limit (77 tokens) ----
    var promptTokenCount by remember { mutableIntStateOf(2) }
    var negativePromptTokenCount by remember { mutableIntStateOf(2) }
    var promptTokenMax by remember { mutableIntStateOf(77) }
    var negativePromptTokenMax by remember { mutableIntStateOf(77) }
    var promptOverflowOffset by remember { mutableIntStateOf(-1) }
    var negativePromptOverflowOffset by remember { mutableIntStateOf(-1) }

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
                    // prompt / negativePrompt / batchCounts: screen-level (global) values
                    // take priority; fallback is handled by the parent (MainActivity).
                    if (prefs.steps > 0) onStepsChange(prefs.steps)
                    if (prefs.cfg > 0) onCfgChange(prefs.cfg)
                    if (prefs.seed.isNotEmpty()) onSeedChange(prefs.seed)
                    onSchedulerChange(prefs.scheduler)
                    onDenoiseStrengthChange(prefs.denoiseStrength)
                    onUseOpenCLChange(prefs.useOpenCL)
                    if (prefs.width > 0) onWidthChange(prefs.width)
                    if (prefs.height > 0) onHeightChange(prefs.height)
                }
            }
        }
    }

    // ---- Token count / CLIP limit debounced requests ----
    LaunchedEffect(prompt) {
        delay(400)
        val result = tokenizePromptForGenerate(prompt) ?: return@LaunchedEffect
        promptTokenCount = result.count
        promptTokenMax = result.maxLength
        promptOverflowOffset = result.overflowOffset
    }
    LaunchedEffect(negativePrompt) {
        delay(400)
        val result = tokenizePromptForGenerate(negativePrompt) ?: return@LaunchedEffect
        negativePromptTokenCount = result.count
        negativePromptTokenMax = result.maxLength
        negativePromptOverflowOffset = result.overflowOffset
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

    // ---- Generation state (single consumer) ----
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var intermediateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generationStartTime by remember { mutableStateOf<Long?>(null) }
    var returnedSeed by remember { mutableStateOf<Long?>(null) }

    // Single consumer of generationState: handles UI updates + bitmap saving.
    // Does NOT reset the service state — the batch loop below owns that.
    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is BackgroundGenerationService.GenerationState.Started -> {
                // Service has accepted the request; clear previous error
                errorMessage = null
            }
            is BackgroundGenerationService.GenerationState.Progress -> {
                if (generationStartTime == null) generationStartTime = System.currentTimeMillis()
                isRunning = true
                progress = state.progress
                state.intermediateImage?.let { intermediateBitmap = it }
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
                            mode = GenerationMode.TXT2IMG,
                        )
                    }
                }
                generationStartTime = null
                // Notify the service that the bitmap has been safely consumed
                // (saved to history). The batch loop will handle resetState().
                BackgroundGenerationService.markBitmapConsumed()
            }
            is BackgroundGenerationService.GenerationState.Error -> {
                isRunning = false
                errorMessage = state.message
                intermediateBitmap = null
                generationStartTime = null
                // Let the batch loop handle resetState() to avoid races.
            }
            is BackgroundGenerationService.GenerationState.Idle -> {}
        }
    }

    // ---- Batch generation loop (Zero-Trust) ----
    // Triggered by GenerateTopBar's "Generate" button via generateTrigger counter.
    // This is the ONLY place that starts the service and manages the batch lifecycle.
    // Every interaction is guarded: timeouts, retries, stale-state detection, forced resets.
    LaunchedEffect(generateTrigger) {
        if (generateTrigger == 0) return@LaunchedEffect
        val job = scope.launch {
            val actualBatchCount = if (seed.isNotBlank()) 1 else batchCounts.coerceAtLeast(1)
            for (i in 0 until actualBatchCount) {
                // ---- Zero-Trust: Stale state detection before each iteration ----
                BackgroundGenerationService.forceResetIfStale()

                // ---- Zero-Trust: Backend health check with retry and auto-restart ----
                val healthCheckRetryInterval = BackgroundGenerationService.getHealthCheckRetryIntervalMs(context)
                val healthCheckMaxFails = BackgroundGenerationService.getHealthCheckMaxFailures(context)
                var backendHealthy = false
                var consecutiveFailures = 0
                while (!backendHealthy) {
                    backendHealthy = withContext(Dispatchers.IO) {
                        BackgroundGenerationService.checkBackendHealth()
                    }
                    if (!backendHealthy) {
                        consecutiveFailures++
                        Log.w("GenerateScreen",
                            "Backend health check failed ($consecutiveFailures/$healthCheckMaxFails)")

                        if (consecutiveFailures >= healthCheckMaxFails) {
                            Log.e("GenerateScreen",
                                "Backend health check failed $consecutiveFailures times consecutively, restarting backend")
                            try {
                                withContext(Dispatchers.IO) {
                                    context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
                                    delay(500)
                                    context.stopService(Intent(context, BackendService::class.java).apply {
                                        action = BackendService.ACTION_STOP
                                    })
                                    delay(2000)
                                    modelId?.let { mid ->
                                        val restartIntent = Intent(context, BackendService::class.java).apply {
                                            putExtra("modelId", mid)
                                            putExtra("width", width)
                                            putExtra("height", height)
                                            putExtra("use_opencl", useOpenCL)
                                        }
                                        context.startForegroundService(restartIntent)
                                    }
                                }
                                consecutiveFailures = 0
                            } catch (e: Exception) {
                                Log.e("GenerateScreen",
                                    "Failed to restart backend: ${e.message}", e)
                                errorMessage = context.getString(R.string.backend_not_healthy)
                                break
                            }
                        }
                        delay(healthCheckRetryInterval)
                    }
                }
                if (!backendHealthy) {
                    break
                }

                onBatchIndexChange(i + 1)

                // ---- Retry loop for this batch item ----
                var batchItemSucceeded = false
                for (retryAttempt in 1..BackgroundGenerationService.MAX_RETRIES) {
                    if (retryAttempt > 1) {
                        Log.w("GenerateScreen", "Batch item $i retry $retryAttempt")
                        delay(BackgroundGenerationService.RETRY_DELAY_MS)
                        BackgroundGenerationService.forceResetIfStale()
                    }

                    val intent = Intent(context, BackgroundGenerationService::class.java).apply {
                        putExtra("prompt", prompt)
                        putExtra("negative_prompt", negativePrompt)
                        putExtra("steps", steps.roundToInt())
                        putExtra("cfg", cfg)
                        seed.toLongOrNull()?.let { putExtra("seed", it) }
                        putExtra("width", width)
                        putExtra("height", height)
                        putExtra("effective_width", width)
                        putExtra("effective_height", height)
                        putExtra("denoise_strength", denoiseStrength)
                        putExtra("use_opencl", useOpenCL)
                        putExtra("scheduler", scheduler)
                        putExtra("aspect_ratio", inferAspectRatioString(width, height))
                    }

                    // Guard: if the batch was cancelled (user clicked stop),
                    // do NOT start a new service request.
                    if (!isActive) {
                        Log.w("GenerateScreen", "Batch cancelled, not starting new request")
                        break
                    }
                    context.startForegroundService(intent)

                    // ---- Zero-Trust: Wait with timeout (not indefinite .first {}) ----
                    val result = withTimeoutOrNull(BackgroundGenerationService.getServiceWaitTimeoutMs(context)) {
                        BackgroundGenerationService.generationState
                            .first { it is BackgroundGenerationService.GenerationState.Complete ||
                                     it is BackgroundGenerationService.GenerationState.Error }
                    }

                    when {
                        // Timeout — service may have hung, force reset and retry
                        result == null -> {
                            Log.w("GenerateScreen", "Batch item $i timed out after ${BackgroundGenerationService.getServiceWaitTimeoutMs(context)}ms")
                            errorMessage = context.getString(R.string.generation_timeout_retry, retryAttempt)
                            BackgroundGenerationService.resetState()
                            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
                            continue // retry
                        }
                        // Success
                        result is BackgroundGenerationService.GenerationState.Complete -> {
                            batchItemSucceeded = true
                            errorMessage = null
                        }
                        // Backend error — may be recoverable, retry
                        result is BackgroundGenerationService.GenerationState.Error -> {
                            Log.w("GenerateScreen", "Batch item $i error: ${result.message}")
                            errorMessage = result.message
                            BackgroundGenerationService.resetState()
                            continue // retry
                        }
                    }

                    // ---- Zero-Trust: Confirm bitmap consumed signal took effect ----
                    val consumed = BackgroundGenerationService.markBitmapConsumed()
                    if (!consumed) {
                        Log.w("GenerateScreen", "markBitmapConsumed() did not take effect, retrying")
                        delay(200)
                        BackgroundGenerationService.markBitmapConsumed()
                    }

                    // ---- Zero-Trust: Wait for service to stop with timeout ----
                    val serviceWaitTimeout = BackgroundGenerationService.getServiceWaitTimeoutMs(context)
                    val waitStartTime = System.currentTimeMillis()
                    while (BackgroundGenerationService.isServiceRunning.value) {
                        if (System.currentTimeMillis() - waitStartTime > serviceWaitTimeout) {
                            Log.w("GenerateScreen", "Service did not stop within timeout, forcing stop")
                            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
                            delay(500)
                            break
                        }
                        delay(100)
                    }

                    // ---- Zero-Trust: Always force-reset after each batch item ----
                    BackgroundGenerationService.forceResetIfStale()

                    if (batchItemSucceeded) break // exit retry loop on success
                }

                // If all retries exhausted for this batch item, stop the entire batch
                if (!batchItemSucceeded) {
                    Log.e("GenerateScreen", "Batch item $i failed after all retries, stopping batch")
                    break
                }
            }
            onBatchIndexChange(0)
            onBatchJobChange(null)
        }
        onBatchJobChange(job)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = width.toString(),
                    onValueChange = { text ->
                        val num = text.filter { it.isDigit() }.toIntOrNull()
                        if (num != null && num in 64..4096) {
                            onWidthChange(num)
                            saveAllFields()
                        }
                    },
                    label = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = height.toString(),
                    onValueChange = { text ->
                        val num = text.filter { it.isDigit() }.toIntOrNull()
                        if (num != null && num in 64..4096) {
                            onHeightChange(num)
                            saveAllFields()
                        }
                    },
                    label = { Text("H") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
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
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.image_saved),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT)
                                                    .show()
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
