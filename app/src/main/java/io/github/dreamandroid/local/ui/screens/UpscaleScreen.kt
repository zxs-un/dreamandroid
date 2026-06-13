package io.github.dreamandroid.local.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import io.github.dreamandroid.local.BuildConfig
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.service.UpscaleBackendManager
import io.github.dreamandroid.local.ui.components.BlockingProgressOverlay
import io.github.dreamandroid.local.ui.components.ErrorMessageCard
import io.github.dreamandroid.local.ui.components.SmoothCircularWavyProgressIndicator
import io.github.dreamandroid.local.ui.theme.Motion
import io.github.dreamandroid.local.utils.performUpscale
import io.github.dreamandroid.local.utils.saveImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpscaleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var upscaledImageUri by remember { mutableStateOf<Uri?>(null) }
    var upscaledBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isUpscaling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentLog by remember { mutableStateOf("") }
    var tileProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val tileRegex = remember { Regex("""Processed tile (\d+)/(\d+)""") }

    var sharedScale by remember { mutableFloatStateOf(1f) }
    var sharedOffsetX by remember { mutableFloatStateOf(0f) }
    var sharedOffsetY by remember { mutableFloatStateOf(0f) }

    // Use shared UpscaleBackendManager state
    val upscaleBackendState by UpscaleBackendManager.state.collectAsState()
    val isUpscaleBackendRunning = upscaleBackendState is UpscaleBackendManager.State.Running
    val loadedUpscalerId = (upscaleBackendState as? UpscaleBackendManager.State.Running)?.upscalerId

    // String resources hoisted to composable scope.
    val msgImageResolutionTooLarge = stringResource(R.string.image_resolution_too_large)
    val msgFailedToLoadImage = stringResource(R.string.failed_to_load_image)
    val msgImageSaved = stringResource(R.string.image_saved)
    val msgUpscaleFailed = stringResource(R.string.upscale_failed)
    val msgUpscaleModelNotLoaded = stringResource(R.string.upscale_model_not_loaded)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }

                    if (bitmap != null) {
                        val totalPixels = bitmap.width.toLong() * bitmap.height.toLong()
                        val maxPixels = 2048L * 2048L
                        val enforceMaxPixels = BuildConfig.FLAVOR == "filter"

                        if (enforceMaxPixels && totalPixels > maxPixels) {
                            withContext(Dispatchers.Main) {
                                errorMessage = msgImageResolutionTooLarge.format(
                                    bitmap.width,
                                    bitmap.height,
                                )
                            }
                        } else {
                            selectedImageUri = it
                            selectedBitmap = bitmap
                            withContext(Dispatchers.Main) {
                                sharedScale = 1f
                                sharedOffsetX = 0f
                                sharedOffsetY = 0f
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UpscaleScreen", "Failed to load image", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = msgFailedToLoadImage.format(e.message ?: "")
                    }
                }
            }
        }
    }

    // Clean temp files on entry
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles { file ->
                    file.name.startsWith("upscaled_temp_") && file.name.endsWith(".jpg")
                }?.forEach { file ->
                    if (file.delete()) {
                        Log.d("UpscaleScreen", "Deleted temp file: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("UpscaleScreen", "Failed to clean temp files", e)
            }
        }
    }

    // ---- UI Content ----
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (selectedImageUri == null) {
                                    Modifier.clickable { imagePickerLauncher.launch("image/*") }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedImageUri == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp),
                            ) {
                                val iconAlpha = remember { Animatable(0.4f) }
                                LaunchedEffect(Unit) {
                                    iconAlpha.animateTo(
                                        targetValue = 0.8f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_image),
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha.value),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.click_to_add_image),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            ZoomableImage(
                                imageUri = selectedImageUri,
                                contentDescription = stringResource(R.string.selected_image),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                scale = sharedScale,
                                offsetX = sharedOffsetX,
                                offsetY = sharedOffsetY,
                                onTransform = { newScale, newOffsetX, newOffsetY ->
                                    sharedScale = newScale
                                    sharedOffsetX = newOffsetX
                                    sharedOffsetY = newOffsetY
                                },
                                useOriginalSize = true,
                            )
                        }

                        if (selectedImageUri != null) {
                            FilledTonalIconButton(
                                onClick = {
                                    selectedImageUri = null
                                    selectedBitmap = null
                                    sharedScale = 1f
                                    sharedOffsetX = 0f
                                    sharedOffsetY = 0f
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_image),
                                )
                            }
                        }

                        if (selectedBitmap != null) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "${selectedBitmap!!.width} × ${selectedBitmap!!.height}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                val fabEnabled = selectedBitmap != null && !isUpscaling && isUpscaleBackendRunning && loadedUpscalerId != null
                val fabContainerColor by animateColorAsState(
                    targetValue = if (fabEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    animationSpec = tween(Motion.DurationMedium),
                    label = "FabContainerColor",
                )
                val fabContentColor by animateColorAsState(
                    targetValue = if (fabEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    animationSpec = tween(Motion.DurationMedium),
                    label = "FabContentColor",
                )
                FloatingActionButton(
                    onClick = {
                        if (fabEnabled) {
                            val upscalerId = loadedUpscalerId ?: return@FloatingActionButton
                            tileProgress = null
                            currentLog = ""
                            isUpscaling = true
                            scope.launch {
                                try {
                                    val resultBitmap = performUpscale(
                                        context = context,
                                        bitmap = selectedBitmap!!,
                                        upscalerId = upscalerId,
                                    )
                                    upscaledBitmap = resultBitmap

                                    resultBitmap.let { bmp ->
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val tempFile = File(
                                                    context.cacheDir,
                                                    "upscaled_temp_${System.currentTimeMillis()}.jpg",
                                                )
                                                tempFile.outputStream().use { out ->
                                                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                }
                                                upscaledImageUri = Uri.fromFile(tempFile)
                                            } catch (e: Exception) {
                                                Log.e("UpscaleScreen", "Failed to save temp file", e)
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
                    },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(R.string.upscale),
                    )
                }

                AnimatedVisibility(
                    visible = upscaledImageUri != null,
                    enter = fadeIn(animationSpec = Motion.Fade) +
                        expandVertically(expandFrom = Alignment.Top, animationSpec = Motion.Expand),
                    exit = fadeOut(animationSpec = Motion.FadeOut) +
                        shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = Motion.Shrink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ZoomableImage(
                                imageUri = upscaledImageUri,
                                contentDescription = stringResource(R.string.upscaled_image),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                scale = sharedScale,
                                offsetX = sharedOffsetX,
                                offsetY = sharedOffsetY,
                                onTransform = { newScale, newOffsetX, newOffsetY ->
                                    sharedScale = newScale
                                    sharedOffsetX = newOffsetX
                                    sharedOffsetY = newOffsetY
                                },
                                useOriginalSize = true,
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    upscaledBitmap?.let { bitmap ->
                                        scope.launch {
                                            saveImage(
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
                                                    errorMessage = error
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = stringResource(R.string.save_image),
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "${upscaledBitmap!!.width} × ${upscaledBitmap!!.height}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (upscaledImageUri == null) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Floating Error Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                errorMessage?.let { msg ->
                    ErrorMessageCard(
                        message = msg,
                        onDismiss = { errorMessage = null },
                    )
                }
            }
        }

        BlockingProgressOverlay(visible = isUpscaling) {
            val progress = tileProgress
            if (progress != null) {
                val (current, total) = progress
                val fraction = current.toFloat() / total
                SmoothCircularWavyProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = "${(fraction * 100).toInt()}%  $current/$total",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                CircularProgressIndicator()
                if (currentLog.isNotEmpty()) {
                    Text(
                        text = currentLog,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
    }

}

@Composable
fun ZoomableImage(
    imageUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    useOriginalSize: Boolean = false,
) {
    val context = LocalContext.current

    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var currentOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scale, offsetX, offsetY) {
        currentScale = scale
        currentOffsetX = offsetX
        currentOffsetY = offsetY
    }

    val imageRequest = remember(imageUri, useOriginalSize) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .apply {
                if (useOriginalSize) {
                    size(Size.ORIGINAL)
                    memoryCacheKey(imageUri.toString() + "_original")
                }
            }
            .build()
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)

                    val newOffsetX = currentOffsetX + pan.x
                    val newOffsetY = currentOffsetY + pan.y

                    currentScale = newScale
                    currentOffsetX = newOffsetX
                    currentOffsetY = newOffsetY

                    onTransform(newScale, newOffsetX, newOffsetY)
                }
            },
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = currentScale,
                    scaleY = currentScale,
                    translationX = currentOffsetX,
                    translationY = currentOffsetY,
                ),
            contentScale = ContentScale.Fit,
        )
    }
}
