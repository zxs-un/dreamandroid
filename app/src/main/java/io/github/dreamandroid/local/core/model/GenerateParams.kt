package io.github.dreamandroid.local.core.model

data class GenerateParams(
    val prompt: String,
    val negativePrompt: String = "",
    val steps: Int = 28,
    val cfg: Float = 7.0f,
    val useCfg: Boolean = true,
    val width: Int = 512,
    val height: Int = 512,
    val denoiseStrength: Float = 0.6f,
    val useOpenCL: Boolean = false,
    val scheduler: String = "dpm",
    val showDiffusionProcess: Boolean = false,
    val showDiffusionStride: Int = 1,
    val aspectRatio: String = "1:1",
    val seed: Long? = null,
    val imageBase64: String? = null,
    val maskBase64: String? = null,
)
