package io.github.dreamandroid.local.service

import android.content.Context
import android.util.Log
import io.github.dreamandroid.local.service.backend.RuntimeDirPreparer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Global manager for the upscale backend process and selected upscale model state.
 *
 * Used by [ModelListTab] for load/unload, and by [UpscaleScreen] for status checks.
 * MainActivity consumes [state] to update [UpscaleTopBar] title warnings.
 */
object UpscaleBackendManager {

    sealed class State {
        /** No upscale model loaded, backend is idle. */
        data object Idle : State()

        /** Upscale backend is starting. */
        data class Starting(val upscalerId: String) : State()

        /** Upscale backend is running. */
        data class Running(val upscalerId: String) : State()

        /** Upscale backend failed to start or crashed. */
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** The ID of the upscaler model currently loaded, or null. */
    val loadedUpscalerId: String?
        get() = (_state.value as? State.Running)?.upscalerId

    /** Whether the upscale backend is currently running. */
    val isRunning: Boolean
        get() = _state.value is State.Running

    private var backendProcess: Process? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start the upscale backend process for the given [upscalerId].
     *
     * This runs the native executable with [--upscaler_mode] on port 8081.
     * If a process is already running it will be stopped first.
     */
    fun start(context: Context, upscalerId: String) {
        // Stop any existing backend first
        if (backendProcess?.isAlive == true) {
            stopInternal()
        }

        _state.value = State.Starting(upscalerId)

        scope.launch {
            try {
                val runtimeDir = RuntimeDirPreparer.prepare(context)
                val nativeDir = context.applicationInfo.nativeLibraryDir
                val executableFile = File(nativeDir, "libstable_diffusion_core.so")

                if (!executableFile.exists()) {
                    _state.value = State.Error("Executable file not found: ${executableFile.absolutePath}")
                    return@launch
                }

                val listenOnAll = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("listen_on_all_addresses", false)
                var command = listOf(
                    executableFile.absolutePath,
                    "--upscaler_mode",
                    "--lib_dir",
                    runtimeDir.absolutePath,
                    "--port",
                    "8081",
                )
                if (listenOnAll) {
                    command = command + "--listen_all"
                }

                val env = mutableMapOf<String, String>()
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
                            val socPaths = listOf(
                                "/vendor/lib64/$soc",
                                "/vendor/lib64/egl/$soc",
                            )
                            socPaths.forEach { path ->
                                if (!systemLibPaths.contains(path)) {
                                    systemLibPaths.add(path)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("UpscaleBackendMgr", "Failed to resolve Mali paths: ${e.message}")
                }

                env["LD_LIBRARY_PATH"] = systemLibPaths.joinToString(":")
                env["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath

                Log.d("UpscaleBackendMgr", "COMMAND: ${command.joinToString(" ")}")

                val processBuilder = ProcessBuilder(command).apply {
                    directory(File(nativeDir))
                    redirectErrorStream(true)
                    environment().putAll(env)
                }

                backendProcess = processBuilder.start()

                // Monitor stdout for early "ready" signal, then mark as Running
                Thread {
                    try {
                        backendProcess?.inputStream?.bufferedReader()?.use { reader ->
                            var line: String?
                            var becameReady = false
                            while (reader.readLine().also { line = it } != null) {
                                val logLine = line!!
                                Log.d("UpscaleBackend", logLine)
                                // Mark running on first log line (backend emits a ready message)
                                if (!becameReady) {
                                    becameReady = true
                                    scope.launch(Dispatchers.Main) {
                                        if (_state.value is State.Starting) {
                                            _state.value = State.Running(upscalerId)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UpscaleBackend", "Monitor error", e)
                    }
                }.apply {
                    isDaemon = true
                    start()
                }

                // Fallback: if monitor hasn't marked running after 8s, mark anyway
                delay(8000)
                if (_state.value is State.Starting) {
                    Log.w("UpscaleBackendMgr", "Backend did not signal ready; marking running anyway")
                    _state.value = State.Running(upscalerId)
                }
            } catch (e: Exception) {
                Log.e("UpscaleBackendMgr", "Failed to start backend", e)
                _state.value = State.Error("Failed to start backend: ${e.message}")
            }
        }
    }

    fun stop() {
        stopInternal()
        _state.value = State.Idle
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun stopInternal() {
        backendProcess?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
                Log.i("UpscaleBackendMgr", "Backend stopped")
            } catch (e: Exception) {
                Log.e("UpscaleBackendMgr", "Failed to stop backend", e)
            } finally {
                backendProcess = null
            }
        }
    }

    val processLogs = mutableListOf<String>()
}
