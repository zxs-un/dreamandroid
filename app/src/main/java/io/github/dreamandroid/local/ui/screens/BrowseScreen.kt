package io.github.dreamandroid.local.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyManager = remember { HistoryManager(context) }

    val historyFilter = remember { HistoryFilter(modelIds = emptySet()) }
    val historyFlow = remember { historyManager.observe(historyFilter) }
    val historyItems by historyFlow.collectAsState(initial = emptyList())
    val knownModelIds by remember { historyManager.observeKnownModelIds() }
        .collectAsState(initial = emptyList())

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryItem>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchSaveDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showHistoryDetailDialog by remember { mutableStateOf<HistoryItem?>(null) }
    var isPreviewMode by remember { mutableStateOf(false) }

    var showFilter by remember { mutableStateOf(false) }
    var filterModelId by remember { mutableStateOf<String?>(null) }

    // Filter-related state
    val effectiveFilter = if (filterModelId != null) {
        historyFilter.copy(modelIds = setOf(filterModelId!!))
    } else {
        historyFilter
    }

    val displayItems = remember(historyItems, effectiveFilter) {
        historyItems.filter { item ->
            filterModelId == null || item.modelId == filterModelId
        }
    }

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
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            var successCount = 0
                            selectedItems.toList().forEach { item ->
                                if (historyManager.deleteHistoryItem(item)) successCount++
                            }
                            selectedItems.clear()
                            isSelectionMode = false
                            showBatchDeleteDialog = false
                            Toast.makeText(
                                context,
                                "Deleted $successCount items",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
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
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            var savedCount = 0
                            var failedCount = 0
                            selectedItems.toList().forEach { item ->
                                val bitmap = try {
                                    withContext(Dispatchers.IO) {
                                        BitmapFactory.decodeFile(item.filePath)
                                    }
                                } catch (_: Exception) { null }
                                if (bitmap != null) {
                                    val result = withContext(Dispatchers.IO) {
                                        saveBitmapToGallery(context, bitmap, item.modelId)
                                    }
                                    if (result) savedCount++ else failedCount++
                                } else {
                                    failedCount++
                                }
                            }
                            selectedItems.clear()
                            isSelectionMode = false
                            showBatchSaveDialog = false
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(
                                    R.plurals.saved_count,
                                    savedCount,
                                    savedCount,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Single item delete dialog
    showHistoryDetailDialog?.let { item ->
        var showDelete by remember { mutableStateOf(false) }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text(stringResource(R.string.delete_image)) },
                text = { Text(stringResource(R.string.delete_image_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                historyManager.deleteHistoryItem(item)
                                showDelete = false
                                showHistoryDetailDialog = null
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Detail dialog
        AlertDialog(
            onDismissRequest = { showHistoryDetailDialog = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.modelId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val bitmap = try {
                                    BitmapFactory.decodeFile(item.filePath)
                                } catch (_: Exception) { null }
                                if (bitmap != null) {
                                    saveBitmapToGallery(context, bitmap, item.modelId)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            stringResource(R.string.image_saved),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.save))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bitmap = remember(item.filePath) {
                        try {
                            BitmapFactory.decodeFile(item.filePath)
                        } catch (_: Exception) { null }
                    }
                    bitmap?.let {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.filePath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (item.params.prompt.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.image_prompt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.params.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.result_params)
                            .format(
                                item.params.steps.toString(),
                                item.params.cfg,
                                item.params.seed?.toString() ?: "-",
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${item.params.width}×${item.params.height} · ${item.params.generationTime ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDetailDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Filter chips
        if (knownModelIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filterModelId == null,
                    onClick = { filterModelId = null },
                    label = { Text(stringResource(R.string.history_filter_all)) },
                )
                knownModelIds.forEach { id ->
                    FilterChip(
                        selected = filterModelId == id,
                        onClick = { filterModelId = id },
                        label = { Text(id) },
                    )
                }
            }
        }

        if (displayItems.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ImageSearch,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_generated_images),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.no_generated_images_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            // Selection mode top bar
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.selected_items_count,
                            selectedItems.size,
                            selectedItems.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showBatchSaveDialog = true }) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.save))
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedItems.clear()
                        }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                }
                HorizontalDivider()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = displayItems,
                    key = { it.id },
                ) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedItems.contains(item)) {
                                            selectedItems.remove(item)
                                            if (selectedItems.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            selectedItems.add(item)
                                        }
                                    } else {
                                        showHistoryDetailDialog = item
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems.add(item)
                                    }
                                },
                            ),
                    ) {
                        Column {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.filePath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Generated image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        if (item.params.width > 0 && item.params.height > 0)
                                            item.params.width.toFloat() / item.params.height
                                        else 1f,
                                    ),
                                contentScale = ContentScale.Fit,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.params.prompt.isNotEmpty()) {
                                        Text(
                                            text = item.params.prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = "${item.modelId} · ${item.params.width}×${item.params.height}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = selectedItems.contains(item),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedItems.add(item)
                                            else {
                                                selectedItems.remove(item)
                                                if (selectedItems.isEmpty()) {
                                                    isSelectionMode = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Preview overlay
    showHistoryDetailDialog?.let { item ->
        if (isPreviewMode) {
            AlertDialog(
                onDismissRequest = { isPreviewMode = false },
                confirmButton = {
                    TextButton(onClick = { isPreviewMode = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                text = {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.filePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                },
            )
        }
    }
}

private suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: android.graphics.Bitmap,
    modelId: String,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            java.util.Locale.US,
        ).format(java.util.Date())
        val filename = "DreamHub_${modelId}_$timestamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.DISPLAY_NAME, filename)
                put(MediaStore.Images.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/DreamHub",
                )
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@withContext false
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES,
                ),
                "DreamHub",
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}
