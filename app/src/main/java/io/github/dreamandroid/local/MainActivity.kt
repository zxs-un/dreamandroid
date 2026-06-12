package io.github.dreamandroid.local

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.dreamandroid.local.data.*
import io.github.dreamandroid.local.navigation.BottomTab
import io.github.dreamandroid.local.service.BackendService
import io.github.dreamandroid.local.service.BackgroundGenerationService
import io.github.dreamandroid.local.ui.components.BlockingProgressOverlay
import io.github.dreamandroid.local.ui.components.SmoothLinearWavyProgressIndicator
import io.github.dreamandroid.local.ui.screens.*
import io.github.dreamandroid.local.ui.theme.DreamHubTheme
import io.github.dreamandroid.local.ui.theme.LocalThemeController
import io.github.dreamandroid.local.ui.theme.rememberThemeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Storage permission is required for saving generated images",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is required for background image generation",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED -> return
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    Toast.makeText(this, "Storage permission is needed", Toast.LENGTH_LONG).show()
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> return
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkStoragePermission()
        checkNotificationPermission()

        val app = application as DreamAndroidApplication

        setContent {
            val themeController = rememberThemeController()
            CompositionLocalProvider(LocalThemeController provides themeController) {
                DreamHubTheme(themeController.state) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        val migrationState by app.migrationState.collectAsState()
                        when (migrationState) {
                            is MigrationState.Done, is MigrationState.NotNeeded -> AppContent()
                            is MigrationState.Idle, is MigrationState.InProgress, is MigrationState.Failed ->
                                MigrationScreen(
                                    state = migrationState,
                                    onRetry = { app.retryMigration() },
                                    onSkip = { app.skipMigration() },
                                )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Shared state ----
    var selectedTab by remember { mutableStateOf(BottomTab.Models) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    val backendState by BackendService.backendState.collectAsState()
    val isModelLoaded = backendState is BackendService.BackendState.Running
    val isModelLoading = backendState is BackendService.BackendState.Starting
    val modelRepository = remember { ModelRepository(context) }
    var modelRefreshVersion by remember { mutableIntStateOf(0) }

    // ---- Generation parameters (shared between top bar and screen) ----
    val model = remember(selectedModelId) { modelRepository.models.find { it.id == selectedModelId } }
    var genPrompt by remember(selectedModelId) { mutableStateOf(model?.defaultPrompt ?: "") }
    var genNegativePrompt by remember(selectedModelId) {
        mutableStateOf(model?.defaultNegativePrompt ?: "")
    }
    var genSteps by remember { mutableFloatStateOf(20f) }
    var genCfg by remember { mutableFloatStateOf(7f) }
    var genSeed by remember { mutableStateOf("") }
    var genBatchCounts by remember { mutableIntStateOf(1) }
    var genScheduler by remember { mutableStateOf("dpm") }
    var genDenoiseStrength by remember { mutableFloatStateOf(0.6f) }
    var genUseOpenCL by remember { mutableStateOf(false) }
    var genWidth by remember(selectedModelId) {
        mutableIntStateOf(
            when {
                model?.isSdxl == true -> 1024
                model?.runOnCpu == true -> 256
                else -> 512
            }
        )
    }
    var genHeight by remember(selectedModelId) {
        mutableIntStateOf(
            when {
                model?.isSdxl == true -> 1024
                model?.runOnCpu == true -> 256
                else -> 512
            }
        )
    }

    // ---- Import dialog state ----
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var showCustomNpuModelDialog by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableStateOf("") }
    var extractByteProgress by remember { mutableStateOf<ExtractByteProgress?>(null) }

    val msgNpuModelAddedSuccess = stringResource(R.string.npu_model_added_success)
    val msgNpuModelAddFailed = stringResource(R.string.npu_model_add_failed)
    val msgModelConversionSuccess = stringResource(R.string.model_conversion_success)
    val msgModelConversionFailed = stringResource(R.string.model_conversion_failed)

    // ---- Model load/unload ----
    fun loadModel(mId: String) {
        scope.launch {
            if (backendState is BackendService.BackendState.Running) {
                context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
                context.stopService(Intent(context, BackendService::class.java).apply {
                    action = BackendService.ACTION_STOP
                })
                delay(500)
            }
            val intent = Intent(context, BackendService::class.java).apply {
                putExtra("modelId", mId)
                putExtra("width", genWidth)
                putExtra("height", genHeight)
                putExtra("use_opencl", genUseOpenCL)
            }
            context.startForegroundService(intent)
            selectedModelId = mId
            snackbarHostState.showSnackbar(context.getString(R.string.loading_model_label))
        }
    }

    fun unloadModel() {
        scope.launch {
            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
            context.stopService(Intent(context, BackendService::class.java).apply {
                action = BackendService.ACTION_STOP
            })
            selectedModelId = null
            snackbarHostState.showSnackbar(context.getString(R.string.model_unloaded))
        }
    }

    var showNoModelWarning by remember { mutableStateOf(false) }
    val generationPreferences = remember { GenerationPreferences(context) }

    // Load preferences when model changes
    LaunchedEffect(selectedModelId) {
        if (selectedModelId != null) {
            val prefs = generationPreferences.getPreferences(selectedModelId!!)
            prefs.first().let { p ->
                if (p.prompt.isNotEmpty()) genPrompt = p.prompt
                if (p.negativePrompt.isNotEmpty()) genNegativePrompt = p.negativePrompt
                if (p.steps > 0) genSteps = p.steps
                if (p.cfg > 0) genCfg = p.cfg
                if (p.seed.isNotEmpty()) genSeed = p.seed
                if (p.batchCounts > 0) genBatchCounts = p.batchCounts
                genScheduler = p.scheduler
                genDenoiseStrength = p.denoiseStrength
                genUseOpenCL = p.useOpenCL
                if (p.width > 0) genWidth = p.width
                if (p.height > 0) genHeight = p.height
            }
        }
    }

    // Dialog: no model warning
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

    // Dialog: custom model import
    if (showCustomModelDialog) {
        CustomModelDialog(
            context,
            onDismiss = { showCustomModelDialog = false },
            onModelAdded = { modelName, fileUri, clipSkip, loraFiles ->
                showCustomModelDialog = false
                scope.launch {
                    convertCustomModel(
                        context = context,
                        modelName = modelName,
                        fileUri = fileUri,
                        clipSkip = clipSkip,
                        loraFiles = loraFiles,
                        onProgress = { conversionProgress = it },
                        onStart = { isConverting = true },
                        onSuccess = {
                            isConverting = false
                            modelRepository.refreshAllModels()
                            modelRefreshVersion++
                            scope.launch {
                                snackbarHostState.showSnackbar(msgModelConversionSuccess)
                            }
                        },
                        onError = { error ->
                            isConverting = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    msgModelConversionFailed.format(error)
                                )
                            }
                        },
                    )
                }
            },
        )
    }

    // Dialog: custom NPU model import
    if (showCustomNpuModelDialog) {
        CustomNpuModelDialog(
            context,
            onDismiss = { showCustomNpuModelDialog = false },
            onModelAdded = { modelName, zipUri ->
                showCustomNpuModelDialog = false
                scope.launch {
                    extractNpuModel(
                        context = context,
                        modelName = modelName,
                        zipUri = zipUri,
                        onProgress = { conversionProgress = it },
                        onByteProgress = { extracted, total, fraction ->
                            extractByteProgress = ExtractByteProgress(extracted, total, fraction)
                        },
                        onStart = {
                            extractByteProgress = null
                            isConverting = true
                        },
                        onSuccess = {
                            isConverting = false
                            extractByteProgress = null
                            modelRepository.refreshAllModels()
                            modelRefreshVersion++
                            scope.launch {
                                snackbarHostState.showSnackbar(msgNpuModelAddedSuccess)
                            }
                        },
                        onError = { error ->
                            isConverting = false
                            extractByteProgress = null
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    msgNpuModelAddFailed.format(error)
                                )
                            }
                        },
                    )
                }
            },
        )
    }

    // Conversion overlay
    BlockingProgressOverlay(
        visible = isConverting,
    ) {
        SmoothLinearWavyProgressIndicator(
            progress = extractByteProgress?.fraction ?: 0f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = conversionProgress.ifEmpty { stringResource(R.string.preparing_model) },
            style = MaterialTheme.typography.bodyMedium,
        )
        extractByteProgress?.let { bp ->
            LinearProgressIndicator(
                progress = { bp.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${bp.extractedBytes / 1024} KB / ${bp.totalCompressedBytes / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ---- Drawer content ----
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
                HorizontalDivider()
                AppSettingsDrawerContent(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                when (selectedTab) {
                    BottomTab.Models -> ModelsTopBar(
                        drawerState = drawerState,
                        selectedModelId = selectedModelId,
                        isModelLoaded = isModelLoaded,
                        isModelLoading = isModelLoading,
                        onLoadModel = { loadModel(it) },
                        onUnloadModel = { unloadModel() },
                    )
                    BottomTab.Generate -> GenerateTopBar(
                        drawerState = drawerState,
                        modelId = selectedModelId,
                        isModelLoaded = isModelLoaded,
                        prompt = genPrompt,
                        negativePrompt = genNegativePrompt,
                        steps = genSteps,
                        cfg = genCfg,
                        seed = genSeed,
                        width = genWidth,
                        height = genHeight,
                        scheduler = genScheduler,
                        denoiseStrength = genDenoiseStrength,
                        useOpenCL = genUseOpenCL,
                    )
                    BottomTab.Upscale -> UpscaleTopBar(drawerState = drawerState)
                    BottomTab.Browse -> BrowseTopBar(drawerState = drawerState)
                }
            },
            bottomBar = {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, stringResource(tab.labelResId)) },
                            label = { Text(stringResource(tab.labelResId)) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when (selectedTab) {
                    BottomTab.Models -> ModelListTab(
                        selectedModelId = selectedModelId,
                        isModelLoaded = isModelLoaded,
                        onSelectModel = { selectedModelId = it },
                        onLoadModel = { loadModel(it) },
                        modelRepository = modelRepository,
                        refreshVersion = modelRefreshVersion,
                        onImportModel = { showCustomModelDialog = true },
                        onImportNpuModel = { showCustomNpuModelDialog = true },
                    )
                    BottomTab.Generate -> TabGenerateScreen(
                        modelId = if (isModelLoaded) selectedModelId else null,
                        prompt = genPrompt,
                        onPromptChange = { genPrompt = it },
                        negativePrompt = genNegativePrompt,
                        onNegativePromptChange = { genNegativePrompt = it },
                        steps = genSteps,
                        onStepsChange = { genSteps = it },
                        cfg = genCfg,
                        onCfgChange = { genCfg = it },
                        seed = genSeed,
                        onSeedChange = { genSeed = it },
                        batchCounts = genBatchCounts,
                        onBatchCountsChange = { genBatchCounts = it },
                        scheduler = genScheduler,
                        onSchedulerChange = { genScheduler = it },
                        denoiseStrength = genDenoiseStrength,
                        onDenoiseStrengthChange = { genDenoiseStrength = it },
                        useOpenCL = genUseOpenCL,
                        onUseOpenCLChange = { genUseOpenCL = it },
                        width = genWidth,
                        onWidthChange = { genWidth = it },
                        height = genHeight,
                        onHeightChange = { genHeight = it },
                    )
                    BottomTab.Upscale -> UpscaleScreen()
                    BottomTab.Browse -> BrowseScreen()
                }
            }
        }
    }
}

// =========== Top App Bars ===========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsTopBar(
    drawerState: DrawerState,
    selectedModelId: String?,
    isModelLoaded: Boolean,
    isModelLoading: Boolean,
    onLoadModel: (String) -> Unit,
    onUnloadModel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(stringResource(R.string.nav_models)) },
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, stringResource(R.string.settings))
            }
        },
        actions = {
            if (selectedModelId != null) {
                if (isModelLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (isModelLoaded) {
                    TextButton(onClick = onUnloadModel) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.unload_model))
                    }
                } else {
                    TextButton(onClick = { onLoadModel(selectedModelId) }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.load_model))
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateTopBar(
    drawerState: DrawerState,
    modelId: String?,
    isModelLoaded: Boolean,
    prompt: String,
    negativePrompt: String,
    steps: Float,
    cfg: Float,
    seed: String,
    width: Int,
    height: Int,
    scheduler: String,
    denoiseStrength: Float,
    useOpenCL: Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val isRunning = serviceState is BackgroundGenerationService.GenerationState.Progress
    val modelRepository = remember { ModelRepository(context) }
    val model = remember(modelId) { modelRepository.models.find { it.id == modelId } }

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

    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.nav_generate), maxLines = 1)
                if (isModelLoaded && model != null) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, stringResource(R.string.settings))
            }
        },
        actions = {
            TextButton(
                onClick = {
                    if (!isModelLoaded || modelId == null) {
                        showNoModelWarning = true
                    } else {
                        scope.launch {
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
                                putExtra("aspect_ratio", "1:1")
                            }
                            context.startForegroundService(intent)
                        }
                    }
                },
                enabled = !isRunning,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.generating))
                } else {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.generate_image))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(stringResource(R.string.nav_browse)) },
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, stringResource(R.string.settings))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpscaleTopBar(drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(stringResource(R.string.image_upscale)) },
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, stringResource(R.string.settings))
            }
        },
    )
}

// =========== Models Tab ===========

@Composable
private fun ModelListTab(
    selectedModelId: String?,
    isModelLoaded: Boolean,
    onSelectModel: (String) -> Unit,
    onLoadModel: (String) -> Unit,
    modelRepository: ModelRepository,
    refreshVersion: Int,
    onImportModel: () -> Unit,
    onImportNpuModel: () -> Unit,
) {
    // Only show custom (imported) models
    val customModels = remember(modelRepository.models, refreshVersion) {
        modelRepository.models.filter { it.isCustom }
    }

    if (customModels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    stringResource(R.string.select_model_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportModel) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.import_model))
                    }
                    if (Model.isQualcommDevice()) {
                        OutlinedButton(onClick = onImportNpuModel) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.import_npu_model))
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onImportModel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.import_model))
                    }
                    if (Model.isQualcommDevice()) {
                        OutlinedButton(
                            onClick = onImportNpuModel,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.import_npu_model))
                        }
                    }
                }
            }

            items(
                items = customModels,
                key = { "${it.id}_$refreshVersion" },
            ) { model ->
                ModelSelectCard(
                    model = model,
                    isSelected = selectedModelId == model.id,
                    isActive = isModelLoaded && selectedModelId == model.id,
                    onSelect = { onSelectModel(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelSelectCard(
    model: Model,
    isSelected: Boolean,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    val targetContainer = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(300),
        label = "CardBg",
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    isActive -> Icons.Default.PlayCircle
                    isSelected -> Icons.Default.CheckCircle
                    else -> Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

// =========== Generate Tab (parameterized wrapper) ===========

@Composable
private fun TabGenerateScreen(
    modelId: String?,
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
    // Reuse the GenerateScreen from the separate file, with all parameters
    GenerateScreen(
        modelId = modelId,
        prompt = prompt,
        onPromptChange = onPromptChange,
        negativePrompt = negativePrompt,
        onNegativePromptChange = onNegativePromptChange,
        steps = steps,
        onStepsChange = onStepsChange,
        cfg = cfg,
        onCfgChange = onCfgChange,
        seed = seed,
        onSeedChange = onSeedChange,
        batchCounts = batchCounts,
        onBatchCountsChange = onBatchCountsChange,
        scheduler = scheduler,
        onSchedulerChange = onSchedulerChange,
        denoiseStrength = denoiseStrength,
        onDenoiseStrengthChange = onDenoiseStrengthChange,
        useOpenCL = useOpenCL,
        onUseOpenCLChange = onUseOpenCLChange,
        width = width,
        onWidthChange = onWidthChange,
        height = height,
        onHeightChange = onHeightChange,
    )
}

// =========== Settings Drawer ===========

@Composable
private fun ColumnScope.AppSettingsDrawerContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeController = LocalThemeController.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Appearance
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)

        var dynamicColor by remember { mutableStateOf(themeController.state.dynamicColor) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dynamic_color))
                Text(
                    stringResource(R.string.dynamic_color_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = dynamicColor, onCheckedChange = { checked ->
                dynamicColor = checked
                themeController.update { it.copy(dynamicColor = checked) }
            })
        }

        var darkMode by remember { mutableStateOf(themeController.state.darkMode) }
        Text(stringResource(R.string.dark_mode))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DarkModePreference.SYSTEM to stringResource(R.string.dark_mode_system),
                DarkModePreference.LIGHT to stringResource(R.string.dark_mode_light),
                DarkModePreference.DARK to stringResource(R.string.dark_mode_dark),
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = darkMode == mode,
                    onClick = {
                        darkMode = mode
                        themeController.update { it.copy(darkMode = mode) }
                    },
                    label = { Text(label) },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.must_read),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
}
