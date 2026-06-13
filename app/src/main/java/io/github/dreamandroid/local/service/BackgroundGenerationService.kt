package io.github.dreamandroid.local.service

import android.app.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import io.github.dreamandroid.local.R
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BackgroundGenerationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val CHANNEL_ID = "image_generation_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "stop_generation"

        // Retry configuration
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1500L
        const val BACKEND_HEALTH_CHECK_TIMEOUT_MS = 3000L

        // Defaults — actual values read from SharedPreferences("app_prefs")
        private const val DEFAULT_SERVICE_WAIT_S = 60
        private const val DEFAULT_BITMAP_CONSUMED_S = 30
        private const val DEFAULT_HEALTH_CHECK_RETRY_INTERVAL_S = 20
        private const val DEFAULT_HEALTH_CHECK_MAX_FAILURES = 4

        /**
         * Read the timeout/interval values from user-configurable preferences.
         * All values are stored in seconds and converted to milliseconds where needed.
         */
        fun getServiceWaitTimeoutMs(context: Context): Long =
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getInt("generation_timeout_s", DEFAULT_SERVICE_WAIT_S).toLong() * 1000

        fun getBitmapConsumedTimeoutMs(context: Context): Long =
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getInt("bitmap_consumed_timeout_s", DEFAULT_BITMAP_CONSUMED_S).toLong() * 1000

        fun getHealthCheckRetryIntervalMs(context: Context): Long =
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getInt("health_check_retry_interval_s", DEFAULT_HEALTH_CHECK_RETRY_INTERVAL_S).toLong() * 1000

        fun getHealthCheckMaxFailures(context: Context): Int =
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getInt("health_check_max_failures", DEFAULT_HEALTH_CHECK_MAX_FAILURES)

        /**
         * Shared OkHttpClient reused across all generation requests.
         * A single connection pool avoids accumulation across batch iterations.
         */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(3600))
                .readTimeout(Duration.ofSeconds(3600))
                .writeTimeout(Duration.ofSeconds(3600))
                .callTimeout(Duration.ofSeconds(3600))
                .retryOnConnectionFailure(true)
                // Aggressively evict idle connections so the backend
                // doesn't accumulate stale sockets across batch items.
                .connectionPool(
                    okhttp3.ConnectionPool(
                        maxIdleConnections = 2,
                        keepAliveDuration = 1,
                        timeUnit = TimeUnit.SECONDS,
                    ),
                )
                .build()
        }

        private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
        val generationState: StateFlow<GenerationState> = _generationState

        private val _bitmapConsumed = MutableStateFlow(false)

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        /**
         * Flag indicating the user has requested a stop.
         * The in-flight generation checks this to discard results and abort early.
         */
        private val _stopRequested = MutableStateFlow(false)

        /** Reset all state to a clean baseline. Atomic — always succeeds. */
        fun resetState() {
            _generationState.value = GenerationState.Idle
            _bitmapConsumed.value = false
            // NOTE: _stopRequested is intentionally NOT cleared here —
            // it is managed by the service lifecycle.
        }

        /**
         * Force-reset state if it appears stuck (e.g. stale Progress without activity).
         * Used by the batch loop as a safety net before starting a new iteration.
         */
        fun forceResetIfStale() {
            val current = _generationState.value
            if (current !is GenerationState.Idle) {
                Log.w("BgGenService", "Force-resetting stale state: $current")
                resetState()
            }
        }

        fun clearCompleteState() {
            if (_generationState.value is GenerationState.Complete) {
                _generationState.value = GenerationState.Idle
            }
        }

        /**
         * Mark the bitmap as consumed with confirmation.
         * Returns true if the flag was successfully flipped.
         */
        fun markBitmapConsumed(): Boolean {
            _bitmapConsumed.value = true
            // Confirm it actually took effect
            return _bitmapConsumed.value
        }

        /**
         * Verify that the backend server is reachable.
         * Returns true if the health check endpoint responds.
         */
        suspend fun checkBackendHealth(): Boolean = withContext(Dispatchers.IO) {
            try {
                val client = sharedClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(BACKEND_HEALTH_CHECK_TIMEOUT_MS))
                    .readTimeout(Duration.ofMillis(BACKEND_HEALTH_CHECK_TIMEOUT_MS))
                    .build()
                val request = Request.Builder()
                    .url("http://localhost:8081/health")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w("BgGenService", "Backend health check failed: ${e.message}")
                false
            }
        }
    }

    /** Reference to the in-flight OkHttp Call, used to cancel the HTTP request on stop. */
    @Volatile
    private var activeCall: okhttp3.Call? = null

    sealed class GenerationState {
        object Idle : GenerationState()
        /** Service has accepted the request and is preparing to send to backend. */
        object Started : GenerationState()
        data class Progress(
            val progress: Float,
            val intermediateImage: Bitmap? = null,
            val step: Int = 0,
            val totalSteps: Int = 0,
        ) : GenerationState()

        data class Complete(val bitmap: Bitmap, val seed: Long?) : GenerationState()
        data class Error(val message: String) : GenerationState()
    }

    private fun updateState(newState: GenerationState) {
        _generationState.value = newState
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("GenerationService", "service created")
        _isServiceRunning.value = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GenerationService", "service execute: ${intent?.extras}")

        startForeground(NOTIFICATION_ID, createNotification(0f))

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d("GenerationService", "service stopped by user")
                _stopRequested.value = true
                // Cancel the in-flight HTTP request immediately so the
                // blocking readLine() throws an IOException and unwinds.
                activeCall?.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val prompt = intent?.getStringExtra("prompt")
        Log.d("GenerationService", "prompt: $prompt")

        if (prompt == null) {
            Log.e("GenerationService", "empty prompt")
            stopSelf()
            return START_NOT_STICKY
        }

        // Clear stop flag when a new generation is explicitly started
        _stopRequested.value = false

        val negativePrompt = intent.getStringExtra("negative_prompt") ?: ""
        val steps = intent.getIntExtra("steps", 28)
        val cfg = intent.getFloatExtra("cfg", 7f)
        val seed = if (intent.hasExtra("seed")) intent.getLongExtra("seed", 0) else null
        val width = intent.getIntExtra("width", 512)
        val height = intent.getIntExtra("height", 512)
        // Effective dimensions = target crop size for SDXL aspect-pad mode,
        // or equal to width/height otherwise. Used for decoding progress
        // previews which the backend already crops to the visible region.
        val effectiveWidth = intent.getIntExtra("effective_width", width)
        val effectiveHeight = intent.getIntExtra("effective_height", height)
        val denoiseStrength = intent.getFloatExtra("denoise_strength", 0.6f)
        val useOpenCL = intent.getBooleanExtra("use_opencl", false)
        val scheduler = intent.getStringExtra("scheduler") ?: "dpm"
        val aspectRatio = intent.getStringExtra("aspect_ratio") ?: "1:1"

        val image = if (intent.getBooleanExtra("has_image", false)) {
            try {
                val tmpFile = File(applicationContext.filesDir, "tmp.txt")
                if (tmpFile.exists()) {
                    tmpFile.readText()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("GenerationService", "Failed to read image data", e)
                null
            }
        } else {
            null
        }
        val mask = if (intent.getBooleanExtra("has_mask", false)) {
            try {
                val maskFile = File(applicationContext.filesDir, "mask.txt")
                if (maskFile.exists()) {
                    maskFile.readText()
                } else {
                    Log.w(
                        "GenerationService",
                        "has_mask is true but mask.txt not found",
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e("GenerationService", "Failed to read mask data", e)
                null
            }
        } else {
            null
        }

        Log.d("GenerationService", "params: steps=$steps, cfg=$cfg, seed=$seed")

        // Zero-trust: always reset to a clean baseline before starting
        if (_generationState.value is GenerationState.Complete) {
            updateState(GenerationState.Idle)
        }
        _bitmapConsumed.value = false

        // Signal that the service has accepted the request (before launching async work)
        updateState(GenerationState.Started)

        serviceScope.launch {
            Log.d("GenerationService", "start generation")
            runGeneration(
                prompt,
                negativePrompt,
                steps,
                cfg,
                seed,
                width,
                height,
                effectiveWidth,
                effectiveHeight,
                image,
                mask,
                denoiseStrength,
                useOpenCL,
                scheduler,
                aspectRatio,
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun runGeneration(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        seed: Long?,
        width: Int,
        height: Int,
        effectiveWidth: Int,
        effectiveHeight: Int,
        image: String?,
        mask: String?,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String,
        aspectRatio: String,
    ) = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            if (!isActive) break
            if (attempt > 1) {
                Log.w("BgGenService", "Retry attempt $attempt/$MAX_RETRIES after ${RETRY_DELAY_MS}ms")
                // Evict stale connections before retrying so the backend
                // sees a clean socket rather than a half-closed one.
                sharedClient.connectionPool.evictAll()
                updateState(GenerationState.Progress(0f)) // Signal retry to UI
                delay(RETRY_DELAY_MS)
            }

            try {
                executeGeneration(
                    prompt, negativePrompt, steps, cfg, seed,
                    width, height, effectiveWidth, effectiveHeight,
                    image, mask, denoiseStrength, useOpenCL,
                    scheduler, aspectRatio,
                )
                // Success — exit retry loop
                return@withContext
            } catch (e: Exception) {
                lastException = e
                Log.e("BgGenService", "Generation attempt $attempt failed: ${e.message}", e)

                // Don't retry on non-recoverable errors (e.g. bad prompt, invalid params)
                if (e is IOException && (e.message?.contains("request failed") == true ||
                        e.message?.contains("no image data") == true)) {
                    Log.w("BgGenService", "Non-recoverable error, not retrying")
                    break
                }
            }
        }

        // All retries exhausted
        updateState(
            GenerationState.Error(
                lastException?.message
                    ?: this@BackgroundGenerationService.getString(R.string.unknown_error),
            ),
        )
        stopSelf()
    }

    /**
     * Execute a single generation attempt. Throws on failure so the retry loop can catch it.
     */
    private suspend fun executeGeneration(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        seed: Long?,
        width: Int,
        height: Int,
        effectiveWidth: Int,
        effectiveHeight: Int,
        image: String?,
        mask: String?,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String,
        aspectRatio: String,
    ) = withContext(Dispatchers.IO) {
            val preferences =
                applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val showProcess = preferences.getBoolean("show_diffusion_process", false)
            val showStride = preferences.getInt("show_diffusion_stride", 1)

            val jsonObject = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("steps", steps)
                put("cfg", cfg)
                put("use_cfg", true)
                put("width", width)
                put("height", height)
                put("denoise_strength", denoiseStrength)
                put("use_opencl", useOpenCL)
                put("scheduler", scheduler)
                put("show_diffusion_process", showProcess)
                put("show_diffusion_stride", showStride)
                put("aspect_ratio", aspectRatio)
                seed?.let { put("seed", it) }
                image?.let { put("image", it) }
                mask?.let { put("mask", it) }
            }

            val request = Request.Builder()
                .url("http://localhost:8081/generate")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val call = sharedClient.newCall(request)
            activeCall = call
            try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        this@BackgroundGenerationService.getString(
                            R.string.error_request_failed,
                            response.code.toString(),
                        ),
                    )
                }

                // Connection established — signal 0% progress
                updateState(GenerationState.Progress(0f))

                response.body?.let { responseBody ->
                    Log.d("BgGenService", "Reading streaming response")

                    val reader = responseBody.charStream().buffered()
                    var messageCount = 0

                    // Read line by line for efficiency.
                    // Also check _stopRequested so we abort promptly on user stop.
                    while (isActive && !_stopRequested.value) {
                        val readLineStart = System.currentTimeMillis()
                        val line = reader.readLine() ?: break
                        val readLineTime = System.currentTimeMillis() - readLineStart

                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data == "[DONE]") break

                            val jsonParseStart = System.currentTimeMillis()
                            val message = JSONObject(data)
                            val jsonParseTime = System.currentTimeMillis() - jsonParseStart
                            messageCount++

                            when (message.optString("type")) {
                                "progress" -> {
                                    val step = message.optInt("step")
                                    val totalSteps = message.optInt("total_steps")
                                    val progress = step.toFloat() / totalSteps

                                    val b64Img = message.optString("image")
                                    var bitmap: Bitmap? = null
                                    if (b64Img.isNotEmpty()) {
                                        try {
                                            val imageBytes = Base64.getDecoder().decode(b64Img)
                                            // Progress previews are cropped to (effectiveWidth,
                                            // effectiveHeight) by the backend so the SDXL aspect-pad
                                            // path doesn't ship the 1024 canvas every step.
                                            val pw = effectiveWidth
                                            val ph = effectiveHeight
                                            val pixels = IntArray(pw * ph)
                                            for (i in 0 until pw * ph) {
                                                val index = i * 3
                                                if (index + 2 < imageBytes.size) {
                                                    val r = imageBytes[index].toInt() and 0xFF
                                                    val g = imageBytes[index + 1].toInt() and 0xFF
                                                    val b = imageBytes[index + 2].toInt() and 0xFF
                                                    pixels[i] =
                                                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                                }
                                            }
                                            bitmap = createBitmap(pw, ph)
                                            bitmap.setPixels(pixels, 0, pw, 0, 0, pw, ph)
                                        } catch (e: Exception) {
                                            Log.e(
                                                "BgGenService",
                                                "Failed to decode intermediate image",
                                                e,
                                            )
                                        }
                                    }

                                    updateState(GenerationState.Progress(progress, bitmap, step, totalSteps))
                                    updateNotification(progress)
                                }

                                "complete" -> {
                                    // Discard result if user requested stop
                                    if (_stopRequested.value) {
                                        Log.d("BgGenService", "Stop requested, discarding completed generation")
                                        stopSelf()
                                        return@withContext
                                    }
                                    Log.d(
                                        "BgGenService",
                                        "=== Received complete message, parsing... ===",
                                    )
                                    Log.d(
                                        "BgGenService",
                                        "readLine took: ${readLineTime}ms, line length: ${line.length}",
                                    )
                                    Log.d(
                                        "BgGenService",
                                        "JSONObject parsing took: ${jsonParseTime}ms, data length: ${data.length}",
                                    )
                                    val completeStartTime = System.currentTimeMillis()

                                    // 1. Extract fields from JSON
                                    val extractStart = System.currentTimeMillis()
                                    val base64Image = message.optString("image")
                                    val returnedSeed =
                                        message.optLong("seed", -1).takeIf { it != -1L }
                                    val resultWidth = message.optInt("width", 512)
                                    val resultHeight = message.optInt("height", 512)
                                    Log.d(
                                        "BgGenService",
                                        "JSON extraction took: ${System.currentTimeMillis() - extractStart}ms, Base64 length: ${base64Image.length}",
                                    )

                                    if (base64Image.isNullOrEmpty()) {
                                        throw IOException("no image data")
                                    }

                                    // 2. Base64 decode
                                    val decodeStartTime = System.currentTimeMillis()
                                    val imageBytes = Base64.getDecoder().decode(base64Image)
                                    Log.d(
                                        "BgGenService",
                                        "Base64 decoding took: ${System.currentTimeMillis() - decodeStartTime}ms, decoded size: ${imageBytes.size} bytes",
                                    )

                                    // 3. RGB conversion + Bitmap creation
                                    val bitmapStartTime = System.currentTimeMillis()
                                    val bitmap = createBitmap(resultWidth, resultHeight)
                                    val pixels = IntArray(resultWidth * resultHeight)

                                    for (i in 0 until resultWidth * resultHeight) {
                                        val index = i * 3
                                        val r = imageBytes[index].toInt() and 0xFF
                                        val g = imageBytes[index + 1].toInt() and 0xFF
                                        val b = imageBytes[index + 2].toInt() and 0xFF
                                        pixels[i] =
                                            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    bitmap.setPixels(
                                        pixels,
                                        0,
                                        resultWidth,
                                        0,
                                        0,
                                        resultWidth,
                                        resultHeight,
                                    )
                                    Log.d(
                                        "BgGenService",
                                        "RGB conversion + Bitmap creation took: ${System.currentTimeMillis() - bitmapStartTime}ms",
                                    )

                                    Log.d(
                                        "BgGenService",
                                        "=== Total processing time for complete message: ${System.currentTimeMillis() - completeStartTime}ms, size: ${resultWidth}x$resultHeight ===",
                                    )

                                    updateState(
                                        GenerationState.Complete(
                                            bitmap,
                                            returnedSeed,
                                        ),
                                    )

                                    Log.d(
                                        "BgGenService",
                                        "Generation completed, waiting for UI to consume bitmap",
                                    )

                                    // Wait for UI to consume the bitmap with timeout
                                    val waitStartTime = System.currentTimeMillis()
                                    val bitmapTimeout = getBitmapConsumedTimeoutMs(applicationContext)
                                    while (!_bitmapConsumed.value && isActive) {
                                        if (System.currentTimeMillis() - waitStartTime > bitmapTimeout) {
                                            Log.w(
                                                "BgGenService",
                                                "Timeout waiting for bitmap consumption after ${bitmapTimeout}ms",
                                            )
                                            break
                                        }
                                        delay(100)
                                    }

                                    Log.d(
                                        "BgGenService",
                                        "Bitmap consumed, stopping service. Wait time: ${System.currentTimeMillis() - waitStartTime}ms",
                                    )
                                    stopSelf()
                                }

                                "error" -> {
                                    val errorMsg =
                                        message.optString("message", "unknown error")
                                    Log.e(
                                        "BgGenService",
                                        "Received error message: $errorMsg",
                                    )
                                    throw IOException(errorMsg)
                                }
                            }
                        }
                    }
                }
            }
            } catch (e: IOException) {
                Log.e("BgGenService", "Generation failed with IOException", e)
                updateState(GenerationState.Error(e.message ?: "Generation failed"))
            } catch (e: Exception) {
                Log.e("BgGenService", "Unexpected generation error", e)
                updateState(GenerationState.Error(e.message ?: "Unexpected error"))
            } finally {
                activeCall = null
            }
    }

    private fun createNotificationChannel() {
        val name = "Image Generation"
        val descriptionText = "Background image generation"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(progress: Float): Notification {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.getString(R.string.generating_notify))
            .setContentText("Progress: ${(progress * 100).toInt()}%")
            .setProgress(100, (progress * 100).toInt(), false)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Float) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(progress))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        handleTimeout(0)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        handleTimeout(fgsType)
    }

    private fun handleTimeout(fgsType: Int) {
        Log.e("GenerationService", "Foreground service timeout (fgsType=$fgsType)")
        updateState(GenerationState.Error("Service timeout"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        // Zero-trust: force-reset any non-Idle state on destroy.
        // This ensures the next service start always begins from a clean baseline.
        forceResetIfStale()

        // Clear the stop flag now that the service is fully destroyed
        _stopRequested.value = false

        // Evict idle connections so the next batch item gets a fresh socket.
        sharedClient.connectionPool.evictAll()

        _isServiceRunning.value = false
        Log.d("GenerationService", "service destroyed, isServiceRunning set to false")
    }
}
