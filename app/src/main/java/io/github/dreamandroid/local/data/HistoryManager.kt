package io.github.dreamandroid.local.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import io.github.dreamandroid.local.data.db.AppDatabase
import io.github.dreamandroid.local.data.db.HistoryEntity
import io.github.dreamandroid.local.ui.screens.GenerationParameters
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Immutable
data class HistoryItem(
    val id: Long,
    val modelId: String,
    val imageFile: File,
    val params: GenerationParameters,
    val timestamp: Long,
    val mode: GenerationMode,
    val upscalerId: String?,
) {
    companion object {
        fun fromEntity(filesDir: File, e: HistoryEntity): HistoryItem {
            val imageFile = File(filesDir, e.imagePath)
            val mode = GenerationMode.fromString(e.mode)
            return HistoryItem(
                id = e.id,
                modelId = e.modelId,
                imageFile = imageFile,
                timestamp = e.timestamp,
                mode = mode,
                upscalerId = e.upscalerId,
                params = GenerationParameters(
                    steps = e.steps,
                    cfg = e.cfg,
                    seed = e.seed,
                    prompt = e.prompt,
                    negativePrompt = e.negativePrompt,
                    generationTime = e.generationTime,
                    width = e.width,
                    height = e.height,
                    runOnCpu = e.runOnCpu,
                    denoiseStrength = e.denoiseStrength ?: 0.6f,
                    useOpenCL = e.useOpenCL,
                    scheduler = e.scheduler,
                    mode = mode,
                ),
            )
        }
    }
}

class HistoryManager(private val context: Context) {

    private val dao = AppDatabase.get(context).historyDao()
    private val filesDir: File = context.filesDir

    private fun getHistoryDir(modelId: String): File {
        val dir = File(filesDir, "history/$modelId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveGeneratedImage(
        modelId: String,
        bitmap: Bitmap,
        params: GenerationParameters,
        mode: GenerationMode,
        upscalerId: String? = null,
    ): HistoryItem? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val historyDir = getHistoryDir(modelId)

            val isUpscaled = upscalerId != null
            val ext = if (isUpscaled) "jpg" else "png"
            val imageFile = File(historyDir, "$timestamp.$ext")
            FileOutputStream(imageFile).use { out ->
                if (isUpscaled) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val relativePath = "history/$modelId/$timestamp.$ext"
            val entity = HistoryEntity(
                modelId = modelId,
                timestamp = timestamp,
                imagePath = relativePath,
                width = params.width,
                height = params.height,
                mode = mode.name,
                denoiseStrength = if (mode == GenerationMode.IMG2IMG || mode == GenerationMode.INPAINT) {
                    params.denoiseStrength
                } else {
                    null
                },
                upscalerId = upscalerId,
                steps = params.steps,
                cfg = params.cfg,
                seed = params.seed,
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                generationTime = params.generationTime,
                scheduler = params.scheduler,
                runOnCpu = params.runOnCpu,
                useOpenCL = params.useOpenCL,
            )
            val id = dao.insert(entity)
            HistoryItem.fromEntity(filesDir, entity.copy(id = id))
        } catch (e: Exception) {
            Log.e("HistoryManager", "Failed to save image", e)
            null
        }
    }

    suspend fun loadHistoryForModel(modelId: String): List<HistoryItem> = withContext(Dispatchers.IO) {
        try {
            val filter = HistoryFilter(modelIds = setOf(modelId))
            dao.queryOnce(filter.toSqlQuery())
                .map { HistoryItem.fromEntity(filesDir, it) }
        } catch (e: Exception) {
            Log.e("HistoryManager", "Failed to load history", e)
            emptyList()
        }
    }

    fun observe(filter: HistoryFilter): Flow<List<HistoryItem>> = dao.query(filter.toSqlQuery()).map { entities ->
        entities.map { HistoryItem.fromEntity(filesDir, it) }
    }

    fun observeKnownModelIds(): Flow<List<String>> = dao.observeKnownModelIds()
    fun observeKnownSchedulers(): Flow<List<String>> = dao.observeKnownSchedulers()
    fun observeKnownSizes(): Flow<List<String>> = dao.observeKnownSizes()

    suspend fun deleteHistoryItem(item: HistoryItem): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.deleteById(item.id)
            if (item.imageFile.exists()) item.imageFile.delete()
            true
        } catch (e: Exception) {
            Log.e("HistoryManager", "Failed to delete history item", e)
            false
        }
    }

    suspend fun clearHistoryForModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.deleteAllForModel(modelId)
            File(filesDir, "history/$modelId").deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e("HistoryManager", "Failed to clear history", e)
            false
        }
    }
}
