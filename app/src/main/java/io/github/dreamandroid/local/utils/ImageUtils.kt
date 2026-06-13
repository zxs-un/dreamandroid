package io.github.dreamandroid.local.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.github.dreamandroid.local.DreamAndroidApplication
import io.github.dreamandroid.local.core.functional.bitmapToRgbBytes
import io.github.dreamandroid.local.data.Model
import io.github.dreamandroid.local.ui.screens.GenerationParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val saveSequence = AtomicLong(0L)

private fun nextSaveFilename(extension: String): String {
    val ts = System.currentTimeMillis()
    val seq = saveSequence.getAndIncrement()
    return "generated_image_${ts}_$seq.$extension"
}

suspend fun performUpscale(context: Context, bitmap: Bitmap, upscalerId: String): Bitmap = withContext(Dispatchers.IO) {
    val totalStartTime = System.currentTimeMillis()

    // Get upscaler model path
    val upscalerModelsDir = File(Model.getModelsDir(context), upscalerId)
    val upscalerFile = File(upscalerModelsDir, "upscaler.bin")

    if (!upscalerFile.exists()) {
        throw Exception("Upscaler model file not found: ${upscalerFile.absolutePath}")
    }

    // Convert bitmap to RGB bytes
    val prepareStartTime = System.currentTimeMillis()
    val (rgbBytes, width, height) = bitmapToRgbBytes(bitmap)
    Log.d(
        "UpscaleBinary",
        "Prepare RGB data took: ${System.currentTimeMillis() - prepareStartTime}ms",
    )

    // Use shared BackendManager for HTTP (shared OkHttpClient connection pool)
    val app = context.applicationContext as DreamAndroidApplication
    val sendStartTime = System.currentTimeMillis()
    val imageBytes = app.backendManager.upscale(rgbBytes, width, height, upscalerFile.absolutePath)
    Log.d(
        "UpscaleBinary",
        "Send data took: ${System.currentTimeMillis() - sendStartTime}ms",
    )
    Log.d(
        "UpscaleBinary",
        "Receive JPEG data size: ${imageBytes.size / 1024}KB",
    )

    // Decode JPEG to Bitmap
    val decodeStartTime = System.currentTimeMillis()
    val resultBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    Log.d(
        "UpscaleBinary",
        "Decode JPEG took: ${System.currentTimeMillis() - decodeStartTime}ms",
    )

    if (resultBitmap == null) {
        throw Exception("Failed to decode JPEG response")
    }

    Log.d("UpscaleBinary", "=== Upscale complete ===")
    Log.d(
        "UpscaleBinary",
        "Client total time: ${System.currentTimeMillis() - totalStartTime}ms",
    )
    Log.d("UpscaleBinary", "Output size: ${resultBitmap.width}x${resultBitmap.height}")

    resultBitmap
}

suspend fun reportImage(
    bitmap: Bitmap,
    modelName: String,
    params: GenerationParameters,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    withContext(Dispatchers.IO) {
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.getEncoder().encodeToString(byteArray)

            val jsonObject = JSONObject().apply {
                put("model_name", modelName)
                put(
                    "generation_params",
                    JSONObject().apply {
                        put("prompt", params.prompt)
                        put("negative_prompt", params.negativePrompt)
                        put("steps", params.steps)
                        put("cfg", params.cfg)
                        put("seed", params.seed ?: JSONObject.NULL)
                        put("size", "${params.width}x${params.height}")
                        put("run_on_cpu", params.runOnCpu)
                        put("generation_time", params.generationTime ?: JSONObject.NULL)
                    },
                )
                put("image_data", base64Image)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(30))
                .build()

            val requestBody = jsonObject.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://report.chino.icu/report")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError("Report failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
//                onError("Failed to report: ${e.localizedMessage}")
                onError("Network Error")
            }
        }
    }
}

suspend fun saveImage(context: Context, bitmap: Bitmap, onSuccess: () -> Unit, onError: (String) -> Unit) {
    saveImage(context, bitmap, bitmap.width, bitmap.height, onSuccess, onError)
}

suspend fun saveImage(context: Context, bitmap: Bitmap, width: Int, height: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            Log.d(
                "SaveImage",
                "Start saving image - size: ${width}x${height}",
            )

            // Save as JPEG if width or height is greater than 1024, otherwise save as PNG
            val isLargeImage = width > 1024 || height > 1024
            val format = if (isLargeImage) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val extension = if (isLargeImage) "jpg" else "png"
            val mimeType = if (isLargeImage) "image/jpeg" else "image/png"
            val quality = if (isLargeImage) 95 else 100

            Log.d("SaveImage", "Save format: ${if (isLargeImage) "JPEG" else "PNG"}")

            val filename = nextSaveFilename(extension)

            // Subdirectory based on image size, e.g. DreamHub/512x512
            val sizeDir = "${width}x${height}"
            val relativePath = Environment.DIRECTORY_PICTURES + "/DreamHub/" + sizeDir

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        relativePath,
                    )
                }

                val resolver = context.contentResolver
                val createUriTime = System.currentTimeMillis()
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw IOException("Failed to create MediaStore entry")
                Log.d(
                    "SaveImage",
                    "Create URI took: ${System.currentTimeMillis() - createUriTime}ms",
                )

                val compressStartTime = System.currentTimeMillis()
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(format, quality, outputStream)
                } ?: throw IOException("Failed to open output stream")
                Log.d(
                    "SaveImage",
                    "Compression and writing took: ${System.currentTimeMillis() - compressStartTime}ms",
                )
            } else {
                // Android 9
                val imagesDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES,
                    ),
                    "DreamHub/$sizeDir",
                )

                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val file = File(imagesDir, filename)
                val compressStartTime = System.currentTimeMillis()
                file.outputStream().use { out ->
                    bitmap.compress(format, quality, out)
                }
                Log.d(
                    "SaveImage",
                    "Compression and writing took: ${System.currentTimeMillis() - compressStartTime}ms",
                )

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.toString()),
                    arrayOf(mimeType),
                    null,
                )
            }

            val totalTime = System.currentTimeMillis() - startTime
            Log.d("SaveImage", "Save complete - total time: ${totalTime}ms")

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to save: ${e.localizedMessage}")
            }
        }
    }
}

/**
 * Copies a pre-encoded image file (PNG/JPEG) into the Pictures/DreamHub gallery
 * folder without decoding + re-encoding. Used for batch-saving history items
 * where the source file is already in the format we want to export.
 */
suspend fun saveImageFromFile(context: Context, sourceFile: File, onSuccess: () -> Unit, onError: (String) -> Unit) {
    saveImageFromFile(context, sourceFile, 0, 0, onSuccess, onError)
}

suspend fun saveImageFromFile(context: Context, sourceFile: File, width: Int, height: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val extension = sourceFile.extension.lowercase().ifEmpty { "png" }
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/*"
            }
            val filename = nextSaveFilename(extension)

            // Subdirectory based on image size, e.g. DreamHub/512x512
            val sizeDir = if (width > 0 && height > 0) "${width}x${height}" else "unknown"
            val relativePath = Environment.DIRECTORY_PICTURES + "/DreamHub/" + sizeDir

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        relativePath,
                    )
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IOException("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IOException("Failed to open output stream")
            } else {
                val imagesDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES,
                    ),
                    "DreamHub/$sizeDir",
                )
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val outFile = File(imagesDir, filename)
                sourceFile.inputStream().use { input ->
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.toString()),
                    arrayOf(mimeType),
                    null,
                )
            }

            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to save: ${e.localizedMessage}")
            }
        }
    }
}
