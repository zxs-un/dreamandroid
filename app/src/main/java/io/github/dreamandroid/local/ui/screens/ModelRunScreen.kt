package io.github.dreamandroid.local.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.dreamandroid.local.BuildConfig
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.data.DownloadProgress
import io.github.dreamandroid.local.data.GenerationMode
import io.github.dreamandroid.local.data.GenerationPreferences
import io.github.dreamandroid.local.data.HistoryFilter
import io.github.dreamandroid.local.data.HistoryItem
import io.github.dreamandroid.local.data.HistoryManager
import io.github.dreamandroid.local.data.ModelRepository
import io.github.dreamandroid.local.data.PatchScanner
import io.github.dreamandroid.local.data.Resolution
import io.github.dreamandroid.local.data.TagAutocompleteRepository
import io.github.dreamandroid.local.data.TagMatchType
import io.github.dreamandroid.local.data.TagSuggestion
import io.github.dreamandroid.local.data.UpscalerModel
import io.github.dreamandroid.local.data.UpscalerRepository
import io.github.dreamandroid.local.service.BackendService
import io.github.dreamandroid.local.service.BackgroundGenerationService
import io.github.dreamandroid.local.service.BackgroundGenerationService.GenerationState
import io.github.dreamandroid.local.service.ModelDownloadService
import io.github.dreamandroid.local.ui.components.BlockingProgressOverlay
import io.github.dreamandroid.local.ui.components.GenerationParamsDialog
import io.github.dreamandroid.local.ui.components.ImportParametersDialog
import io.github.dreamandroid.local.ui.components.OverlayIconButton
import io.github.dreamandroid.local.ui.components.PromptTagTextField
import io.github.dreamandroid.local.ui.components.ReproduceParametersDialog
import io.github.dreamandroid.local.ui.components.ShareParametersDialog
import io.github.dreamandroid.local.ui.components.SmoothLinearWavyProgressIndicator
import io.github.dreamandroid.local.ui.components.ZoomableImageOverlay
import io.github.dreamandroid.local.ui.theme.Motion
import io.github.dreamandroid.local.utils.ImportedParams
import io.github.dreamandroid.local.utils.LogCapture
import io.github.dreamandroid.local.utils.ParamShare
import io.github.dreamandroid.local.utils.ParamShareField
import io.github.dreamandroid.local.utils.performUpscale
import io.github.dreamandroid.local.utils.reportImage
import io.github.dreamandroid.local.utils.saveImage
import io.github.dreamandroid.local.utils.saveImageFromFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Prompt undo/redo: cap on stored steps, and the window within which continuous
// typing collapses into a single step.
private const val HISTORY_LIMIT = 100
private const val HISTORY_COALESCE_MS = 600L

private fun checkStoragePermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    true // Android 10
} else {
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
}

private val tokenizeClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
}

private data class TokenizeResult(val count: Int, val maxLength: Int, val overflowOffset: Int)

private suspend fun tokenizePromptRequest(text: String): TokenizeResult? = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply { put("prompt", text) }
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("http://localhost:8081/tokenize")
            .post(body)
            .build()
        tokenizeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val payload = response.body?.string() ?: return@withContext null
            val json = JSONObject(payload)
            TokenizeResult(
                count = json.optInt("count", 0),
                maxLength = json.optInt("max_length", 77),
                overflowOffset = json.optInt("overflow_offset", -1),
            )
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun checkBackendHealth(
    backendState: StateFlow<BackendService.BackendState>,
    onHealthy: () -> Unit,
    onUnhealthy: () -> Unit,
) = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS) // 100ms
            .build()

        val startTime = System.currentTimeMillis()
//        val timeoutDuration = 10000
        val timeoutDuration = 60000

        while (currentCoroutineContext().isActive) {
            if (backendState.value is BackendService.BackendState.Error) {
                withContext(Dispatchers.Main) {
                    onUnhealthy()
                }
                break
            }

            if (System.currentTimeMillis() - startTime > timeoutDuration) {
                withContext(Dispatchers.Main) {
                    onUnhealthy()
                }
                break
            }

            try {
                val request = Request.Builder()
                    .url("http://localhost:8081/health")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onHealthy()
                    }
                    break
                }
            } catch (e: Exception) {
                // e
            }

            delay(100)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onUnhealthy()
        }
    }
}

/**
 * For SDXL with a non-1:1 aspectRatio, returns the centered (target_w, target_h)
 * region inside the 1024x1024 generation canvas. The longest side is forced to
 * canvasMax (1024), the shortest side is scaled by the ratio and aligned down to
 * a multiple of 8. Returns null in all other cases (non-SDXL, 1:1, malformed),
 * meaning "no padding, use canvas size directly."
 */
fun computeAspectTargetSize(isSdxl: Boolean, aspectRatio: String, canvasMax: Int = 1024): Pair<Int, Int>? {
    if (!isSdxl) return null
    val parts = aspectRatio.split(":")
    if (parts.size != 2) return null
    val rw = parts[0].toIntOrNull() ?: return null
    val rh = parts[1].toIntOrNull() ?: return null
    if (rw <= 0 || rh <= 0 || rw == rh) return null
    return if (rw >= rh) {
        val th = ((canvasMax.toDouble() * rh / rw).toInt() / 8 * 8).coerceAtLeast(8)
        Pair(canvasMax, th)
    } else {
        val tw = ((canvasMax.toDouble() * rw / rh).toInt() / 8 * 8).coerceAtLeast(8)
        Pair(tw, canvasMax)
    }
}

/**
 * GCD-reduces (width, height) into a "W:H" aspect-ratio string.
 * Used by reproduce/import paths to recover an aspect from a recorded result size.
 */
fun inferAspectRatioString(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "1:1"
    var a = width
    var b = height
    while (b != 0) {
        val t = b
        b = a % b
        a = t
    }
    return "${width / a}:${height / a}"
}

/**
 * Pads `src` (already at targetW x targetH) into a canvas of size canvasW x canvasH
 * with a centered placement and black borders. If src already matches canvas size,
 * returns the source unchanged.
 */
fun padBitmapToCanvas(src: Bitmap, canvasW: Int, canvasH: Int): Bitmap {
    if (src.width == canvasW && src.height == canvasH) return src
    val out = createBitmap(canvasW, canvasH)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val left = ((canvasW - src.width) / 2).toFloat()
    val top = ((canvasH - src.height) / 2).toFloat()
    canvas.drawBitmap(src, left, top, null)
    return out
}

@Immutable
data class GenerationParameters(
    val steps: Int,
    val cfg: Float,
    val seed: Long?,
    val prompt: String,
    val negativePrompt: String,
    val generationTime: String?,
    val width: Int,
    val height: Int,
    val runOnCpu: Boolean,
    val denoiseStrength: Float = 0.6f,
    val useOpenCL: Boolean = false,
    val scheduler: String = "dpm",
    val mode: GenerationMode = GenerationMode.UNKNOWN,
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelRunScreen(modelId: String, navController: NavController, modifier: Modifier = Modifier) {
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val backendState by BackendService.backendState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val generationPreferences = remember { GenerationPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelRepository = remember { ModelRepository(context) }

    // String resources hoisted to composable scope (lint: LocalContextGetResourceValueCall).
    val msgMediaPermissionHint = stringResource(R.string.media_permission_hint)
    val msgBackendFailed = stringResource(R.string.backend_failed)
    val msgImportNoParams = stringResource(R.string.import_no_params)
    val msgImageSaved = stringResource(R.string.image_saved)
    val msgDownloadDone = stringResource(R.string.download_done)
    val msgErrorDownloadFailed = stringResource(R.string.error_download_failed)
    val msgDownloadModelFirst = stringResource(R.string.download_model_first)
    val msgDeleted = stringResource(R.string.deleted)
    val msgDeleteFailedMessage = stringResource(R.string.delete_failed_message)
    val msgShareCopied = stringResource(R.string.share_copied)
    val msgImportApplied = stringResource(R.string.import_applied)
    val msgUpscaleFailed = stringResource(R.string.upscale_failed)
    val msgSavedCountWithFailed = stringResource(R.string.saved_count_with_failed)
    val msgDeletedCountWithFailed = stringResource(R.string.deleted_count_with_failed)
    val model = remember { modelRepository.models.find { it.id == modelId } }
    val historyManager = remember { HistoryManager(context) }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showOpenCLWarningDialog by remember { mutableStateOf(false) }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var intermediateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageVersion by remember { mutableIntStateOf(0) }
    var generationParams by remember { mutableStateOf<GenerationParameters?>(null) }
    var generationParamsModelId by remember { mutableStateOf(modelId) }

    // History state
    var historyFilter by remember(modelId) {
        mutableStateOf(HistoryFilter(modelIds = setOf(modelId)))
    }
    val historyFlow = remember(historyFilter) { historyManager.observe(historyFilter) }
    val historyItems by historyFlow.collectAsState(initial = emptyList())
    val knownModelIds by remember { historyManager.observeKnownModelIds() }
        .collectAsState(initial = emptyList())
    val knownSchedulers by remember { historyManager.observeKnownSchedulers() }
        .collectAsState(initial = emptyList())
    val knownSizes by remember { historyManager.observeKnownSizes() }
        .collectAsState(initial = emptyList())
    var showHistoryFilterSheet by remember { mutableStateOf(false) }
    var selectedHistoryItem by remember { mutableStateOf<HistoryItem?>(null) }
    var showHistoryDetailDialog by remember { mutableStateOf(false) }
    var showHistoryParametersDialog by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var showReproduceParamsDialog by remember { mutableStateOf(false) }
    var pendingReproduceParams by remember { mutableStateOf<GenerationParameters?>(null) }

    // Parameter share state
    var shareSourceParams by remember { mutableStateOf<GenerationParameters?>(null) }
    var shareSourceModelId by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ImportedParams?>(null) }
    var clipboardImportChecked by remember { mutableStateOf(false) }
    val shareUseBase64 by remember { generationPreferences.observeShareUseBase64() }
        .collectAsState(initial = true)
    val shareClearClipboardOnImport by remember {
        generationPreferences.observeShareClearClipboardOnImport()
    }.collectAsState(initial = true)

    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryItem>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchSaveDialog by remember { mutableStateOf(false) }
    var isBatchSaving by remember { mutableStateOf(false) }
    var batchSaveTotal by remember { mutableIntStateOf(0) }
    var batchSaveCurrent by remember { mutableIntStateOf(0) }
    var batchSaveFailed by remember { mutableIntStateOf(0) }

    var generationParamsTmp by remember {
        mutableStateOf(
            GenerationParameters(
                steps = 0,
                cfg = 0f,
                seed = 0,
                prompt = "",
                negativePrompt = "",
                generationTime = "",
                width = if (model?.isSdxl == true) {
                    1024
                } else if (model?.runOnCpu == true) {
                    256
                } else {
                    512
                },
                height = if (model?.isSdxl == true) {
                    1024
                } else if (model?.runOnCpu == true) {
                    256
                } else {
                    512
                },
                runOnCpu = model?.runOnCpu ?: false,
            ),
        )
    }
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var promptFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var negativePromptFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var promptSuggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var negativePromptSuggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var promptActiveQuery by remember { mutableStateOf<String?>(null) }
    var negativePromptActiveQuery by remember { mutableStateOf<String?>(null) }
    var isPromptFocused by remember { mutableStateOf(false) }
    var isNegativePromptFocused by remember { mutableStateOf(false) }
    // Undo/redo history of the prompt text. Rapid typing coalesces into a single
    // step; suggestion picks and toolbar edits are discrete steps.
    var promptUndoStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var promptRedoStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var promptHistoryAt by remember { mutableLongStateOf(0L) }
    var negativePromptUndoStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var negativePromptRedoStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var negativePromptHistoryAt by remember { mutableLongStateOf(0L) }
    // Set when the back gesture dismisses the popup; reset on the next edit so the
    // popup stays closed until the user actually does something again.
    var promptPopupDismissed by remember { mutableStateOf(false) }
    var negativePromptPopupDismissed by remember { mutableStateOf(false) }
    var cfg by remember { mutableFloatStateOf(7f) }
    var steps by remember { mutableFloatStateOf(20f) }
    var seed by remember { mutableStateOf("") }
    var denoiseStrength by remember { mutableFloatStateOf(0.6f) }
    var useOpenCL by remember { mutableStateOf(false) }
    var batchCounts by remember { mutableIntStateOf(1) }
    var scheduler by remember { mutableStateOf("dpm") }
    var aspectRatio by remember { mutableStateOf("1:1") }
    var showCustomAspectRatioDialog by remember { mutableStateOf(false) }
    var currentBatchIndex by remember { mutableIntStateOf(0) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var base64EncodeDone by remember { mutableStateOf(false) }
    var returnedSeed by remember { mutableStateOf<Long?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingBackend by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showParametersDialog by remember { mutableStateOf(false) }
    var pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var generationStartTime by remember { mutableStateOf<Long?>(null) }
    var hasInitialized by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    // The prompt fields live on page 0. When the user swipes to the result or
    // history page the suggestion popup is anchored absolutely and would linger,
    // so drop focus (which clears the suggestions) as soon as we leave page 0.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) {
            focusManager.clearFocus()
        }
    }

    var currentWidth by remember {
        mutableIntStateOf(
            if (model?.isSdxl ==
                true
            ) {
                1024
            } else if (model?.runOnCpu == true) {
                256
            } else {
                512
            },
        )
    }
    var currentHeight by remember {
        mutableIntStateOf(
            if (model?.isSdxl ==
                true
            ) {
                1024
            } else if (model?.runOnCpu == true) {
                256
            } else {
                512
            },
        )
    }
    var availableResolutions by remember { mutableStateOf<List<Resolution>>(emptyList()) }
    var showResolutionChangeDialog by remember { mutableStateOf(false) }
    var pendingResolution by remember { mutableStateOf<Resolution?>(null) }
    var backendRestartTrigger by remember { mutableIntStateOf(0) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    var isPreviewMode by remember { mutableStateOf(false) }
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val useImg2img = preferences.getBoolean("use_img2img", true)
    val enableTagAutocomplete = preferences.getBoolean("enable_tag_autocomplete", true)
    val tagSuggestionCount = 128
    val tagAutocompleteRepository = remember { TagAutocompleteRepository.getInstance(context) }
    val tagDictState by tagAutocompleteRepository.state.collectAsState()
    val tagAutocompleteAvailable = enableTagAutocomplete && tagDictState.mainImported

    LaunchedEffect(tagAutocompleteAvailable) {
        if (tagAutocompleteAvailable) {
            tagAutocompleteRepository.warmUp()
        }
    }

    // Names of imported textual-inversion embeddings (filename stems). Refreshed
    // when either prompt field gains focus so newly-imported embeddings show up
    // without re-entering the screen.
    var embeddingNames by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(isPromptFocused, isNegativePromptFocused) {
        if (!isPromptFocused && !isNegativePromptFocused) return@LaunchedEffect
        val names = withContext(Dispatchers.IO) {
            File(context.filesDir, "embeddings")
                .takeIf { it.isDirectory }
                ?.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.equals("safetensors", ignoreCase = true) }
                ?.map { it.nameWithoutExtension }
                ?.sortedBy { it.lowercase() }
                ?.toList()
                .orEmpty()
        }
        embeddingNames = names
    }

    var showCropScreen by remember { mutableStateOf(false) }
    var imageUriForCrop by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showInpaintScreen by remember { mutableStateOf(false) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isInpaintMode by remember { mutableStateOf(false) }
    var savedPathHistory by remember { mutableStateOf<List<PathData>?>(null) }
    var cropRect by remember { mutableStateOf<AndroidRect?>(null) }

    // True only when selectedImageUri points to a real source image from the gallery picker.
    // False when img2img was seeded from a result/history bitmap (selectedImageUri is a
    // synthetic tmp.txt path that holds base64, not a decodable image).
    var hasOriginalImageForStitch by remember { mutableStateOf(false) }

    var snapshotIsInpaintMode by remember { mutableStateOf(false) }
    var snapshotSelectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var snapshotCropRect by remember { mutableStateOf<AndroidRect?>(null) }
    var snapshotHasOriginalImage by remember { mutableStateOf(false) }
    // History-item ids whose bitmaps may be stitched back into the inpaint source:
    // the just-completed inpaint generation plus any upscaled copies derived from
    // it. Compared against currentDisplayedHistoryId so saving only stitches when
    // the bitmap on screen really is one of those (regardless of whether it's the
    // original Bitmap object or a fresh decode from clicking the thumbnail again).
    var stitchableHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var currentDisplayedHistoryId by remember { mutableStateOf<Long?>(null) }

    var saveAllJob: Job? by remember { mutableStateOf(null) }
    var batchGenerationJob: Job? by remember { mutableStateOf(null) }

    // Upscaler related states
    var showUpscalerDialog by remember { mutableStateOf(false) }
    var isUpscaling by remember { mutableStateOf(false) }
    val upscalerRepository = remember { UpscalerRepository(context) }
    val upscalerPreferences =
        remember { context.getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE) }

    // (effectiveWidth, effectiveHeight) is the size of the visible result.
    // For SDXL with non-1:1 aspect_ratio it equals the centered target_w/target_h
    // inside the 1024x1024 generation canvas; otherwise it equals the canvas itself.
    val effectiveSize = remember(model?.isSdxl, aspectRatio, currentWidth, currentHeight) {
        computeAspectTargetSize(model?.isSdxl == true, aspectRatio)
            ?: Pair(currentWidth, currentHeight)
    }
    val effectiveWidth = effectiveSize.first
    val effectiveHeight = effectiveSize.second

    fun clearImg2imgState() {
        selectedImageUri = null
        croppedBitmap = null
        maskBitmap = null
        isInpaintMode = false
        cropRect = null
        savedPathHistory = null
        base64EncodeDone = false
        hasOriginalImageForStitch = false
    }

    fun saveAllFields() {
        saveAllJob?.cancel()
        saveAllJob = scope.launch(Dispatchers.IO) {
            delay(1000)
            generationPreferences.saveAllFields(
                modelId = modelId,
                prompt = prompt,
                negativePrompt = negativePrompt,
                steps = steps,
                cfg = cfg,
                seed = seed,
                width = currentWidth,
                height = currentHeight,
                denoiseStrength = denoiseStrength,
                useOpenCL = useOpenCL,
                batchCounts = batchCounts,
                scheduler = scheduler,
                aspectRatio = aspectRatio,
            )
        }
    }

    val onStepsChange = remember {
        { value: Float ->
            steps = value
            saveAllFields()
        }
    }
    val onCfgChange = remember {
        { value: Float ->
            cfg = value
            saveAllFields()
        }
    }
    val onSizeChange = remember {
        { value: Float ->
            val rounded = (value / 64).roundToInt() * 64
            val newSize = rounded.coerceIn(128, 512)
            currentWidth = newSize
            currentHeight = newSize
            saveAllFields()
        }
    }
    val onDenoiseStrengthChange =
        remember {
            { value: Float ->
                denoiseStrength = value
                saveAllFields()
            }
        }
    val onSeedChange = remember {
        { value: String ->
            seed = value
            saveAllFields()
        }
    }
    var promptSuggestJob by remember { mutableStateOf<Job?>(null) }
    var negativePromptSuggestJob by remember { mutableStateOf<Job?>(null) }

    var promptTokenCount by remember { mutableIntStateOf(2) }
    var negativePromptTokenCount by remember { mutableIntStateOf(2) }
    var promptTokenMax by remember { mutableIntStateOf(77) }
    var negativePromptTokenMax by remember { mutableIntStateOf(77) }
    // UTF-16 index from which the prompt exceeds the token limit, or -1 when it
    // fits. Drives the greyed-out overflow hint in the prompt fields.
    var promptOverflowOffset by remember { mutableIntStateOf(-1) }
    var negativePromptOverflowOffset by remember { mutableIntStateOf(-1) }

    LaunchedEffect(prompt, isCheckingBackend) {
        if (isCheckingBackend) return@LaunchedEffect
        delay(400)
        val result = tokenizePromptRequest(prompt) ?: return@LaunchedEffect
        promptTokenCount = result.count
        promptTokenMax = result.maxLength
        promptOverflowOffset = result.overflowOffset
    }
    LaunchedEffect(negativePrompt, isCheckingBackend) {
        if (isCheckingBackend) return@LaunchedEffect
        delay(400)
        val result = tokenizePromptRequest(negativePrompt) ?: return@LaunchedEffect
        negativePromptTokenCount = result.count
        negativePromptTokenMax = result.maxLength
        negativePromptOverflowOffset = result.overflowOffset
    }

    // Build embedding TagSuggestion rows for the current query. Returns at most
    // `limit` entries: prefix matches first, then contains matches. Comparison
    // normalizes spaces/dashes to underscores so users typing either form match.
    fun embeddingSuggestionsFor(query: String, limit: Int = 5): List<TagSuggestion> {
        if (embeddingNames.isEmpty()) return emptyList()
        val q = query.trim()
            .lowercase()
            .replace(' ', '_')
            .replace('-', '_')
        if (q.isEmpty()) return emptyList()
        val prefix = mutableListOf<TagSuggestion>()
        val contains = mutableListOf<TagSuggestion>()
        for (name in embeddingNames) {
            val normalized = name.lowercase().replace(' ', '_').replace('-', '_')
            val idx = normalized.indexOf(q)
            if (idx < 0) continue
            val suggestion = TagSuggestion(
                replacementTag = name,
                primaryText = name,
                secondaryText = null,
                matchType = TagMatchType.Embedding,
                category = 0,
                postCount = 0,
                score = 0,
            )
            if (idx == 0) prefix += suggestion else contains += suggestion
        }
        return (prefix + contains).take(limit)
    }

    // Records `snapshot` as an undo checkpoint. Continuous typing within the
    // coalesce window collapses into one step; discrete edits (suggestion picks,
    // toolbar actions) pass coalesce = false to always start a new step.
    fun pushPromptHistory(snapshot: String, coalesce: Boolean) {
        val now = System.currentTimeMillis()
        val skip = coalesce && promptUndoStack.isNotEmpty() && now - promptHistoryAt < HISTORY_COALESCE_MS
        if (!skip) {
            promptUndoStack = (promptUndoStack + snapshot).takeLast(HISTORY_LIMIT)
        }
        promptRedoStack = emptyList()
        // Discrete edits leave the window closed so the next keystroke opens a
        // fresh step instead of merging into the discrete one.
        promptHistoryAt = if (coalesce) now else 0L
    }

    fun updatePromptField(value: TextFieldValue, recordHistory: Boolean = true) {
        val previousText = promptFieldValue.text
        val textChanged = value.text != previousText
        val selectionChanged = value.selection != promptFieldValue.selection
        if (textChanged && recordHistory) {
            pushPromptHistory(previousText, coalesce = true)
        }
        if (textChanged || selectionChanged) {
            promptPopupDismissed = false
        }
        promptFieldValue = value
        if (textChanged) {
            prompt = value.text
            saveAllFields()
        }
        if (!tagAutocompleteAvailable || !isPromptFocused) {
            promptSuggestJob?.cancel()
            promptSuggestions = emptyList()
            promptActiveQuery = null
            return
        }
        if (!textChanged && !selectionChanged) return
        val activeTag =
            TagAutocompleteRepository.extractActiveTag(value.text, value.selection.start)
        if (activeTag == null) {
            promptSuggestJob?.cancel()
            promptSuggestions = emptyList()
            promptActiveQuery = null
            return
        }
        promptActiveQuery = activeTag.token
        promptSuggestJob?.cancel()
        promptSuggestJob = scope.launch {
            delay(200)
            val embeddings = embeddingSuggestionsFor(activeTag.token)
            val results = tagAutocompleteRepository.suggest(activeTag.token, tagSuggestionCount)
            // Embeddings always pinned to the top; their relevance is local to
            // this user, so they outrank dictionary suggestions by construction.
            promptSuggestions = embeddings + results
        }
    }

    fun pushNegativePromptHistory(snapshot: String, coalesce: Boolean) {
        val now = System.currentTimeMillis()
        val skip = coalesce && negativePromptUndoStack.isNotEmpty() &&
            now - negativePromptHistoryAt < HISTORY_COALESCE_MS
        if (!skip) {
            negativePromptUndoStack = (negativePromptUndoStack + snapshot).takeLast(HISTORY_LIMIT)
        }
        negativePromptRedoStack = emptyList()
        negativePromptHistoryAt = if (coalesce) now else 0L
    }

    fun updateNegativePromptField(value: TextFieldValue, recordHistory: Boolean = true) {
        val previousText = negativePromptFieldValue.text
        val textChanged = value.text != previousText
        val selectionChanged = value.selection != negativePromptFieldValue.selection
        if (textChanged && recordHistory) {
            pushNegativePromptHistory(previousText, coalesce = true)
        }
        if (textChanged || selectionChanged) {
            negativePromptPopupDismissed = false
        }
        negativePromptFieldValue = value
        if (textChanged) {
            negativePrompt = value.text
            saveAllFields()
        }
        if (!tagAutocompleteAvailable || !isNegativePromptFocused) {
            negativePromptSuggestJob?.cancel()
            negativePromptSuggestions = emptyList()
            negativePromptActiveQuery = null
            return
        }
        if (!textChanged && !selectionChanged) return
        val activeTag =
            TagAutocompleteRepository.extractActiveTag(value.text, value.selection.start)
        if (activeTag == null) {
            negativePromptSuggestJob?.cancel()
            negativePromptSuggestions = emptyList()
            negativePromptActiveQuery = null
            return
        }
        negativePromptActiveQuery = activeTag.token
        negativePromptSuggestJob?.cancel()
        negativePromptSuggestJob = scope.launch {
            delay(200)
            val embeddings = embeddingSuggestionsFor(activeTag.token)
            val results = tagAutocompleteRepository.suggest(activeTag.token, tagSuggestionCount)
            negativePromptSuggestions = embeddings + results
        }
    }

    fun applyPromptSuggestion(suggestion: TagSuggestion) {
        // Discrete history step so an accidental pick can be undone. The popup is
        // left up (suggestions cleared, but the toolbar persists) for follow-up.
        pushPromptHistory(promptFieldValue.text, coalesce = false)
        val (updatedText, updatedSelection) = TagAutocompleteRepository.applySuggestion(
            promptFieldValue.text,
            promptFieldValue.selection.start,
            suggestion,
        )
        prompt = updatedText
        promptFieldValue = TextFieldValue(updatedText, TextRange(updatedSelection))
        promptSuggestions = emptyList()
        promptActiveQuery = null
        promptPopupDismissed = false
        saveAllFields()
    }

    fun applyNegativePromptSuggestion(suggestion: TagSuggestion) {
        pushNegativePromptHistory(negativePromptFieldValue.text, coalesce = false)
        val (updatedText, updatedSelection) = TagAutocompleteRepository.applySuggestion(
            negativePromptFieldValue.text,
            negativePromptFieldValue.selection.start,
            suggestion,
        )
        negativePrompt = updatedText
        negativePromptFieldValue = TextFieldValue(updatedText, TextRange(updatedSelection))
        negativePromptSuggestions = emptyList()
        negativePromptActiveQuery = null
        negativePromptPopupDismissed = false
        saveAllFields()
    }

    // Runs one of the suggestion-toolbar text edits against the prompt field as a
    // discrete undo step, then routes the result back through updatePromptField so
    // the suggestions (and the popup) refresh against the new caret position.
    fun runPromptTagAction(action: (String, Int) -> Pair<String, Int>?) {
        val (updatedText, updatedSelection) = action(
            promptFieldValue.text,
            promptFieldValue.selection.start,
        ) ?: return
        pushPromptHistory(promptFieldValue.text, coalesce = false)
        updatePromptField(
            TextFieldValue(updatedText, TextRange(updatedSelection)),
            recordHistory = false,
        )
    }

    fun runNegativePromptTagAction(action: (String, Int) -> Pair<String, Int>?) {
        val (updatedText, updatedSelection) = action(
            negativePromptFieldValue.text,
            negativePromptFieldValue.selection.start,
        ) ?: return
        pushNegativePromptHistory(negativePromptFieldValue.text, coalesce = false)
        updateNegativePromptField(
            TextFieldValue(updatedText, TextRange(updatedSelection)),
            recordHistory = false,
        )
    }

    fun undoPrompt() {
        if (promptUndoStack.isEmpty()) return
        val previous = promptUndoStack.last()
        promptUndoStack = promptUndoStack.dropLast(1)
        promptRedoStack = (promptRedoStack + promptFieldValue.text).takeLast(HISTORY_LIMIT)
        promptHistoryAt = 0L
        updatePromptField(
            TextFieldValue(previous, TextRange(previous.length)),
            recordHistory = false,
        )
    }

    fun redoPrompt() {
        if (promptRedoStack.isEmpty()) return
        val next = promptRedoStack.last()
        promptRedoStack = promptRedoStack.dropLast(1)
        promptUndoStack = (promptUndoStack + promptFieldValue.text).takeLast(HISTORY_LIMIT)
        promptHistoryAt = 0L
        updatePromptField(
            TextFieldValue(next, TextRange(next.length)),
            recordHistory = false,
        )
    }

    fun undoNegativePrompt() {
        if (negativePromptUndoStack.isEmpty()) return
        val previous = negativePromptUndoStack.last()
        negativePromptUndoStack = negativePromptUndoStack.dropLast(1)
        negativePromptRedoStack =
            (negativePromptRedoStack + negativePromptFieldValue.text).takeLast(HISTORY_LIMIT)
        negativePromptHistoryAt = 0L
        updateNegativePromptField(
            TextFieldValue(previous, TextRange(previous.length)),
            recordHistory = false,
        )
    }

    fun redoNegativePrompt() {
        if (negativePromptRedoStack.isEmpty()) return
        val next = negativePromptRedoStack.last()
        negativePromptRedoStack = negativePromptRedoStack.dropLast(1)
        negativePromptUndoStack =
            (negativePromptUndoStack + negativePromptFieldValue.text).takeLast(HISTORY_LIMIT)
        negativePromptHistoryAt = 0L
        updateNegativePromptField(
            TextFieldValue(next, TextRange(next.length)),
            recordHistory = false,
        )
    }

    val onBatchCountsChange = remember {
        { value: Float ->
            batchCounts = value.roundToInt().coerceIn(1, 10)
            saveAllFields()
        }
    }

    fun processSelectedImage(uri: Uri) {
        imageUriForCrop = uri
        showCropScreen = true
    }

    @Suppress("UnusedParameter") // base64String from cropify callback is re-derived later
    fun handleCropComplete(base64String: String, bitmap: Bitmap, rect: AndroidRect) {
        showCropScreen = false
        val sourceUri = imageUriForCrop
        selectedImageUri = sourceUri
        imageUriForCrop = null
        hasOriginalImageForStitch = true

        // CropImageScreen returns the cropped bitmap via cropify, whose output
        // can carry a sub-pixel offset relative to the cropRect we computed
        // from frameRect / imageRect. That's invisible when the patch is later
        // pasted back as a whole (SD1.5 / SDXL 1:1), but in SDXL aspect-pad
        // mode the patch goes through scale → pad → backend center-crop →
        // scale-back, and the per-step rounding leaks the offset as a
        // few-pixel stitch misalignment.
        //
        // Fix: re-crop directly from the original image using BitmapRegionDecoder
        // so the bitmap content is *strictly* the cropRect region in the
        // original's coordinate space. cropRect is also clamped to the original
        // bounds, and the clamped value is saved so stitch later paints to the
        // exact same pixel range we cropped from.
        scope.launch(Dispatchers.IO) {
            try {
                base64EncodeDone = false
                val aspectTarget =
                    computeAspectTargetSize(model?.isSdxl == true, aspectRatio)
                val targetW = aspectTarget?.first ?: currentWidth
                val targetH = aspectTarget?.second ?: currentHeight

                var clampedRect = rect
                val freshCropped: Bitmap? = try {
                    sourceUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            @Suppress("DEPRECATION")
                            val decoder = BitmapRegionDecoder.newInstance(input, false)
                                ?: throw IllegalStateException(
                                    "BitmapRegionDecoder.newInstance returned null",
                                )
                            try {
                                val safeLeft = rect.left.coerceAtLeast(0)
                                val safeTop = rect.top.coerceAtLeast(0)
                                val safeRight = rect.right.coerceAtMost(decoder.width)
                                val safeBottom = rect.bottom.coerceAtMost(decoder.height)
                                if (safeRight > safeLeft && safeBottom > safeTop) {
                                    val region = AndroidRect(
                                        safeLeft,
                                        safeTop,
                                        safeRight,
                                        safeBottom,
                                    )
                                    clampedRect = region
                                    decoder.decodeRegion(region, BitmapFactory.Options())
                                } else {
                                    null
                                }
                            } finally {
                                decoder.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(
                        "ModelRunScreen",
                        "BitmapRegionDecoder failed, fall back to cropify bitmap: ${e.message}",
                    )
                    null
                }

                val sourceBitmap = freshCropped ?: bitmap

                val scaled = withContext(Dispatchers.Default) {
                    if (sourceBitmap.width != targetW || sourceBitmap.height != targetH) {
                        sourceBitmap.scale(targetW, targetH)
                    } else {
                        sourceBitmap
                    }
                }

                val needsPad =
                    scaled.width != currentWidth || scaled.height != currentHeight
                val payload = if (needsPad) {
                    val padded = padBitmapToCanvas(scaled, currentWidth, currentHeight)
                    val baos = ByteArrayOutputStream()
                    padded.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    Base64.getEncoder().encodeToString(baos.toByteArray())
                } else {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    Base64.getEncoder().encodeToString(baos.toByteArray())
                }

                withContext(Dispatchers.Main) {
                    cropRect = clampedRect
                    croppedBitmap = scaled
                }

                val tmpFile = File(context.filesDir, "tmp.txt")
                tmpFile.writeText(payload)
                base64EncodeDone = true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    selectedImageUri = null
                    croppedBitmap = null
                    cropRect = null
                    hasOriginalImageForStitch = false
                }
            }
        }
    }

    fun handleInpaintComplete(maskBase64: String, maskBmp: Bitmap, pathHistory: List<PathData>) {
        showInpaintScreen = false
        isInpaintMode = true
        maskBitmap = maskBmp
        savedPathHistory = pathHistory

        scope.launch(Dispatchers.IO) {
            try {
                // The mask comes back at target size (matching the cropped image fed
                // into InpaintScreen). For SDXL aspect-pad mode we re-encode after
                // padding to currentWidth x currentHeight so it lines up with the
                // padded image upload.
                val needsPad = maskBmp.width != currentWidth || maskBmp.height != currentHeight
                val payload = if (needsPad) {
                    val padded = padBitmapToCanvas(maskBmp, currentWidth, currentHeight)
                    val baos = ByteArrayOutputStream()
                    padded.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    Base64.getEncoder().encodeToString(baos.toByteArray())
                } else {
                    maskBase64
                }
                val maskFile = File(context.filesDir, "mask.txt")
                maskFile.writeText(payload)

                withContext(Dispatchers.Main) {
                    base64EncodeDone = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    isInpaintMode = false
                    maskBitmap = null
                    savedPathHistory = null
                }
            }
        }
    }

    fun sendBitmapToImg2img(bitmap: Bitmap) {
        scope.launch {
            val ready = try {
                base64EncodeDone = false
                val aspectTarget = computeAspectTargetSize(model?.isSdxl == true, aspectRatio)
                val targetW = aspectTarget?.first ?: currentWidth
                val targetH = aspectTarget?.second ?: currentHeight

                // 1) Center-crop+scale the source to (targetW, targetH).
                // 2) If aspect padding is in effect, pad up to (currentWidth, currentHeight).
                val resized = withContext(Dispatchers.Default) {
                    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val dstRatio = targetW.toFloat() / targetH.toFloat()
                    val centerCropped = if (kotlin.math.abs(srcRatio - dstRatio) < 1e-3f) {
                        bitmap
                    } else {
                        val (cropW, cropH) = if (srcRatio > dstRatio) {
                            Pair((bitmap.height * dstRatio).toInt(), bitmap.height)
                        } else {
                            Pair(bitmap.width, (bitmap.width / dstRatio).toInt())
                        }
                        val cx = (bitmap.width - cropW) / 2
                        val cy = (bitmap.height - cropH) / 2
                        Bitmap.createBitmap(bitmap, cx, cy, cropW, cropH)
                    }
                    val scaled =
                        if (centerCropped.width != targetW || centerCropped.height != targetH) {
                            centerCropped.scale(targetW, targetH)
                        } else {
                            centerCropped.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    scaled
                }

                val displayBitmap = resized
                val uploadBitmap =
                    if (resized.width != currentWidth || resized.height != currentHeight) {
                        padBitmapToCanvas(resized, currentWidth, currentHeight)
                    } else {
                        resized
                    }

                val base64String = withContext(Dispatchers.IO) {
                    val baos = ByteArrayOutputStream()
                    uploadBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    Base64.getEncoder().encodeToString(baos.toByteArray())
                }

                withContext(Dispatchers.IO) {
                    File(context.filesDir, "tmp.txt").writeText(base64String)
                }

                croppedBitmap = displayBitmap
                cropRect = AndroidRect(0, 0, displayBitmap.width, displayBitmap.height)
                selectedImageUri = Uri.fromFile(File(context.filesDir, "tmp.txt"))
                hasOriginalImageForStitch = false
                base64EncodeDone = true
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "img2img failed: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
                base64EncodeDone = false
                selectedImageUri = null
                croppedBitmap = null
                cropRect = null
                hasOriginalImageForStitch = false
                false
            }

            if (ready) {
                try {
                    pagerState.animateScrollToPage(0)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Animation interrupted by another scroll — img2img data is already set, ignore
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        PickVisualMedia(),
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    val contentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    val requestStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            contentPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                msgMediaPermissionHint,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onSelectImageClick() {
        when {
            // Android 13+
            Build.VERSION.SDK_INT >= 33 -> {
                // PhotoPicker API
                photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            }

            // Android 12-
            else -> {
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        contentPickerLauncher.launch("image/*")
                    }

                    else -> {
                        requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }

    fun handleSaveImage(context: Context, bitmap: Bitmap, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!checkStoragePermission(context)) {
            onError("need storage permission to save image")
            return
        }

        // Only stitch when:
        //  - the image currently shown is the most recent inpaint generation or an
        //    upscaled copy of it (matched via history-item id, so clicking another
        //    thumbnail and back still works while clicks on unrelated thumbnails
        //    don't stitch), and
        //  - the source img2img/inpaint image was a real gallery image with a decodable
        //    URI (not a synthetic tmp.txt from sendBitmapToImg2img).
        val shouldStitch = snapshotIsInpaintMode &&
            snapshotCropRect != null &&
            snapshotSelectedImageUri != null &&
            snapshotHasOriginalImage &&
            currentDisplayedHistoryId != null &&
            currentDisplayedHistoryId in stitchableHistoryIds

        coroutineScope.launch {
            if (shouldStitch) {
                withContext(Dispatchers.IO) {
                    try {
                        val originalBitmap =
                            context.contentResolver.openInputStream(snapshotSelectedImageUri!!)!!
                                .use { BitmapFactory.decodeStream(it) }

                        val mutableOriginal =
                            originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

                        val rectW = snapshotCropRect!!.width()
                        val rectH = snapshotCropRect!!.height()
                        val resizedPatch = bitmap.scale(rectW, rectH)

                        val canvas = Canvas(mutableOriginal)
                        canvas.drawBitmap(
                            resizedPatch,
                            snapshotCropRect!!.left.toFloat(),
                            snapshotCropRect!!.top.toFloat(),
                            null,
                        )

                        saveImage(
                            context = context,
                            bitmap = mutableOriginal,
                            onSuccess = onSuccess,
                            onError = onError,
                        )
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Failed to create composite image: ${e.localizedMessage}")
                        }
                    }
                }
            } else {
                saveImage(
                    context = context,
                    bitmap = bitmap,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    fun cleanup() {
        try {
            currentBitmap = null
            generationParams = null
            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
            val backendServiceIntent = Intent(context, BackendService::class.java)
            context.stopService(backendServiceIntent)
            isRunning = false
            progress = 0f
            errorMessage = null
            currentBatchIndex = 0
            generationStartTime = null
            BackgroundGenerationService.resetState()
            coroutineScope.launch {
                pagerState.scrollToPage(0)
            }
            saveAllJob?.cancel()
            batchGenerationJob?.cancel()
        } catch (e: Exception) {
            Log.e("ModelRunScreen", "error", e)
        }
    }

    fun handleExit() {
        cleanup()
        BackgroundGenerationService.clearCompleteState()
        navController.navigateUp()
    }

    DisposableEffect(modelId) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val captureEnabled = prefs.getBoolean("enable_log_capture", false)
        if (captureEnabled) {
            LogCapture.start()
        }
        onDispose {
            if (captureEnabled) {
                LogCapture.stopAndPublish()
            }
            // Safety net for paths that bypass handleExit() (e.g. predictive back
            // popping the destination while not running).
            BackgroundGenerationService.clearCompleteState()
        }
    }

    LaunchedEffect(modelId, model?.runOnCpu) {
        if (model?.runOnCpu == false && model.isSdxl == false) {
            val baseResolution = Resolution(512, 512)
            val patchResolutions = PatchScanner.scanAvailableResolutions(context, modelId)

            val allResolutions =
                (listOf(baseResolution) + patchResolutions).distinctBy { "${it.width}x${it.height}" }
            availableResolutions = allResolutions
        }
    }

    LaunchedEffect(modelId) {
        if (!hasInitialized) {
            val prefs = generationPreferences.getPreferences(modelId).first()

            if (prefs.prompt.isEmpty() && prefs.negativePrompt.isEmpty()) {
                model?.let { m ->
                    if (m.defaultPrompt.isNotEmpty()) {
                        prompt = m.defaultPrompt
                        promptFieldValue =
                            TextFieldValue(m.defaultPrompt, TextRange(m.defaultPrompt.length))
                    }
                    if (m.defaultNegativePrompt.isNotEmpty()) {
                        negativePrompt = m.defaultNegativePrompt
                        negativePromptFieldValue = TextFieldValue(
                            m.defaultNegativePrompt,
                            TextRange(m.defaultNegativePrompt.length),
                        )
                    }
                    saveAllFields()
                }
            } else {
                prompt = prefs.prompt
                negativePrompt = prefs.negativePrompt
                promptFieldValue = TextFieldValue(prefs.prompt, TextRange(prefs.prompt.length))
                negativePromptFieldValue =
                    TextFieldValue(prefs.negativePrompt, TextRange(prefs.negativePrompt.length))
            }

            steps = prefs.steps
            cfg = prefs.cfg
            seed = prefs.seed
            denoiseStrength = prefs.denoiseStrength
            useOpenCL = prefs.useOpenCL
            batchCounts = prefs.batchCounts
            scheduler = prefs.scheduler
            // Without img2img the backend has no VAE encoder, so a stored
            // non-1:1 ratio would silently fall back to 1024x1024 anyway.
            aspectRatio = if (useImg2img) prefs.aspectRatio else "1:1"

            currentWidth =
                if (model?.isSdxl == true) {
                    1024
                } else if (prefs.width == -1) {
                    (if (model?.runOnCpu == true) 256 else 512)
                } else {
                    prefs.width
                }
            currentHeight =
                if (model?.isSdxl == true) {
                    1024
                } else if (prefs.height == -1) {
                    (if (model?.runOnCpu == true) 256 else 512)
                } else {
                    prefs.height
                }

            hasInitialized = true
        }
    }

    LaunchedEffect(hasInitialized) {
        if (hasInitialized && backendState !is BackendService.BackendState.Running) {
            val intent = Intent(context, BackendService::class.java).apply {
                putExtra("modelId", model?.id)
                putExtra("width", currentWidth)
                putExtra("height", currentHeight)
                putExtra("use_opencl", useOpenCL)
            }
            context.startForegroundService(intent)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cleanup()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_DESTROY -> {
                    cleanup()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cleanup()
        }
    }

    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is GenerationState.Progress -> {
                if (generationStartTime == null) {
                    generationStartTime = System.currentTimeMillis()
                }
                progress = state.progress
                isRunning = true
                state.intermediateImage?.let { intermediateBitmap = it }
            }

            is GenerationState.Complete -> {
                intermediateBitmap = null
                withContext(Dispatchers.Main) {
                    Log.d("ModelRunScreen", "update bitmap")

                    state.seed?.let { returnedSeed = it }
                    progress = 0f

                    val genTime = generationStartTime?.let { startTime ->
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        when {
                            duration < 1000 -> "${duration}ms"

                            duration < 60000 -> String.format(Locale.US, "%.1fs", duration / 1000.0)

                            else -> String.format(
                                Locale.US,
                                "%dm%ds",
                                duration / 60000,
                                (duration % 60000) / 1000,
                            )
                        }
                    }

                    val currentGenerationMode = when {
                        isInpaintMode -> GenerationMode.INPAINT
                        selectedImageUri != null -> GenerationMode.IMG2IMG
                        else -> GenerationMode.TXT2IMG
                    }

                    val newParams = GenerationParameters(
                        steps = generationParamsTmp.steps,
                        cfg = generationParamsTmp.cfg,
                        seed = returnedSeed,
                        prompt = generationParamsTmp.prompt,
                        negativePrompt = generationParamsTmp.negativePrompt,
                        generationTime = genTime,
                        width = if (model?.runOnCpu == true) generationParamsTmp.width else state.bitmap.width,
                        height = if (model?.runOnCpu == true) generationParamsTmp.height else state.bitmap.height,
                        runOnCpu = model?.runOnCpu ?: false,
                        denoiseStrength = generationParamsTmp.denoiseStrength,
                        useOpenCL = generationParamsTmp.useOpenCL,
                        scheduler = generationParamsTmp.scheduler,
                        mode = currentGenerationMode,
                    )

                    // Save to disk and update history list. The saved item's id is
                    // forwarded to both the snapshot and the currently-displayed marker
                    // so handleSaveImage can later confirm the user is still looking at
                    // this generation (and not a different history thumbnail).
                    coroutineScope.launch(Dispatchers.IO) {
                        val savedItem = historyManager.saveGeneratedImage(
                            modelId = modelId,
                            bitmap = state.bitmap,
                            params = newParams,
                            mode = currentGenerationMode,
                        )
                        if (savedItem != null) {
                            withContext(Dispatchers.Main) {
                                stitchableHistoryIds = setOf(savedItem.id)
                                currentDisplayedHistoryId = savedItem.id
                            }
                        }
                    }

                    currentBitmap = state.bitmap
                    generationParams = newParams
                    generationParamsModelId = modelId
                    imageVersion += 1

                    snapshotIsInpaintMode = isInpaintMode
                    snapshotSelectedImageUri = selectedImageUri
                    snapshotCropRect = cropRect
                    snapshotHasOriginalImage = hasOriginalImageForStitch
                    // stitchableHistoryIds / currentDisplayedHistoryId are set once
                    // the DB save above resolves.
                    stitchableHistoryIds = emptySet()
                    currentDisplayedHistoryId = null

                    Log.d(
                        "ModelRunScreen",
                        "params update: ${generationParams?.steps}, ${generationParams?.cfg}",
                    )

                    generationStartTime = null

                    if (pagerState.currentPage == 0 && !showAdvancedSettings) {
                        try {
                            pagerState.animateScrollToPage(1)
                        } finally {
                            BackgroundGenerationService.markBitmapConsumed()
                        }
                    } else {
                        BackgroundGenerationService.markBitmapConsumed()
                    }
                }
            }

            is GenerationState.Error -> {
                intermediateBitmap = null
                errorMessage = state.message
                isRunning = false
                progress = 0f
                generationStartTime = null
            }

            else -> {
                isRunning = false
                progress = 0f
            }
        }
    }

    // Only intercept back when a generation is running, so the predictive back
    // gesture can show NavHost's peek of the previous destination in idle state.
    if (isRunning) {
        BackHandler { showExitDialog = true }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.confirm_exit)) },
            text = { Text(stringResource(R.string.confirm_exit_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        handleExit()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showOpenCLWarningDialog) {
        AlertDialog(
            onDismissRequest = { showOpenCLWarningDialog = false },
            title = { Text("GPU Runtime Warning") },
            text = { Text(stringResource(R.string.opencl_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOpenCLWarningDialog = false
                        useOpenCL = true
                        saveAllFields()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenCLWarningDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCustomAspectRatioDialog) {
        var ratioWStr by remember { mutableStateOf("") }
        var ratioHStr by remember { mutableStateOf("") }
        var ratioError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCustomAspectRatioDialog = false },
            title = { Text(stringResource(R.string.aspect_ratio_custom_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.aspect_ratio_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = ratioWStr,
                            onValueChange = {
                                ratioWStr = it.filter { c -> c.isDigit() }.take(5)
                                ratioError =
                                    false
                            },
                            label = { Text("W") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            isError = ratioError,
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = ratioHStr,
                            onValueChange = {
                                ratioHStr = it.filter { c -> c.isDigit() }.take(5)
                                ratioError =
                                    false
                            },
                            label = { Text("H") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            isError = ratioError,
                        )
                    }
                    if (ratioError) {
                        Text(
                            stringResource(R.string.aspect_ratio_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = ratioWStr.toIntOrNull()
                    val h = ratioHStr.toIntOrNull()
                    if (w != null && h != null && w > 0 && h > 0) {
                        val newRatio = "$w:$h"
                        if (newRatio != aspectRatio) {
                            aspectRatio = newRatio
                            clearImg2imgState()
                            saveAllFields()
                        }
                        showCustomAspectRatioDialog = false
                    } else {
                        ratioError = true
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomAspectRatioDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showResolutionChangeDialog && pendingResolution != null) {
        AlertDialog(
            onDismissRequest = {
                showResolutionChangeDialog = false
                pendingResolution = null
            },
            title = { Text(stringResource(R.string.switch_resolution)) },
            text = { Text(stringResource(R.string.switch_resolution_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingResolution?.let { resolution ->
                            // Check aspect ratio change
                            val oldRatio =
                                if (currentHeight > 0) currentWidth.toFloat() / currentHeight.toFloat() else 1f
                            val newRatio =
                                if (resolution.height >
                                    0
                                ) {
                                    resolution.width.toFloat() / resolution.height.toFloat()
                                } else {
                                    1f
                                }

                            if (kotlin.math.abs(oldRatio - newRatio) > 0.01f) {
                                // Clear img2img data
                                selectedImageUri = null
                                croppedBitmap = null
                                maskBitmap = null
                                isInpaintMode = false
                                cropRect = null
                                savedPathHistory = null
                                base64EncodeDone = false
                                hasOriginalImageForStitch = false
                            }

                            currentWidth = resolution.width
                            currentHeight = resolution.height
                            scope.launch {
                                generationPreferences.saveResolution(
                                    modelId,
                                    resolution.width,
                                    resolution.height,
                                )
                            }
                            model?.let { m ->
                                val serviceIntent =
                                    Intent(context, BackendService::class.java).apply {
                                        action = BackendService.ACTION_RESTART
                                        putExtra("modelId", modelId)
                                        putExtra("width", resolution.width)
                                        putExtra("height", resolution.height)
                                    }
                                context.startForegroundService(serviceIntent)
                                isCheckingBackend = true
                                backendRestartTrigger++
                            }
                        }
                        showResolutionChangeDialog = false
                        pendingResolution = null
                        showAdvancedSettings = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResolutionChangeDialog = false
                        pendingResolution = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.reset)) },
            text = { Text(stringResource(R.string.reset_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        steps = 20f
                        cfg = 7f
                        seed = ""
                        batchCounts = 1
                        scheduler = "dpm"
                        aspectRatio = "1:1"
                        prompt = model?.defaultPrompt ?: ""
                        negativePrompt = model?.defaultNegativePrompt ?: ""
                        promptFieldValue = TextFieldValue(prompt, TextRange(prompt.length))
                        negativePromptFieldValue =
                            TextFieldValue(negativePrompt, TextRange(negativePrompt.length))
                        promptSuggestions = emptyList()
                        negativePromptSuggestions = emptyList()
                        denoiseStrength = 0.6f
                        scope.launch(Dispatchers.IO) {
                            generationPreferences.saveAllFields(
                                modelId = modelId,
                                prompt = model?.defaultPrompt ?: "",
                                negativePrompt = model?.defaultNegativePrompt ?: "",
                                steps = 20f,
                                cfg = 7f,
                                seed = "",
                                width = if (model?.isSdxl == true) {
                                    1024
                                } else if (model?.runOnCpu == true) {
                                    256
                                } else {
                                    512
                                },
                                height = if (model?.isSdxl == true) {
                                    1024
                                } else if (model?.runOnCpu == true) {
                                    256
                                } else {
                                    512
                                },
                                denoiseStrength = 0.6f,
                                useOpenCL = useOpenCL,
                                batchCounts = 1,
                                scheduler = "dpm",
                                aspectRatio = "1:1",
                            )
                        }
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        checkBackendHealth(
            backendState = BackendService.backendState,
            onHealthy = {
                isCheckingBackend = false
            },
            onUnhealthy = {
                isCheckingBackend = false
                errorMessage = msgBackendFailed
            },
        )
    }

    LaunchedEffect(backendRestartTrigger) {
        if (backendRestartTrigger > 0) {
            delay(500)
            checkBackendHealth(
                backendState = BackendService.backendState,
                onHealthy = {
                    isCheckingBackend = false
                },
                onUnhealthy = {
                    isCheckingBackend = false
                    errorMessage = msgBackendFailed
                },
            )
        }
    }

    // === Page Composable Functions ===
    @Composable
    fun PromptPage() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Reserve the IME area so the scroll viewport ends above the
                // keyboard; the focused prompt field is then scrolled above the IME
                // (which also keeps its window position accurate for the popup).
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = intermediateBitmap == null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.prompt_settings),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (useImg2img) {
                                    TextButton(
                                        onClick = { onSelectImageClick() },
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 8.dp,
                                        ),
                                    ) {
                                        Text(
                                            "img2img",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = "select image",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { showAdvancedSettings = true },
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 8.dp,
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.advanced_settings),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.settings),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (showAdvancedSettings) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showAdvancedSettings = false
                                    },
                                    title = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                stringResource(R.string.advanced_settings_title),
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(onClick = {
                                                val clipboard =
                                                    context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE,
                                                    ) as? ClipboardManager
                                                val raw = clipboard?.primaryClip
                                                    ?.takeIf { it.itemCount > 0 }
                                                    ?.getItemAt(0)
                                                    ?.coerceToText(context)
                                                    ?.toString()
                                                val imported = ParamShare.tryDecode(raw)
                                                if (imported != null) {
                                                    pendingImport = imported
                                                    clipboardImportChecked = true
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        msgImportNoParams,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentPaste,
                                                    contentDescription = stringResource(R.string.import_from_clipboard),
                                                )
                                            }
                                            IconButton(onClick = {
                                                val currentMode = when {
                                                    isInpaintMode -> GenerationMode.INPAINT
                                                    selectedImageUri != null -> GenerationMode.IMG2IMG
                                                    else -> GenerationMode.TXT2IMG
                                                }
                                                shareSourceParams = GenerationParameters(
                                                    steps = steps.toInt(),
                                                    cfg = cfg,
                                                    seed = seed.toLongOrNull(),
                                                    prompt = prompt,
                                                    negativePrompt = negativePrompt,
                                                    generationTime = null,
                                                    width = currentWidth,
                                                    height = currentHeight,
                                                    runOnCpu = model?.runOnCpu ?: false,
                                                    denoiseStrength = denoiseStrength,
                                                    useOpenCL = useOpenCL,
                                                    scheduler = scheduler,
                                                    mode = currentMode,
                                                )
                                                shareSourceModelId = modelId
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = stringResource(R.string.share),
                                                )
                                            }
                                        }
                                    },
                                    text = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(
                                                2.dp,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                                .padding(vertical = 4.dp),
                                        ) {
                                            // Aspect ratio needs the VAE encoder (inpaint-based
                                            // padding), which --no_img2img does not load.
                                            if (model?.isSdxl == true && useImg2img) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        stringResource(R.string.aspect_ratio),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    val presets = listOf("1:1", "3:4", "4:3")
                                                    val isCustom = aspectRatio !in presets
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                                                    ) {
                                                        presets.forEachIndexed { index, ratio ->
                                                            FilterChip(
                                                                selected = aspectRatio == ratio,
                                                                onClick = {
                                                                    if (!isRunning && aspectRatio != ratio) {
                                                                        aspectRatio = ratio
                                                                        clearImg2imgState()
                                                                        saveAllFields()
                                                                    }
                                                                },
                                                                label = { Text(ratio) },
                                                                enabled = !isRunning,
                                                            )
                                                        }
                                                        FilterChip(
                                                            selected = isCustom,
                                                            onClick = {
                                                                if (!isRunning) {
                                                                    showCustomAspectRatioDialog = true
                                                                }
                                                            },
                                                            label = {
                                                                Text(
                                                                    if (isCustom) {
                                                                        aspectRatio
                                                                    } else {
                                                                        stringResource(R.string.aspect_ratio_custom)
                                                                    },
                                                                )
                                                            },
                                                            enabled = !isRunning,
                                                        )
                                                    }
                                                }
                                            }
                                            if (model?.runOnCpu == false &&
                                                model.isSdxl == false &&
                                                availableResolutions.isNotEmpty()
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    // verticalArrangement = Arrangement.spacedBy(
                                                    //     4.dp
                                                    // )
                                                ) {
                                                    Text(
                                                        stringResource(R.string.resolution),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(
                                                                rememberScrollState(),
                                                            ),
                                                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                                                    ) {
                                                        availableResolutions.forEachIndexed { index, resolution ->
                                                            FilterChip(
                                                                selected = currentWidth == resolution.width &&
                                                                    currentHeight == resolution.height,
                                                                onClick = {
                                                                    if (
                                                                        !isRunning &&
                                                                        (
                                                                            resolution.width != currentWidth ||
                                                                                resolution.height != currentHeight
                                                                            )
                                                                    ) {
                                                                        pendingResolution = resolution
                                                                        showResolutionChangeDialog = true
                                                                    }
                                                                },
                                                                label = { Text(resolution.toString()) },
                                                                enabled = !isRunning,
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                // verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Split scheduler id into base + Karras flag so the UI
                                                // can offer one base chip per family plus a single
                                                // Karras switch, instead of listing every combination.
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
                                                        modifier = Modifier
                                                            .padding(end = 8.dp)
                                                            .alpha(if (karrasSupported) 1f else 0.4f),
                                                    )
                                                    CompositionLocalProvider(
                                                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                                                    ) {
                                                        Switch(
                                                            checked = karras && karrasSupported,
                                                            enabled = karrasSupported,
                                                            onCheckedChange = { enable ->
                                                                scheduler = if (enable) {
                                                                    "${baseId}_karras"
                                                                } else {
                                                                    baseId
                                                                }
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
                                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                                ) {
                                                    baseOptions.forEachIndexed { index, (id, label) ->
                                                        FilterChip(
                                                            selected = baseId == id,
                                                            onClick = {
                                                                if (baseId != id) {
                                                                    val nextKarras =
                                                                        karras && id != "lcm"
                                                                    scheduler = if (nextKarras) {
                                                                        "${id}_karras"
                                                                    } else {
                                                                        id
                                                                    }
                                                                    saveAllFields()
                                                                }
                                                            },
                                                            label = { Text(label) },
                                                        )
                                                    }
                                                }
                                            }

                                            Column {
                                                Text(
                                                    stringResource(
                                                        R.string.steps,
                                                        steps.roundToInt(),
                                                    ),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Slider(
                                                    value = steps,
                                                    onValueChange = onStepsChange,
                                                    valueRange = 1f..50f,
                                                    steps = 48,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }

                                            Column {
                                                Text(
                                                    "CFG Scale: %.1f".format(cfg),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Slider(
                                                    value = cfg,
                                                    onValueChange = onCfgChange,
                                                    valueRange = 1f..30f,
                                                    steps = 57,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (model?.runOnCpu ?: false) {
                                                Column {
                                                    Text(
                                                        stringResource(
                                                            R.string.image_size,
                                                            currentWidth,
                                                            currentHeight,
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    Slider(
                                                        value = currentWidth.toFloat(),
                                                        onValueChange = onSizeChange,
                                                        valueRange = 128f..512f,
                                                        steps = 5,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                            }
                                            if (model?.runOnCpu ?: false) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        "Runtime",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(end = 4.dp),
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                                        FilterChip(
                                                            selected = !useOpenCL,
                                                            onClick = {
                                                                useOpenCL = false
                                                                saveAllFields()
                                                            },
                                                            label = { Text("CPU") },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        FilterChip(
                                                            selected = useOpenCL,
                                                            onClick = {
                                                                if (!useOpenCL) {
                                                                    showOpenCLWarningDialog = true
                                                                }
                                                            },
                                                            label = { Text("GPU") },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                            }
                                            Column {
                                                Text(
                                                    stringResource(
                                                        R.string.batch_count,
                                                        batchCounts,
                                                    ),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Slider(
                                                    value = batchCounts.toFloat(),
                                                    onValueChange = onBatchCountsChange,
                                                    valueRange = 1f..10f,
                                                    steps = 8,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (useImg2img) {
                                                Column {
                                                    Text(
                                                        "[img2img]Denoise Strength: %.2f".format(
                                                            denoiseStrength,
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    Slider(
                                                        value = denoiseStrength,
                                                        onValueChange = onDenoiseStrengthChange,
                                                        valueRange = 0f..1f,
                                                        steps = 99,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(
                                                    8.dp,
                                                ),
                                            ) {
                                                OutlinedTextField(
                                                    value = seed,
                                                    onValueChange = onSeedChange,
                                                    label = { Text(stringResource(R.string.random_seed)) },
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                    ),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = MaterialTheme.shapes.medium,
                                                    trailingIcon = {
                                                        if (seed.isNotEmpty()) {
                                                            IconButton(onClick = {
                                                                seed = ""
                                                                saveAllFields()
                                                            }) {
                                                                Icon(
                                                                    Icons.Default.Clear,
                                                                    contentDescription = "clear",
                                                                )
                                                            }
                                                        }
                                                    },
                                                )

                                                if (returnedSeed != null) {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            seed =
                                                                returnedSeed.toString()
                                                            saveAllFields()
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Refresh,
                                                            contentDescription = stringResource(
                                                                R.string.use_last_seed,
                                                            ),
                                                            modifier = Modifier
                                                                .size(
                                                                    20.dp,
                                                                )
                                                                .padding(end = 4.dp),
                                                        )
                                                        Text(
                                                            stringResource(
                                                                R.string.use_last_seed,
                                                                returnedSeed.toString(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showResetConfirmDialog = true
                                                },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error,
                                                ),
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = stringResource(
                                                        R.string.reset,
                                                    ),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .padding(end = 4.dp),
                                                )
                                                Text(stringResource(R.string.reset))
                                            }

                                            TextButton(onClick = {
                                                showAdvancedSettings = false
                                            }) {
                                                Text(stringResource(R.string.confirm))
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        PromptTagTextField(
                            value = promptFieldValue,
                            onValueChange = ::updatePromptField,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                PromptCountLabel(
                                    label = stringResource(R.string.image_prompt),
                                    count = promptTokenCount,
                                    max = promptTokenMax,
                                    showCount = prompt.isNotEmpty(),
                                )
                            },
                            suggestions = promptSuggestions,
                            onSuggestionClick = ::applyPromptSuggestion,
                            showSuggestions = tagAutocompleteAvailable && isPromptFocused &&
                                !promptPopupDismissed,
                            // Toolbar stays up even on an empty prompt so undo/redo
                            // remain reachable.
                            showToolbar = tagAutocompleteAvailable && isPromptFocused &&
                                !promptPopupDismissed,
                            highlightQuery = promptActiveQuery,
                            overflowOffset = promptOverflowOffset,
                            onFocusChanged = {
                                isPromptFocused = it
                                if (!it) promptSuggestions = emptyList()
                            },
                            onDismissSuggestions = { promptPopupDismissed = true },
                            onUndo = ::undoPrompt,
                            onRedo = ::redoPrompt,
                            undoEnabled = promptUndoStack.isNotEmpty(),
                            redoEnabled = promptRedoStack.isNotEmpty(),
                            onAddTag = {
                                runPromptTagAction(TagAutocompleteRepository::appendTagAfterActive)
                            },
                            onClearTag = {
                                runPromptTagAction(TagAutocompleteRepository::clearActiveTag)
                            },
                            onIncreaseWeight = {
                                runPromptTagAction { text, sel ->
                                    TagAutocompleteRepository.adjustActiveTagWeight(text, sel, 0.1)
                                }
                            },
                            onDecreaseWeight = {
                                runPromptTagAction { text, sel ->
                                    TagAutocompleteRepository.adjustActiveTagWeight(text, sel, -0.1)
                                }
                            },
                        )

                        PromptTagTextField(
                            value = negativePromptFieldValue,
                            onValueChange = ::updateNegativePromptField,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                PromptCountLabel(
                                    label = stringResource(R.string.negative_prompt),
                                    count = negativePromptTokenCount,
                                    max = negativePromptTokenMax,
                                    showCount = negativePrompt.isNotEmpty(),
                                )
                            },
                            suggestions = negativePromptSuggestions,
                            onSuggestionClick = ::applyNegativePromptSuggestion,
                            showSuggestions = tagAutocompleteAvailable && isNegativePromptFocused &&
                                !negativePromptPopupDismissed,
                            showToolbar = tagAutocompleteAvailable && isNegativePromptFocused &&
                                !negativePromptPopupDismissed,
                            highlightQuery = negativePromptActiveQuery,
                            overflowOffset = negativePromptOverflowOffset,
                            onFocusChanged = {
                                isNegativePromptFocused = it
                                if (!it) negativePromptSuggestions = emptyList()
                            },
                            onDismissSuggestions = { negativePromptPopupDismissed = true },
                            onUndo = ::undoNegativePrompt,
                            onRedo = ::redoNegativePrompt,
                            undoEnabled = negativePromptUndoStack.isNotEmpty(),
                            redoEnabled = negativePromptRedoStack.isNotEmpty(),
                            onAddTag = {
                                runNegativePromptTagAction(
                                    TagAutocompleteRepository::appendTagAfterActive,
                                )
                            },
                            onClearTag = {
                                runNegativePromptTagAction(
                                    TagAutocompleteRepository::clearActiveTag,
                                )
                            },
                            onIncreaseWeight = {
                                runNegativePromptTagAction { text, sel ->
                                    TagAutocompleteRepository.adjustActiveTagWeight(text, sel, 0.1)
                                }
                            },
                            onDecreaseWeight = {
                                runNegativePromptTagAction { text, sel ->
                                    TagAutocompleteRepository.adjustActiveTagWeight(text, sel, -0.1)
                                }
                            },
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                Log.d(
                                    "ModelRunScreen",
                                    "start generation",
                                )
                                generationParamsTmp = GenerationParameters(
                                    steps = steps.roundToInt(),
                                    cfg = cfg,
                                    seed = 0,
                                    prompt = prompt,
                                    negativePrompt = negativePrompt,
                                    generationTime = "",
                                    width = currentWidth,
                                    height = currentHeight,
                                    runOnCpu = model?.runOnCpu ?: false,
                                    denoiseStrength = denoiseStrength,
                                    useOpenCL = useOpenCL,
                                    scheduler = scheduler,
                                )

                                Log.d(
                                    "ModelRunScreen",
                                    "start generation batch: $batchCounts times",
                                )

                                // If seed is set, only generate once regardless of batch count
                                val actualBatchCount =
                                    if (seed.isNotBlank()) 1 else batchCounts

                                batchGenerationJob = coroutineScope.launch {
                                    for (i in 0 until actualBatchCount) {
                                        currentBatchIndex = i + 1
                                        Log.d(
                                            "ModelRunScreen",
                                            "preparing batch $i",
                                        )

                                        // Update generationParamsTmp to reflect current parameters
                                        // This allows parameters to be changed during batch execution
                                        generationParamsTmp = GenerationParameters(
                                            steps = steps.roundToInt(),
                                            cfg = cfg,
                                            seed = 0,
                                            prompt = prompt,
                                            negativePrompt = negativePrompt,
                                            generationTime = "",
                                            width = currentWidth,
                                            height = currentHeight,
                                            runOnCpu = model?.runOnCpu ?: false,
                                            denoiseStrength = denoiseStrength,
                                            useOpenCL = useOpenCL,
                                            scheduler = scheduler,
                                        )

                                        val batchIntent = Intent(
                                            context,
                                            BackgroundGenerationService::class.java,
                                        ).apply {
                                            putExtra("prompt", prompt)
                                            putExtra(
                                                "negative_prompt",
                                                negativePrompt,
                                            )
                                            putExtra("steps", steps.roundToInt())
                                            putExtra("cfg", cfg)
                                            seed.toLongOrNull()
                                                ?.let { putExtra("seed", it) }
                                            putExtra("width", currentWidth)
                                            putExtra("height", currentHeight)
                                            // Backend now crops progress previews to the
                                            // visible target rectangle, so the service must
                                            // decode each preview with the effective dims
                                            // (target_w/h), not the 1024 canvas size.
                                            putExtra("effective_width", effectiveWidth)
                                            putExtra("effective_height", effectiveHeight)
                                            putExtra(
                                                "denoise_strength",
                                                denoiseStrength,
                                            )
                                            putExtra("use_opencl", useOpenCL)
                                            putExtra("scheduler", scheduler)
                                            putExtra("aspect_ratio", aspectRatio)
                                            putExtra("batch_index", i)
                                            if (selectedImageUri != null && base64EncodeDone) {
                                                putExtra("has_image", true)
                                                if (isInpaintMode && maskBitmap != null) {
                                                    putExtra("has_mask", true)
                                                }
                                            }
                                        }

                                        Log.d(
                                            "ModelRunScreen",
                                            "start service - batch $i",
                                        )

                                        context.startForegroundService(batchIntent)
                                        Log.d(
                                            "ModelRunScreen",
                                            "start service sent - batch $i",
                                        )

                                        BackgroundGenerationService.generationState
                                            .first { state ->
                                                state is GenerationState.Complete ||
                                                    state is GenerationState.Error
                                            }

                                        Log.d(
                                            "ModelRunScreen",
                                            "batch $i completed, waiting for service to stop",
                                        )

                                        // Wait for service to actually stop
                                        val waitStartTime =
                                            System.currentTimeMillis()
                                        val timeoutMs = 5000L
                                        while (BackgroundGenerationService.isServiceRunning.value) {
                                            if (System.currentTimeMillis() - waitStartTime > timeoutMs) {
                                                Log.w(
                                                    "ModelRunScreen",
                                                    "Timeout waiting for service to stop",
                                                )
                                                break
                                            }
                                            delay(100)
                                        }

                                        Log.d(
                                            "ModelRunScreen",
                                            "service stopped, wait time: ${System.currentTimeMillis() - waitStartTime}ms",
                                        )

                                        BackgroundGenerationService.resetState()
                                        Log.d(
                                            "ModelRunScreen",
                                            "service state reset, ready for next batch",
                                        )
                                    }
                                    currentBatchIndex = 0
                                    isRunning = false
                                    Log.d(
                                        "ModelRunScreen",
                                        "all batches completed, isRunning set to false",
                                    )
                                }
                            },
                            enabled = serviceState !is GenerationState.Progress && !isRunning && !isUpscaling,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            AnimatedContent(
                                targetState = serviceState is GenerationState.Progress || isUpscaling,
                                transitionSpec = {
                                    (
                                        fadeIn(animationSpec = tween(Motion.DurationShort)) + scaleIn(
                                            initialScale = 0.8f,
                                            animationSpec = tween(Motion.DurationShort),
                                        )
                                        )
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(Motion.DurationShort)) + scaleOut(
                                                targetScale = 0.8f,
                                                animationSpec = tween(Motion.DurationShort),
                                            ),
                                        )
                                },
                                label = "GenerateButtonContent",
                            ) { isLoading ->
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(stringResource(R.string.generate_image))
                                }
                            }
                        }
                    }
                }
            }
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
                                contentDescription = null,
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
                            text = if (currentBatchIndex > 0) {
                                "${
                                    stringResource(
                                        R.string.generating,
                                    )
                                } ($currentBatchIndex/$batchCounts)…"
                            } else {
                                stringResource(
                                    R.string.generating,
                                )
                            },
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Generation Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedImageUri != null && base64EncodeDone,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Card(
                            modifier = Modifier
                                .size(100.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Box {
                                croppedBitmap?.let { bitmap ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(
                                            LocalContext.current,
                                        )
                                            .data(bitmap)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Cropped Image",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } ?: selectedImageUri?.let { uri ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(
                                            LocalContext.current,
                                        )
                                            .data(uri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Selected Image",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        croppedBitmap = null
                                        maskBitmap = null
                                        isInpaintMode = false
                                        cropRect = null
                                        savedPathHistory = null
                                        hasOriginalImageForStitch = false
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(
                                                alpha = 0.7f,
                                            ),
                                            shape = CircleShape,
                                        )
                                        .align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Remove Image",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = croppedBitmap != null && !isInpaintMode,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(12.dp))
                                SmallFloatingActionButton(
                                    onClick = {
                                        if (croppedBitmap != null) {
                                            showInpaintScreen = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Please Crop First",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Brush,
                                        contentDescription = "Set Mask",
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isInpaintMode && maskBitmap != null,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    onClick = {
                                        if (croppedBitmap != null && maskBitmap != null) {
                                            showInpaintScreen = true
                                        }
                                    },
                                    modifier = Modifier.size(100.dp),
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Box {
                                        maskBitmap?.let { mb ->
                                            AsyncImage(
                                                model = ImageRequest.Builder(
                                                    LocalContext.current,
                                                )
                                                    .data(mb)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Mask Image",
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                maskBitmap = null
                                                isInpaintMode = false
                                                savedPathHistory = null
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.7f,
                                                    ),
                                                    shape = CircleShape,
                                                )
                                                .align(Alignment.TopEnd),
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear Mask",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ResultPage() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Crossfade(
                targetState = currentBitmap != null,
                label = "result_crossfade",
            ) { hasResult ->
                if (!hasResult) {
                    ElevatedCard(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val iconScale = remember { Animatable(1f) }
                            LaunchedEffect(Unit) {
                                iconScale.animateTo(
                                    targetValue = 1.1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1500),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer(
                                        scaleX = iconScale.value,
                                        scaleY = iconScale.value,
                                    ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.no_results),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.no_results_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.go_to_generate))
                            }
                        }
                    }
                } else {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.result_tab),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                currentBitmap?.let { bitmap ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            8.dp,
                                        ),
                                    ) {
                                        if (BuildConfig.FLAVOR == "filter") {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    showReportDialog = true
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Report,
                                                    contentDescription = "report inappropriate content",
                                                )
                                            }
                                        }

                                        // Upscaler button - only show for NPU runtime and resolution <= 1024
                                        if (!model?.runOnCpu!! &&
                                            generationParams?.let {
                                                maxOf(
                                                    it.width,
                                                    it.height,
                                                ) <= 1024
                                            } == true
                                        ) {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    showUpscalerDialog = true
                                                },
                                                enabled = !isRunning && !isUpscaling,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoFixHigh,
                                                    contentDescription = "upscale image",
                                                )
                                            }
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                handleSaveImage(
                                                    context = context,
                                                    bitmap = bitmap,
                                                    onSuccess = {
                                                        Toast.makeText(
                                                            context,
                                                            msgImageSaved,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    },
                                                    onError = { error ->
                                                        Toast.makeText(
                                                            context,
                                                            error,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    },
                                                )
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = "save image",
                                            )
                                        }
                                    }
                                }
                            }

                            // The shadowed Surface stays outside AnimatedContent: a
                            // drop shadow rendered inside the crossfade's alpha layer
                            // composites with a rectangular outline and flashes square
                            // corners. Keeping it static lets only the image crossfade,
                            // clipped to the rounded shape.
                            Surface(
                                onClick = { isPreviewMode = true },
                                enabled = currentBitmap != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 4.dp,
                            ) {
                                AnimatedContent(
                                    targetState = imageVersion to currentBitmap,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        fadeIn(animationSpec = Motion.Fade) togetherWith
                                            fadeOut(animationSpec = Motion.FadeOut)
                                    },
                                    label = "ImagePreviewCrossfade",
                                ) { (_, bitmap) ->
                                    bitmap?.let {
                                        AsyncImage(
                                            model = ImageRequest.Builder(
                                                LocalContext.current,
                                            )
                                                .data(it)
                                                .size(coil.size.Size.ORIGINAL)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "generated image",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }

                            if (historyItems.size > 1) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp,
                                    ),
                                ) {
                                    items(historyItems.take(20).size) { idx ->
                                        val item = historyItems[idx]
                                        Card(
                                            onClick = {
                                                val bitmap =
                                                    BitmapFactory.decodeFile(
                                                        item.imageFile.absolutePath,
                                                    )
                                                if (bitmap != null) {
                                                    currentBitmap = bitmap
                                                    generationParams = item.params
                                                    generationParamsModelId = item.modelId
                                                    currentDisplayedHistoryId = item.id
                                                    imageVersion++
                                                }
                                            },
                                            modifier = Modifier.size(72.dp),
                                            shape = MaterialTheme.shapes.small,
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(
                                                    LocalContext.current,
                                                )
                                                    .data(item.imageFile)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "thumb",
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                onClick = { showParametersDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.generation_params),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "view details",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    generationParams?.let { params ->
                                        Text(
                                            stringResource(
                                                R.string.result_params,
                                                params.steps,
                                                params.cfg,
                                                params.seed.toString(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            stringResource(
                                                R.string.result_params_2,
                                                params.width,
                                                params.height,
                                                params.generationTime
                                                    ?: "unknown",
                                                if (params.runOnCpu) {
                                                    if (params.useOpenCL) "GPU" else "CPU"
                                                } else {
                                                    "NPU"
                                                },
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showReportDialog && currentBitmap != null && generationParams != null) {
                AlertDialog(
                    onDismissRequest = { showReportDialog = false },
                    title = { Text("Report") },
                    text = {
                        Column {
//                                                Text("Report this image?")
                            Text(
                                "Report this image if you feel it is inappropriate. Params and image will be sent to the server for review.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showReportDialog = false
                                coroutineScope.launch {
                                    currentBitmap?.let { bitmap ->
                                        reportImage(
                                            bitmap = bitmap,
                                            modelName = model?.name ?: "",
                                            params = generationParams!!,
                                            onSuccess = {
                                                Toast.makeText(
                                                    context,
                                                    "Thanks for your report.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            },
                                            onError = { error ->
                                                Toast.makeText(
                                                    context,
                                                    "Error: $error",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            },
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Report")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReportDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
            if (showParametersDialog && generationParams != null) {
                GenerationParamsDialog(
                    title = stringResource(R.string.params_detail),
                    params = generationParams!!,
                    modelId = generationParamsModelId,
                    showImg2imgButton = useImg2img,
                    onShare = {
                        shareSourceParams = generationParams
                        shareSourceModelId = generationParamsModelId
                    },
                    onSendToImg2img = {
                        val bmp = currentBitmap
                        if (bmp != null) {
                            sendBitmapToImg2img(bmp)
                            showParametersDialog = false
                        } else {
                            Toast.makeText(
                                context,
                                "No image available",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onReproduce = {
                        generationParams?.let {
                            pendingReproduceParams = it
                            showParametersDialog = false
                            showReproduceParamsDialog = true
                        }
                    },
                    onDismiss = { showParametersDialog = false },
                )
            }
        }
    }

    @Composable
    fun HistoryPage() {
        // History page
        val locale = LocalConfiguration.current.locales[0]
        // Handle back button in selection mode
        BackHandler(enabled = isSelectionMode && !isBatchSaving) {
            isSelectionMode = false
            selectedItems.clear()
        }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            HistoryFilterBar(
                filter = historyFilter,
                currentModelId = modelId,
                onShowFilterSheet = { showHistoryFilterSheet = true },
                onSetCurrentModelOnly = {
                    historyFilter = historyFilter.copy(modelIds = setOf(modelId))
                },
                onSetAllModels = {
                    historyFilter = historyFilter.copy(modelIds = null)
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                if (historyItems.isEmpty()) {
                    var emptyVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { emptyVisible = true }
                    val emptyAlpha by animateFloatAsState(
                        targetValue = if (emptyVisible) 1f else 0f,
                        animationSpec = Motion.Fade,
                        label = "emptyAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(emptyAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.offset(y = (-60).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.no_history),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.no_history_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(historyItems.size) { index ->
                            val item = historyItems[index]
                            val isSelected = selectedItems.contains(item)
                            Card(
                                modifier = Modifier.aspectRatio(1f),
                                shape = MaterialTheme.shapes.medium,
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 2.dp,
                                ),
                            ) {
                                // Clickable inside the card so its ripple is clipped
                                // to the rounded shape (square corners otherwise).
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                // Toggle selection
                                                if (isSelected) {
                                                    selectedItems.remove(item)
                                                    if (selectedItems.isEmpty()) {
                                                        isSelectionMode = false
                                                    }
                                                } else {
                                                    selectedItems.add(item)
                                                }
                                            } else {
                                                // Normal preview
                                                selectedHistoryItem = item
                                                showHistoryDetailDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedItems.clear()
                                                selectedItems.add(item)
                                            }
                                        },
                                    ),
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(item.imageFile)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Generated image",
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.2f,
                                                    ),
                                                ),
                                        )
                                    }

                                    // Timestamp overlay
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomStart),
                                        shape = RoundedCornerShape(
                                            topStart = 0.dp,
                                            topEnd = 4.dp,
                                            bottomStart = 12.dp,
                                            bottomEnd = 0.dp,
                                        ),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            .copy(alpha = 0.8f),
                                    ) {
                                        Text(
                                            text = remember(item.timestamp, locale) {
                                                java.text.SimpleDateFormat(
                                                    "MM/dd HH:mm",
                                                    locale,
                                                ).format(java.util.Date(item.timestamp))
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 3.dp,
                                            ),
                                        )
                                    }

                                    // Selection indicator
                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .background(
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
                                                    },
                                                    shape = CircleShape,
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.White
                                                    },
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating selection mode bottom bar
                if (isSelectionMode) {
                    val visibleItems = historyItems
                    val isAllSelected =
                        selectedItems.size == visibleItems.size && visibleItems.all { it in selectedItems }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedItems.clear()
                                },
                                enabled = !isBatchSaving,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit selection mode",
                                )
                            }
                            Text(
                                text = pluralStringResource(
                                    R.plurals.selected_items_count,
                                    selectedItems.size,
                                    selectedItems.size,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isAllSelected) {
                                            selectedItems.clear()
                                            isSelectionMode = false
                                        } else {
                                            selectedItems.clear()
                                            selectedItems.addAll(visibleItems)
                                        }
                                    },
                                    enabled = !isBatchSaving,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = if (isAllSelected) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.CheckCircleOutline
                                        },
                                        contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                                    )
                                }
                                IconButton(
                                    onClick = { showBatchSaveDialog = true },
                                    enabled = selectedItems.isNotEmpty() && !isBatchSaving,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save selected",
                                    )
                                }
                                IconButton(
                                    onClick = { showBatchDeleteDialog = true },
                                    enabled = selectedItems.isNotEmpty() && !isBatchSaving,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete selected",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                focusManager.clearFocus()
            },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        // Hide title when collapsed
                        if (scrollBehavior.state.collapsedFraction < 0.5f) {
                            Column {
                                Text(
                                    text = model?.name ?: "Running Model",
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                )
                                Text(
                                    text = model?.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isRunning) {
                                showExitDialog = true
                            } else {
                                handleExit()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Row {
                            val tabs = listOf(
                                stringResource(R.string.prompt_tab),
                                stringResource(R.string.result_tab),
                                stringResource(R.string.history_tab),
                            )
                            tabs.forEachIndexed { index, label ->
                                val selected = pagerState.currentPage == index
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            focusManager.clearFocus()
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (model != null) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        // Mark the Scaffold insets (incl. the navigation bar) as
                        // consumed so the prompt page's imePadding only reserves the
                        // remaining IME height, instead of double-counting the nav
                        // bar and leaving a background strip above the keyboard.
                        .consumeWindowInsets(paddingValues),
                ) { page ->
                    when (page) {
                        0 -> PromptPage()
                        1 -> ResultPage()
                        2 -> HistoryPage()
                    }
                }
            }
        }
        if (showCropScreen && imageUriForCrop != null) {
            val aspectTarget = computeAspectTargetSize(model?.isSdxl == true, aspectRatio)
            val cropW = aspectTarget?.first ?: currentWidth
            val cropH = aspectTarget?.second ?: currentHeight
            CropImageScreen(
                imageUri = imageUriForCrop!!,
                width = cropW,
                height = cropH,
                onCropComplete = { base64String, bitmap, rect ->
                    handleCropComplete(base64String, bitmap, rect)
                },
                onCancel = {
                    showCropScreen = false
                    imageUriForCrop = null
                    selectedImageUri = null
                    hasOriginalImageForStitch = false
                },
            )
        }
        if (showInpaintScreen && croppedBitmap != null) {
            InpaintScreen(
                originalBitmap = croppedBitmap!!,
                existingMaskBitmap = if (isInpaintMode) maskBitmap else null,
                existingPathHistory = savedPathHistory,
                onInpaintComplete = { maskBase64, originalBitmap, maskBitmap, pathHistory ->
                    handleInpaintComplete(maskBase64, maskBitmap, pathHistory)
                },
                onCancel = {
                    showInpaintScreen = false
                },
            )
        }
    }
    if (isPreviewMode && currentBitmap != null) {
        ZoomableImageOverlay(
            bitmap = currentBitmap,
            onDismiss = { isPreviewMode = false },
            showScaleIndicator = true,
            topEndContent = {
                OverlayIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "close preview",
                    onClick = { isPreviewMode = false },
                )
            },
        )
    }

    // Upscaler dialog
    if (showUpscalerDialog) {
        var tempSelectedUpscalerId by remember {
            mutableStateOf(upscalerPreferences.getString("${modelId}_selected_upscaler", null))
        }
        var downloadingUpscalerId by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

        val downloadState by ModelDownloadService.downloadState.collectAsState()

        LaunchedEffect(downloadState) {
            when (val state = downloadState) {
                is ModelDownloadService.DownloadState.Downloading -> {
                    val upscaler = upscalerRepository.upscalers.find { it.id == state.modelId }
                    if (upscaler != null) {
                        downloadingUpscalerId = upscaler.id
                        downloadProgress = DownloadProgress(
                            progress = state.progress,
                            downloadedBytes = state.downloadedBytes,
                            totalBytes = state.totalBytes,
                        )
                    }
                }

                is ModelDownloadService.DownloadState.Success -> {
                    upscalerRepository.refreshUpscalerState(state.modelId)
                    downloadingUpscalerId = null
                    downloadProgress = null
                    Toast.makeText(
                        context,
                        msgDownloadDone,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is ModelDownloadService.DownloadState.Error -> {
                    downloadingUpscalerId = null
                    downloadProgress = null
                    Toast.makeText(
                        context,
                        msgErrorDownloadFailed.format(state.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is ModelDownloadService.DownloadState.Extracting -> {
                    val upscaler = upscalerRepository.upscalers.find { it.id == state.modelId }
                    if (upscaler != null) {
                        downloadingUpscalerId = upscaler.id
                        downloadProgress = null // Indeterminate progress during extraction
                    }
                }

                is ModelDownloadService.DownloadState.Idle -> {
                    if (downloadingUpscalerId != null && downloadProgress == null) {
                        downloadingUpscalerId = null
                    }
                }
            }
        }

        UpscalerSelectDialog(
            upscalers = upscalerRepository.upscalers,
            selectedUpscalerId = tempSelectedUpscalerId,
            downloadingUpscalerId = downloadingUpscalerId,
            downloadProgress = downloadProgress,
            onDismiss = { showUpscalerDialog = false },
            onSelectUpscaler = { upscalerId ->
                tempSelectedUpscalerId = upscalerId
            },
            onConfirm = {
                val selectedUpscaler =
                    upscalerRepository.upscalers.find { it.id == tempSelectedUpscalerId }
                if (selectedUpscaler != null && selectedUpscaler.isDownloaded) {
                    // Save selection
                    upscalerPreferences.edit {
                        putString("${modelId}_selected_upscaler", selectedUpscaler.id)
                    }
                    showUpscalerDialog = false

                    // Execute upscale
                    currentBitmap?.let { bitmap ->
                        // If the source image is stitch-eligible (an inpaint result or
                        // an upscaled copy of one), its upscaled copy is too.
                        val sourceIsStitchable =
                            currentDisplayedHistoryId != null &&
                                currentDisplayedHistoryId in stitchableHistoryIds
                        isUpscaling = true
                        scope.launch {
                            try {
                                val upscaledBitmap = performUpscale(
                                    context = context,
                                    bitmap = bitmap,
                                    upscalerId = selectedUpscaler.id,
                                )

                                // Save upscaled image via HistoryManager (DB + JPG file)
                                generationParams?.let { params ->
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val updatedParams = params.copy(
                                                width = upscaledBitmap.width,
                                                height = upscaledBitmap.height,
                                            )
                                            val sourceMode = selectedHistoryItem?.mode
                                                ?: GenerationMode.UNKNOWN
                                            val saved = historyManager.saveGeneratedImage(
                                                modelId = modelId,
                                                bitmap = upscaledBitmap,
                                                params = updatedParams,
                                                mode = sourceMode,
                                                upscalerId = selectedUpscaler.id,
                                            )
                                            if (saved != null) {
                                                withContext(Dispatchers.Main) {
                                                    currentBitmap = upscaledBitmap
                                                    generationParams = updatedParams
                                                    generationParamsModelId = modelId
                                                    currentDisplayedHistoryId = saved.id
                                                    if (sourceIsStitchable) {
                                                        stitchableHistoryIds =
                                                            stitchableHistoryIds + saved.id
                                                    }
                                                    imageVersion++
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "ModelRunScreen",
                                                "Failed to save upscaled image",
                                                e,
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    msgUpscaleFailed.format(e.message ?: "Unknown error"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                isUpscaling = false
                            }
                        }
                    }
                } else if (selectedUpscaler != null) {
                    Toast.makeText(
                        context,
                        msgDownloadModelFirst,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDownload = { upscaler ->
                downloadingUpscalerId = upscaler.id
                downloadProgress = null
                upscaler.startDownload(context)
            },
        )
    }

    BlockingProgressOverlay(visible = isCheckingBackend) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading_model),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    BlockingProgressOverlay(visible = isUpscaling) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.upscaling_image),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (showHistoryFilterSheet) {
        HistoryFilterSheet(
            initialFilter = historyFilter,
            knownModelIds = knownModelIds,
            knownSchedulers = knownSchedulers,
            knownSizes = knownSizes,
            onApply = {
                historyFilter = it
                showHistoryFilterSheet = false
            },
            onDismiss = { showHistoryFilterSheet = false },
        )
    }

    // History detail dialog
    if (showHistoryDetailDialog && selectedHistoryItem != null) {
        val historyBitmap = remember(selectedHistoryItem?.imageFile?.absolutePath) {
            BitmapFactory.decodeFile(selectedHistoryItem!!.imageFile.absolutePath)
        }
        val dismissDetail = {
            showHistoryDetailDialog = false
            selectedHistoryItem = null
        }
        ZoomableImageOverlay(
            bitmap = historyBitmap,
            onDismiss = dismissDetail,
            topEndContent = {
                OverlayIconButton(
                    icon = Icons.Default.Info,
                    contentDescription = "View parameters",
                    onClick = {
                        if (selectedHistoryItem != null) {
                            showHistoryParametersDialog = true
                        }
                    },
                )
                OverlayIconButton(
                    icon = Icons.Default.Save,
                    contentDescription = "Save to gallery",
                    onClick = {
                        if (historyBitmap != null) {
                            scope.launch {
                                saveImage(
                                    context = context,
                                    bitmap = historyBitmap,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            msgImageSaved,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    onError = { errorMsg ->
                                        Toast.makeText(
                                            context,
                                            errorMsg,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    },
                )
            },
        )
    }

    // History parameters dialog
    if (showHistoryParametersDialog && selectedHistoryItem != null) {
        val params = selectedHistoryItem!!.params
        GenerationParamsDialog(
            title = stringResource(R.string.generation_params_title),
            params = params,
            modelId = selectedHistoryItem?.modelId ?: "",
            displayMode = selectedHistoryItem?.mode,
            showImg2imgButton = useImg2img,
            onShare = {
                shareSourceParams = params
                shareSourceModelId = selectedHistoryItem?.modelId
            },
            onSendToImg2img = {
                val item = selectedHistoryItem
                if (item != null) {
                    val bmp = BitmapFactory.decodeFile(item.imageFile.absolutePath)
                    if (bmp != null) {
                        sendBitmapToImg2img(bmp)
                        showHistoryParametersDialog = false
                        showHistoryDetailDialog = false
                        selectedHistoryItem = null
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to load image",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onReproduce = {
                pendingReproduceParams = selectedHistoryItem!!.params
                showHistoryParametersDialog = false
                showReproduceParamsDialog = true
            },
            onDismiss = { showHistoryParametersDialog = false },
        )
    }

    // Reproduce parameters dialog
    if (showReproduceParamsDialog && pendingReproduceParams != null) {
        val params = pendingReproduceParams!!
        ReproduceParametersDialog(
            params = params,
            onApply = { selectedFields ->
                if (ParamShareField.PROMPT in selectedFields) {
                    prompt = params.prompt
                    promptFieldValue = TextFieldValue(prompt, TextRange(prompt.length))
                    promptSuggestions = emptyList()
                }
                if (ParamShareField.NEGATIVE_PROMPT in selectedFields) {
                    negativePrompt = params.negativePrompt
                    negativePromptFieldValue =
                        TextFieldValue(negativePrompt, TextRange(negativePrompt.length))
                    negativePromptSuggestions = emptyList()
                }
                if (ParamShareField.STEPS in selectedFields) {
                    steps = params.steps.toFloat()
                }
                if (ParamShareField.CFG in selectedFields) {
                    cfg = params.cfg
                }
                if (ParamShareField.SEED in selectedFields) {
                    seed = params.seed?.toString() ?: ""
                }
                if (ParamShareField.SCHEDULER in selectedFields) {
                    scheduler = params.scheduler
                }
                if (ParamShareField.DENOISE_STRENGTH in selectedFields) {
                    denoiseStrength = params.denoiseStrength
                }
                if (model?.isSdxl == true && useImg2img) {
                    val newRatio = inferAspectRatioString(params.width, params.height)
                    if (newRatio != aspectRatio) {
                        aspectRatio = newRatio
                        clearImg2imgState()
                    }
                }
                saveAllFields()

                showReproduceParamsDialog = false
                pendingReproduceParams = null
                showHistoryDetailDialog = false
                selectedHistoryItem = null
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
            },
            onDismiss = {
                showReproduceParamsDialog = false
                pendingReproduceParams = null
                showHistoryDetailDialog = false
                selectedHistoryItem = null
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteHistoryDialog && selectedHistoryItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteHistoryDialog = false },
            title = { Text(stringResource(R.string.delete_image)) },
            text = { Text(stringResource(R.string.delete_image_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = historyManager.deleteHistoryItem(
                                item = selectedHistoryItem!!,
                            )
                            if (success) {
                                showDeleteHistoryDialog = false
                                showHistoryDetailDialog = false
                                selectedHistoryItem = null
                                Toast.makeText(
                                    context,
                                    msgDeleted,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    msgDeleteFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Batch save confirmation dialog
    if (showBatchSaveDialog && selectedItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchSaveDialog = false },
            title = { Text(stringResource(R.string.batch_save)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.batch_save_confirm,
                        selectedItems.size,
                        selectedItems.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val items = selectedItems.toList()
                        showBatchSaveDialog = false
                        if (items.isEmpty()) return@TextButton
                        batchSaveTotal = items.size
                        batchSaveCurrent = 0
                        batchSaveFailed = 0
                        isBatchSaving = true
                        scope.launch(Dispatchers.IO) {
                            items.forEach { item ->
                                var success = false
                                if (item.imageFile.exists()) {
                                    saveImageFromFile(
                                        context = context,
                                        sourceFile = item.imageFile,
                                        onSuccess = { success = true },
                                        onError = { },
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    batchSaveCurrent += 1
                                    if (!success) batchSaveFailed += 1
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val total = batchSaveTotal
                                val failed = batchSaveFailed
                                val saved = total - failed
                                val message = if (failed == 0) {
                                    resources.getQuantityString(
                                        R.plurals.saved_count,
                                        saved,
                                        saved,
                                    )
                                } else {
                                    msgSavedCountWithFailed.format(saved, failed)
                                }
                                Toast.makeText(
                                    context,
                                    message,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                isBatchSaving = false
                                selectedItems.clear()
                                isSelectionMode = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Batch save progress dialog (modal — blocks other interactions)
    if (isBatchSaving) {
        AlertDialog(
            onDismissRequest = { /* not dismissable */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text(stringResource(R.string.batch_save)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            R.string.batch_saving_progress,
                            batchSaveCurrent,
                            batchSaveTotal,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val saveProgress = if (batchSaveTotal > 0) {
                        batchSaveCurrent.toFloat() / batchSaveTotal
                    } else {
                        0f
                    }
                    SmoothLinearWavyProgressIndicator(
                        progress = saveProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteDialog && selectedItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.batch_delete_confirm,
                        selectedItems.size,
                        selectedItems.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val itemsToDelete = selectedItems.toList()
                            var successCount = 0
                            var failCount = 0

                            itemsToDelete.forEach { item ->
                                val success = historyManager.deleteHistoryItem(
                                    item = item,
                                )
                                if (success) {
                                    successCount++
                                } else {
                                    failCount++
                                }
                            }

                            selectedItems.clear()
                            isSelectionMode = false
                            showBatchDeleteDialog = false

                            val message = if (failCount == 0) {
                                resources.getQuantityString(
                                    R.plurals.deleted_count,
                                    successCount,
                                    successCount,
                                )
                            } else {
                                msgDeletedCountWithFailed.format(successCount, failCount)
                            }
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Detect shared params on the clipboard once the model is ready.
    LaunchedEffect(backendState, hasInitialized) {
        if (!clipboardImportChecked &&
            hasInitialized &&
            backendState is BackendService.BackendState.Running
        ) {
            clipboardImportChecked = true
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val raw = clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            ParamShare.tryDecode(raw)?.let { pendingImport = it }
        }
    }

    // Share parameters dialog
    shareSourceParams?.let { source ->
        val available = remember(source) {
            val list = mutableListOf<ParamShareField>()
            list += ParamShareField.PROMPT
            list += ParamShareField.NEGATIVE_PROMPT
            list += ParamShareField.STEPS
            list += ParamShareField.CFG
            if (source.seed != null) list += ParamShareField.SEED
            list += ParamShareField.SCHEDULER
            if (source.mode != GenerationMode.UNKNOWN &&
                source.mode != GenerationMode.TXT2IMG
            ) {
                list += ParamShareField.DENOISE_STRENGTH
            }
            list
        }
        ShareParametersDialog(
            availableFields = available,
            fieldPreview = { field ->
                when (field) {
                    ParamShareField.PROMPT -> source.prompt

                    ParamShareField.NEGATIVE_PROMPT -> source.negativePrompt

                    ParamShareField.STEPS -> source.steps.toString()

                    ParamShareField.CFG -> "%.1f".format(source.cfg)

                    ParamShareField.SEED -> source.seed?.toString()

                    ParamShareField.SCHEDULER -> when (source.scheduler) {
                        "dpm" -> "DPM++ 2M"
                        "dpm_karras" -> "DPM++ 2M Karras"
                        "euler_a" -> "Euler A"
                        "euler_a_karras" -> "Euler A Karras"
                        "lcm" -> "LCM"
                        "euler" -> "Euler"
                        "euler_karras" -> "Euler Karras"
                        "dpm_sde" -> "DPM++ 2M SDE"
                        "dpm_sde_karras" -> "DPM++ 2M SDE Karras"
                        else -> source.scheduler
                    }

                    ParamShareField.DENOISE_STRENGTH ->
                        "%.2f".format(source.denoiseStrength)

                    ParamShareField.MODE -> source.mode.name.lowercase()
                }
            },
            useBase64Initial = shareUseBase64,
            onUseBase64Changed = { value ->
                scope.launch { generationPreferences.setShareUseBase64(value) }
            },
            onConfirm = { selectedFields, useBase64 ->
                val json = ParamShare.buildJson(source, shareSourceModelId, selectedFields)
                val payload = ParamShare.encodeForClipboard(json, useBase64)
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("dreamandroid params", payload),
                )
                clipboardImportChecked = true
                shareSourceParams = null
                shareSourceModelId = null
                Toast.makeText(
                    context,
                    msgShareCopied,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = {
                shareSourceParams = null
                shareSourceModelId = null
            },
        )
    }

    // Import shared parameters dialog
    pendingImport?.let { imported ->
        val clearClipboardAction = {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? ClipboardManager
            runCatching {
                // Build.VERSION_CODES.P is API 28, minSdk = 28, so the legacy
                // setPrimaryClip(empty) fallback is unreachable.
                clipboard?.clearPrimaryClip()
            }
        }
        ImportParametersDialog(
            imported = imported,
            clearClipboardInitial = shareClearClipboardOnImport,
            onClearClipboardChanged = { value ->
                scope.launch {
                    generationPreferences.setShareClearClipboardOnImport(value)
                }
            },
            onApply = { selectedFields, clearClipboard ->
                if (ParamShareField.PROMPT in selectedFields) {
                    imported.prompt?.let {
                        prompt = it
                        promptFieldValue = TextFieldValue(it, TextRange(it.length))
                        promptSuggestions = emptyList()
                    }
                }
                if (ParamShareField.NEGATIVE_PROMPT in selectedFields) {
                    imported.negativePrompt?.let {
                        negativePrompt = it
                        negativePromptFieldValue =
                            TextFieldValue(it, TextRange(it.length))
                        negativePromptSuggestions = emptyList()
                    }
                }
                if (ParamShareField.STEPS in selectedFields) {
                    imported.steps?.let { steps = it.toFloat() }
                }
                if (ParamShareField.CFG in selectedFields) {
                    imported.cfg?.let { cfg = it }
                }
                if (ParamShareField.SEED in selectedFields) {
                    seed = imported.seed?.toString() ?: ""
                }
                if (ParamShareField.SCHEDULER in selectedFields) {
                    imported.scheduler?.let { scheduler = it }
                }
                if (ParamShareField.DENOISE_STRENGTH in selectedFields) {
                    imported.denoiseStrength?.let { denoiseStrength = it }
                }
                saveAllFields()
                if (clearClipboard) {
                    clearClipboardAction()
                }
                pendingImport = null
                Toast.makeText(
                    context,
                    msgImportApplied,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { clearClipboard ->
                if (clearClipboard) {
                    clearClipboardAction()
                }
                pendingImport = null
            },
        )
    }
}

@Composable
fun UpscalerSelectDialog(
    upscalers: List<UpscalerModel>,
    selectedUpscalerId: String?,
    downloadingUpscalerId: String?,
    downloadProgress: DownloadProgress?,
    onDismiss: () -> Unit,
    onSelectUpscaler: (String) -> Unit,
    onConfirm: () -> Unit,
    onDownload: (UpscalerModel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_upscaler_model)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(upscalers) { upscaler ->
                    UpscalerModelCard(
                        upscaler = upscaler,
                        isSelected = upscaler.id == selectedUpscalerId,
                        isDownloading = upscaler.id == downloadingUpscalerId,
                        downloadProgress = if (upscaler.id == downloadingUpscalerId) downloadProgress else null,
                        onSelect = { onSelectUpscaler(upscaler.id) },
                        onDownload = { onDownload(upscaler) },
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = selectedUpscalerId != null,
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
    )
}

@Composable
fun UpscalerModelCard(
    upscaler: UpscalerModel,
    isSelected: Boolean,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = upscaler.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = upscaler.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (!upscaler.isDownloaded) {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.download),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.download))
                    }
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Show progress bar when downloading
            if (isDownloading && downloadProgress != null) {
                SmoothLinearWavyProgressIndicator(
                    progress = downloadProgress.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PromptCountLabel(label: String, count: Int, max: Int, showCount: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        if (showCount) {
            Spacer(Modifier.width(6.dp))
            Text("$count/$max")
        }
    }
}
