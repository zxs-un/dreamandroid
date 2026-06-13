package io.github.dreamandroid.local.service.queue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.dreamandroid.local.DreamAndroidApplication
import io.github.dreamandroid.local.R
import io.github.dreamandroid.local.core.error.AppError
import io.github.dreamandroid.local.core.model.GenerateParams
import io.github.dreamandroid.local.data.GenerationMode
import io.github.dreamandroid.local.data.HistoryManager
import io.github.dreamandroid.local.service.QueueRepository
import io.github.dreamandroid.local.ui.screens.GenerationParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent Foreground Service that sequentially processes the generation queue.
 *
 * Replaces the per-task BackgroundGenerationService pattern.
 * Lifecycle: runs while PENDING tasks exist, stopSelf() when queue is empty.
 *
 * Architecture: Service → BackendManager → SseStreamParser → QueueRepository/HistoryManager
 * Communication: StateFlow fields observed by QueueViewModel → QueueScreen
 */
class QueueProcessingService : Service() {

    companion object {
        private const val TAG = "QueueProcService"
        private const val CHANNEL_ID = "queue_processing_channel"
        private const val NOTIFICATION_ID = 5
        const val ACTION_STOP = "io.github.dreamandroid.local.STOP_QUEUE"
    }

    // ── Dependencies (via Application) ──

    private val backendManager get() = (application as DreamAndroidApplication).backendManager
    private val historyManager by lazy { HistoryManager(applicationContext) }

    // QueueRepository is still a plain class (not in Application DI yet)
    // TODO: Move to Application DI in next phase
    private val queueRepository = QueueRepository()

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    // ── State ──

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentProgress = MutableStateFlow(0f)
    val currentProgress: StateFlow<Float> = _currentProgress.asStateFlow()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processingJob: Job? = null

    // ── Service Lifecycle ──

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested by user")
                processingJob?.cancel()
                _isProcessing.value = false
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(
            NOTIFICATION_ID,
            createNotification("Idle", 0)
        )

        // Start processing loop if not already running
        if (!_isProcessing.value && queueRepository.hasPendingTasks()) {
            startProcessing()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        processingJob?.cancel()
        serviceScope.cancel()
        _isProcessing.value = false
        super.onDestroy()
    }

    // ── Processing Loop ──

    private fun startProcessing() {
        if (_isProcessing.value) return
        _isProcessing.value = true

        processingJob = serviceScope.launch {
            processLoop()
        }
    }

    private suspend fun processLoop() {
        while (coroutineContext.isActive) {
            val task = queueRepository.getNextPending()
            if (task == null) {
                Log.d(TAG, "No more pending tasks, stopping")
                _isProcessing.value = false
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return
            }

            Log.d(TAG, "Processing task: ${task.id} (${task.prompt.take(50)}...)")
            queueRepository.markTaskProcessing(task.id)
            updateNotification("Processing: ${task.prompt.take(30)}...", 0)

            // 1. Health Check with retry
            if (!backendManager.healthCheckWithRetry()) {
                Log.e(TAG, "Health check failed after retries for task ${task.id}")
                queueRepository.markTaskError(task.id, AppError.Backend("Health check failed"))
                continue
            }

            // 2. Build GenerateParams from task
            val params = GenerateParams(
                prompt = task.prompt,
                negativePrompt = task.negativePrompt,
                steps = task.steps,
                cfg = task.cfg,
                width = task.width,
                height = task.height,
                denoiseStrength = task.denoiseStrength,
                useOpenCL = task.useOpenCL,
                scheduler = task.scheduler,
                aspectRatio = task.aspectRatio,
                seed = task.seed,
            )

            // 3. Execute generation via BackendManager
            try {
                backendManager.generate(params).collect { event ->
                    when (event) {
                        is SseStreamParser.SseEvent.Progress -> {
                            val progress = event.step.toFloat() / event.totalSteps
                            _currentProgress.value = progress
                            queueRepository.updateTaskProgress(task.id, progress)
                            updateNotification(
                                "Generating: ${task.prompt.take(30)}...",
                                (progress * 100).toInt()
                            )
                        }
                        is SseStreamParser.SseEvent.Complete -> {
                            val bitmap = base64ToBitmap(
                                event.imageBase64,
                                event.width,
                                event.height
                            )
                            if (bitmap != null) {
                                // Save to history via HistoryManager (handles file + Room transaction)
                                val params = GenerationParameters(
                                    steps = task.steps,
                                    cfg = task.cfg,
                                    seed = event.seed,
                                    prompt = task.prompt,
                                    negativePrompt = task.negativePrompt,
                                    generationTime = System.currentTimeMillis().toString(),
                                    width = event.width,
                                    height = event.height,
                                    runOnCpu = false,
                                    denoiseStrength = task.denoiseStrength,
                                    useOpenCL = task.useOpenCL,
                                    scheduler = task.scheduler,
                                    mode = GenerationMode.TXT2IMG,
                                )
                                historyManager.saveGeneratedImage(
                                    modelId = task.modelId,
                                    bitmap = bitmap,
                                    params = params,
                                    mode = GenerationMode.TXT2IMG,
                                )

                                queueRepository.markTaskComplete(task.id, bitmap, event.seed)
                                updateNotification(
                                    "Complete: ${task.prompt.take(30)}...",
                                    100
                                )
                                // Bitmap will be recycled by UI after consumption
                            } else {
                                queueRepository.markTaskError(
                                    task.id,
                                    AppError.Parse("Failed to decode result bitmap")
                                )
                            }
                        }
                        is SseStreamParser.SseEvent.Error -> {
                            queueRepository.markTaskError(
                                task.id,
                                AppError.Backend(event.message)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed for task ${task.id}", e)
                queueRepository.markTaskError(task.id, AppError.from(e))
            }
        }
    }

    // ── Helpers ──

    private fun base64ToBitmap(base64: String, width: Int, height: Int): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            for (i in 0 until width * height) {
                val idx = i * 3
                if (idx + 2 < bytes.size) {
                    val r = bytes[idx].toInt() and 0xFF
                    val g = bytes[idx + 1].toInt() and 0xFF
                    val b = bytes[idx + 2].toInt() and 0xFF
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 bitmap", e)
            null
        }
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Queue Processing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background queue processing"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, progress: Int): Notification {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, QueueProcessingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DreamHub Queue")
            .setContentText(title)
            .setProgress(100, progress, progress == 0)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(text, progress))
    }
}
