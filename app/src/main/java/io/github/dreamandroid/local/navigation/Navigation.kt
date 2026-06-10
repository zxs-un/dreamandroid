package io.github.dreamandroid.local.navigation

sealed class Screen(val route: String) {
    object ModelList : Screen("model_list")
    object ModelRun : Screen("model_run/{modelId}") {
        fun createRoute(modelId: String) = "model_run/$modelId"
    }

    object Upscale : Screen("upscale")
}
