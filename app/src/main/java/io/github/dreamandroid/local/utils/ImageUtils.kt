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
import io.github.dreamandroid.local.data.Model
import io.github.dreamandroid.local.ui.screens.GenerationParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val rgbBytes = ByteArray(width * height * 3)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        rgbBytes[i * 3] = ((pixel shr 16) and 0xFF).toByte()
        rgbBytes[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()
        rgbBytes[i * 3 + 2] = (pixel and 0xFF).toByte()
    }
    Log.d(
        "UpscaleBinary",
        "Prepare RGB data took: ${System.currentTimeMillis() - prepareStartTime}ms",
    )

    // Prepare binary request
    val url = URL("http://localhost:8081/upscale")
    val connection = url.openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setRequestProperty("X-Image-Width", width.toString())
        connection.setRequestProperty("X-Image-Height", height.toString())
        connection.setRequestProperty("X-Upscaler-Path", upscalerFile.absolutePath)
        connection.doOutput = true
        connection.connectTimeout = 300000 // 5 minutes
        connection.readTimeout = 300000

        // Send RGB binary data directly
        val sendStartTime = System.currentTimeMillis()
        connection.outputStream.use { os ->
            os.write(rgbBytes)
        }
        Log.d(
            "UpscaleBinary",
            "Send data took: ${System.currentTimeMillis() - sendStartTime}ms",
        )

        // Read response
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Read JPEG binary data
            val readStartTime = System.currentTimeMillis()
            val imageBytes = connection.inputStream.use { it.readBytes() }
            Log.d(
                "UpscaleBinary",
                "Receive JPEG data took: ${System.currentTimeMillis() - readStartTime}ms, size: ${imageBytes.size / 1024}KB",
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

            // Read response headers
            val resultWidth =
                connection.getHeaderField("X-Output-Width")?.toIntOrNull() ?: resultBitmap.width
            val resultHeight =
                connection.getHeaderField("X-Output-Height")?.toIntOrNull() ?: resultBitmap.height
            val durationMs = connection.getHeaderField("X-Duration-Ms")?.toIntOrNull() ?: 0

            Log.d("UpscaleBinary", "=== Upscale complete ===")
            Log.d("UpscaleBinary", "Server processing took: ${durationMs}ms")
            Log.d(
                "UpscaleBinary",
                "Client total time: ${System.currentTimeMillis() - totalStartTime}ms",
            )
            Log.d("UpscaleBinary", "Output size: ${resultWidth}x$resultHeight")

            resultBitmap
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("Upscale failed with response code: $responseCode, error: $errorBody")
        }
    } finally {
        connection.disconnect()
    }
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
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
                FileOutputStream(file).use { out ->
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
                    FileOutputStream(outFile).use { out -> input.copyTo(out) }
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
