package io.github.dreamandroid.local.core.functional

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pure, stateless functional utilities used across the app.
 *
 * All functions are:
 * - **Deterministic**: same input → same output (except network I/O of course)
 * - **Stateless**: no mutable state captured from outside
 * - **Side-effect free**: no mutation of external state
 */

// ──────────────────────────────────────────
// Network / Health
// ──────────────────────────────────────────

/**
 * Probe a /health endpoint on [baseUrl] with the given timeouts.
 *
 * Returns `true` if the endpoint responds 2xx, `false` on any error
 * (connection refused, timeout, non-2xx, etc.).
 */
fun healthCheck(
    client: OkHttpClient,
    baseUrl: String,
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
): Boolean {
    return try {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()

        val timedClient = client.newBuilder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

        timedClient.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}

/**
 * Retry [block] up to [maxRetries] times, waiting [delayMs]
 * between attempts. Short-circuits on first `true`.
 */
suspend inline fun retryOrFalse(
    maxRetries: Int,
    delayMs: Long,
    crossinline block: suspend () -> Boolean,
): Boolean {
    repeat(maxRetries) {
        if (block()) return true
        if (it < maxRetries - 1) delay(delayMs)
    }
    return false
}

// ──────────────────────────────────────────
// Image transforms (pure Bitmap ↔ raw RGB)
// ──────────────────────────────────────────

/**
 * Convert a [Bitmap] to raw interleaved RGB bytes (width × height × 3).
 *
 * @return Triple of (rgbBytes, width, height)
 */
fun bitmapToRgbBytes(bitmap: Bitmap): Triple<ByteArray, Int, Int> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val rgb = ByteArray(width * height * 3)
    for (i in pixels.indices) {
        val p = pixels[i]
        rgb[i * 3]     = ((p shr 16) and 0xFF).toByte()    // R
        rgb[i * 3 + 1] = ((p shr 8)  and 0xFF).toByte()    // G
        rgb[i * 3 + 2] = ( p         and 0xFF).toByte()    // B
    }
    return Triple(rgb, width, height)
}

/**
 * Convert raw interleaved RGB bytes to an ARGB_8888 [Bitmap].
 *
 * @param rgb  ByteArray of size width * height * 3 (or larger; only first
 *             `width * height * 3` bytes are read).
 */
fun rgbBytesToBitmap(rgb: ByteArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val off = i * 3
        val r = rgb[off].toInt() and 0xFF
        val g = rgb[off + 1].toInt() and 0xFF
        val b = rgb[off + 2].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
