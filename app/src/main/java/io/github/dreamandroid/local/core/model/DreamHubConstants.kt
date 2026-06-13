package io.github.dreamandroid.local.core.model

object DreamHubConstants {
    const val BACKEND_PORT = 8081
    const val BACKEND_HOST = "localhost"
    const val RUNTIME_DIR = "runtime_libs"
    const val EXECUTABLE_NAME = "libstable_diffusion_core.so"

    // Timeouts (seconds)
    const val HEALTH_CHECK_TIMEOUT_S = 3L
    const val TOKENIZE_TIMEOUT_S = 5L
    const val GENERATE_TIMEOUT_S = 3600L
    const val UPSCALE_TIMEOUT_S = 300L

    // Process lifecycle
    const val PROCESS_STOP_TIMEOUT_S = 5L
    const val PROCESS_START_TIMEOUT_S = 30L

    // Health check retry
    const val DEFAULT_HEALTH_CHECK_RETRY_INTERVAL_S = 20L
    const val DEFAULT_HEALTH_CHECK_MAX_FAILURES = 4

    val BASE_URL get() = "http://$BACKEND_HOST:$BACKEND_PORT"
}
