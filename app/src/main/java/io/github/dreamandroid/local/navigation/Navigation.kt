package io.github.dreamandroid.local.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.dreamandroid.local.R

enum class BottomTab(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector,
) {
    Models("models", R.string.nav_models, Icons.Default.Memory),
    Generate("generate", R.string.nav_generate, Icons.Outlined.AutoAwesome),
    Upscale("upscale", R.string.nav_upscale, Icons.Default.ImageSearch),
    Browse("browse", R.string.nav_browse, Icons.Default.PhotoLibrary),
}

sealed class Screen(val route: String) {
    object ModelList : Screen("model_list")
    object ModelRun : Screen("model_run/{modelId}") {
        fun createRoute(modelId: String) = "model_run/$modelId"
    }

    object Upscale : Screen("upscale")
}
