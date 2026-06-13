package io.github.dreamandroid.local.service

import android.graphics.Bitmap
import io.github.dreamandroid.local.data.BatchGroupDisplay
import io.github.dreamandroid.local.data.GenerationTask
import io.github.dreamandroid.local.data.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class QueueRepository {
    private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())
    val tasks: StateFlow<List<GenerationTask>> = _tasks

    private val _processingActive = MutableStateFlow(false)
    val processingActive: StateFlow<Boolean> = _processingActive

    fun setProcessingActive(active: Boolean) {
        _processingActive.value = active
    }

    fun addBatch(
        modelId: String,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        seed: String,
        width: Int,
        height: Int,
        effectiveWidth: Int,
        effectiveHeight: Int,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String,
        aspectRatio: String,
        count: Int,
    ): String {
        val batchGroupId = UUID.randomUUID().toString()
        val seedLong = seed.toLongOrNull()
        val newTasks = (0 until count).map { i ->
            GenerationTask(
                id = UUID.randomUUID().toString(),
                batchGroupId = batchGroupId,
                batchIndex = i,
                modelId = modelId,
                prompt = prompt,
                negativePrompt = negativePrompt,
                steps = steps,
                cfg = cfg,
                seed = seedLong,
                width = width,
                height = height,
                effectiveWidth = effectiveWidth,
                effectiveHeight = effectiveHeight,
                denoiseStrength = denoiseStrength,
                useOpenCL = useOpenCL,
                scheduler = scheduler,
                aspectRatio = aspectRatio,
            )
        }
        _tasks.value = _tasks.value + newTasks
        return batchGroupId
    }

    fun removeTask(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    fun removeBatch(batchGroupId: String) {
        _tasks.value = _tasks.value.filterNot { it.batchGroupId == batchGroupId }
    }

    fun updateTask(id: String, update: (GenerationTask) -> GenerationTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) update(it) else it }
    }

    fun markTaskProcessing(id: String) {
        updateTask(id) { it.copy(status = TaskStatus.PROCESSING) }
    }

    fun markTaskComplete(id: String, bitmap: Bitmap?, seed: Long?) {
        updateTask(id) {
            it.copy(
                status = TaskStatus.COMPLETED,
                resultBitmap = bitmap,
                resultSeed = seed,
            )
        }
    }

    fun markTaskError(id: String, message: String) {
        updateTask(id) {
            it.copy(
                status = TaskStatus.ERROR,
                errorMessage = message,
            )
        }
    }

    fun updateTaskProgress(id: String, progress: Float) {
        updateTask(id) { it.copy(progress = progress) }
    }

    fun cancelAllPending() {
        _tasks.value = _tasks.value.map { task ->
            if (task.status == TaskStatus.PENDING) {
                task.copy(status = TaskStatus.CANCELLED)
            } else task
        }
    }

    fun getNextPending(): GenerationTask? {
        return _tasks.value.firstOrNull { it.status == TaskStatus.PENDING }
    }

    fun hasPendingTasks(): Boolean {
        return _tasks.value.any { it.status == TaskStatus.PENDING }
    }

    /** Build collapsed batch groups for display */
    fun getBatchGroups(): List<BatchGroupDisplay> {
        val grouped = _tasks.value.groupBy { it.batchGroupId }
        return grouped.map { (groupId, tasks) ->
            val sorted = tasks.sortedBy { it.batchIndex }
            BatchGroupDisplay(
                batchGroupId = groupId,
                tasks = sorted,
                prompt = sorted.firstOrNull()?.prompt ?: "",
                count = tasks.size,
            )
        }
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filterNot { it.status == TaskStatus.COMPLETED }
    }
}
