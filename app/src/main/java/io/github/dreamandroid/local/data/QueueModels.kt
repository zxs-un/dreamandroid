package io.github.dreamandroid.local.data

import android.graphics.Bitmap

enum class TaskStatus { PENDING, PROCESSING, COMPLETED, ERROR, CANCELLED }

data class GenerationTask(
    val id: String,
    val batchGroupId: String,
    val batchIndex: Int,
    val modelId: String,
    val prompt: String,
    val negativePrompt: String,
    val steps: Int,
    val cfg: Float,
    val seed: Long?,
    val width: Int,
    val height: Int,
    val effectiveWidth: Int,
    val effectiveHeight: Int,
    val denoiseStrength: Float,
    val useOpenCL: Boolean,
    val scheduler: String,
    val aspectRatio: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val resultBitmap: Bitmap? = null,
    val resultSeed: Long? = null,
    val errorMessage: String? = null,
    val progress: Float = 0f,
) {
    /** Display-friendly status label */
    val statusLabel: String
        get() = when (status) {
            TaskStatus.PENDING -> "PENDING"
            TaskStatus.PROCESSING -> "PROCESSING"
            TaskStatus.COMPLETED -> "COMPLETED"
            TaskStatus.ERROR -> "ERROR"
            TaskStatus.CANCELLED -> "CANCELLED"
        }
}

/** Collapsed view of a batch group */
data class BatchGroupDisplay(
    val batchGroupId: String,
    val tasks: List<GenerationTask>,
    val prompt: String,
    val count: Int,
    val isExpanded: Boolean = false,
)
