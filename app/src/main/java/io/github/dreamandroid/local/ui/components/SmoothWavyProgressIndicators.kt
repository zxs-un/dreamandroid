package io.github.dreamandroid.local.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.dreamandroid.local.ui.theme.Motion

@Composable
fun SmoothLinearWavyProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = Motion.Progress,
        label = "linearProgress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier,
    )
}

@Composable
fun SmoothCircularWavyProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = Motion.Progress,
        label = "circularProgress",
    )
    CircularProgressIndicator(
        progress = { animated },
        modifier = modifier,
    )
}
