package io.github.dreamandroid.local.service.backend

import android.content.Context
import android.util.Log
import io.github.dreamandroid.local.core.error.AppError
import io.github.dreamandroid.local.core.model.DreamHubConstants
import io.github.dreamandroid.local.core.model.GenerateParams
import io.github.dreamandroid.local.data.Model
import io.github.dreamandroid.local.data.ModelRepository
import io.github.dreamandroid.local.service.http.HttpClientProvider
import io.github.dreamandroid.local.service.queue.SseStreamParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Unified backend process manager.
 * Guarantees only one C++ process runs on port 8081 at any time.
 * Replaces: BackendService + UpscaleBackendManager
 */
class BackendManager(private val context: Context) {

    companion object {
        private const val TAG = "BackendManager"
    }

    enum class Mode { Diffusion, Upscaler }

    sealed class State {
        data object Idle : State()
        data class Starting(val mode: Mode, val modelId: String) : State()
        data class Running(val mode: Mode, val modelId: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Single shared OkHttpClient for all backend HTTP calls */
    val httpClient: OkHttpClient = HttpClientProvider.create()

    private var process: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public API ──

    suspend fun startDiffusion(
        modelId: String,
        width: Int,
        height: Int,
        useOpenCL: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopProcess()
            _state.value = State.Starting(Mode.Diffusion, modelId)

            val modelRepository = ModelRepository(context)
            val model = modelRepository.models.find { it.id == modelId }
                ?: return@withContext Result.failure(AppError.Backend("Model not found: $modelId"))
            val modelsDir = File(Model.getModelsDir(context), modelId)

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val executableFile = File(nativeDir, DreamHubConstants.EXECUTABLE_NAME)

            if (!executableFile.exists()) {
                val msg = "Executable not found: ${executableFile.absolutePath}"
                _state.value = State.Error(msg)
                return@withContext Result.failure(AppError.Backend(msg))
            }

            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val useImg2img = prefs.getBoolean("use_img2img", true)
            val listenOnAll = prefs.getBoolean("listen_on_all_addresses", false)

            val command = mutableListOf(
                executableFile.absolutePath,
                "--type", model.backendType,
                "--model_dir", modelsDir.absolutePath,
                "--port", DreamHubConstants.BACKEND_PORT.toString()
            )
            if (!model.runOnCpu) {
                command += listOf("--lib_dir", RuntimeDirPreparer.prepare(context).absolutePath)
            }
            if (!useImg2img) command += "--no_img2img"

            // SD1.5 NPU non-512x512 patch
            if (model.backendType == "sd15npu" && (width != 512 || height != 512)) {
                val patchFile = if (width == height) {
                    val squarePatch = File(modelsDir, "$width.patch")
                    if (squarePatch.exists()) squarePatch
                    else File(modelsDir, "${width}x$height.patch")
                } else {
                    File(modelsDir, "${width}x$height.patch")
                }
                if (patchFile.exists()) command += listOf("--patch", patchFile.absolutePath)
            }

            if (File(modelsDir, "V_PRED").exists()) command += "--use_v_pred"
            if (model.isSdxl && prefs.getBoolean("sdxl_lowram", true)) command += "--lowram"
            if (listenOnAll) command += "--listen_all"

            val env = buildLibraryPathEnv()

            Log.d(TAG, "COMMAND: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command).apply {
                directory(File(nativeDir))
                redirectErrorStream(true)
                environment().putAll(env)
            }

            process = processBuilder.start()
            startProcessMonitor()

            _state.value = State.Running(Mode.Diffusion, modelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start diffusion backend", e)
            _state.value = State.Error("Failed to start: ${e.message}")
            Result.failure(AppError.from(e))
        }
    }

    suspend fun startUpscaler(upscalerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopProcess()
            _state.value = State.Starting(Mode.Upscaler, upscalerId)

            val nativeDir = context.applicationInfo.nativeLibraryDir
            val executableFile = File(nativeDir, DreamHubConstants.EXECUTABLE_NAME)

            if (!executableFile.exists()) {
                val msg = "Executable not found"
                _state.value = State.Error(msg)
                return@withContext Result.failure(AppError.Backend(msg))
            }

            val listenOnAll = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("listen_on_all_addresses", false)

            var command = listOf(
                executableFile.absolutePath,
                "--upscaler_mode",
                "--lib_dir", RuntimeDirPreparer.prepare(context).absolutePath,
                "--port", DreamHubConstants.BACKEND_PORT.toString()
            )
            if (listenOnAll) command = command + "--listen_all"

            val env = buildLibraryPathEnv()

            Log.d(TAG, "COMMAND: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command).apply {
                directory(File(nativeDir))
                redirectErrorStream(true)
                environment().putAll(env)
            }

            process = processBuilder.start()
            startProcessMonitor()

            _state.value = State.Running(Mode.Upscaler, upscalerId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start upscaler backend", e)
            _state.value = State.Error("Failed to start upscaler: ${e.message}")
            Result.failure(AppError.from(e))
        }
    }

    /**
     * Graceful shutdown: SIGTERM → waitFor(5s) → destroyForcibly() → waitFor()
     * Process must fully exit before returning (prevents zombie processes).
     */
    suspend fun stop() {
        withContext(Dispatchers.IO) { stopProcess() }
        scope.cancel()
    }

    // ── Health Check ──

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${DreamHubConstants.BASE_URL}/health")
                .get()
                .build()

            val client = httpClient.newBuilder()
                .readTimeout(DreamHubConstants.HEALTH_CHECK_TIMEOUT_S, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun healthCheckWithRetry(
        maxRetries: Int = DreamHubConstants.DEFAULT_HEALTH_CHECK_MAX_FAILURES,
        intervalSeconds: Long = DreamHubConstants.DEFAULT_HEALTH_CHECK_RETRY_INTERVAL_S
    ): Boolean {
        repeat(maxRetries) {
            if (healthCheck()) return true
            delay(intervalSeconds * 1000)
        }
        return false
    }

    // ── Business Endpoints ──

    fun generate(params: GenerateParams): Flow<SseStreamParser.SseEvent> = flow {
        val jsonBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("steps", params.steps)
            put("cfg", params.cfg.toDouble())
            put("use_cfg", params.useCfg)
            put("width", params.width)
            put("height", params.height)
            put("denoise_strength", params.denoiseStrength.toDouble())
            put("use_opencl", params.useOpenCL)
            put("scheduler", params.scheduler)
            put("show_diffusion_process", params.showDiffusionProcess)
            put("show_diffusion_stride", params.showDiffusionStride)
            put("aspect_ratio", params.aspectRatio)
            params.seed?.let { put("seed", it) }
            params.imageBase64?.let { put("image", it) }
            params.maskBase64?.let { put("mask", it) }
        }

        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${DreamHubConstants.BASE_URL}/generate")
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw AppError.Backend("Generate request failed: ${response.code}")
        }

        val body = response.body ?: throw AppError.Backend("Empty response body")
        val parser = SseStreamParser(body.byteStream())
        parser.events().collect { emit(it) }
    }

    data class TokenizeResult(
        val count: Int,
        val maxLength: Int,
        val overflowOffset: Int,
    )

    suspend fun tokenize(prompt: String): TokenizeResult = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply { put("prompt", prompt) }
        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${DreamHubConstants.BASE_URL}/tokenize")
            .post(requestBody)
            .build()

        val client = httpClient.newBuilder()
            .readTimeout(DreamHubConstants.TOKENIZE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw AppError.Backend("Tokenize failed: ${response.code}")
        val body = response.body?.string() ?: throw AppError.Backend("Empty tokenize response")
        val json = JSONObject(body)
        TokenizeResult(
            count = json.optInt("count", 0),
            maxLength = json.optInt("max_length", 77),
            overflowOffset = json.optInt("overflow_offset", -1),
        )
    }

    suspend fun upscale(
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        upscalerPath: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val requestBody = rgbBytes.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url("${DreamHubConstants.BASE_URL}/upscale")
            .header("X-Image-Width", width.toString())
            .header("X-Image-Height", height.toString())
            .header("X-Upscaler-Path", upscalerPath)
            .post(requestBody)
            .build()

        val client = httpClient.newBuilder()
            .readTimeout(DreamHubConstants.UPSCALE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw AppError.Backend("Upscale failed: ${response.code}")
        response.body?.bytes() ?: throw AppError.Backend("Empty upscale response")
    }

    // ── Private Helpers ──

    private fun stopProcess() {
        process?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(DreamHubConstants.PROCESS_STOP_TIMEOUT_S, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    proc.waitFor()
                }
                Log.i(TAG, "Process stopped, exit code: ${proc.exitValue()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping process", e)
            } finally {
                process = null
            }
        }
    }

    private fun buildLibraryPathEnv(): Map<String, String> {
        val runtimeDir = RuntimeDirPreparer.prepare(context)

        val systemLibPaths = mutableListOf(
            runtimeDir.absolutePath,
            "/system/lib64",
            "/vendor/lib64",
            "/vendor/lib64/egl",
        )

        try {
            val maliSymlink = File("/system/vendor/lib64/egl/libGLES_mali.so")
            if (maliSymlink.exists()) {
                val realPath = maliSymlink.canonicalPath
                val soc = realPath.split("/").getOrNull(realPath.split("/").size - 2)
                if (soc != null) {
                    listOf("/vendor/lib64/$soc", "/vendor/lib64/egl/$soc").forEach { path ->
                        if (path !in systemLibPaths) systemLibPaths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Mali paths: ${e.message}")
        }

        return mapOf(
            "LD_LIBRARY_PATH" to systemLibPaths.joinToString(":"),
            "DSP_LIBRARY_PATH" to runtimeDir.absolutePath
        )
    }

    private fun startProcessMonitor() {
        Thread {
            try {
                process?.let { proc ->
                    proc.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.i(TAG, "Backend: $line")
                        }
                    }
                    val exitCode = proc.waitFor()
                    Log.i(TAG, "Backend process exited with code: $exitCode")
                    if (_state.value is State.Running) {
                        _state.value = State.Error("Process exited with code: $exitCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process monitor error", e)
            }
        }.apply {
            isDaemon = true
            name = "BackendProcessMonitor"
            start()
        }
    }
}
