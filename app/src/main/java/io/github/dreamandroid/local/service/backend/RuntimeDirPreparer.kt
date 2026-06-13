package io.github.dreamandroid.local.service.backend

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

object RuntimeDirPreparer {
    private const val TAG = "RuntimeDirPreparer"
    private const val RUNTIME_DIR = "runtime_libs"

    private var prepared = false

    @Synchronized
    fun prepare(context: Context): File {
        if (prepared) return File(context.filesDir, RUNTIME_DIR)

        val runtimeDir = File(context.filesDir, RUNTIME_DIR).apply {
            if (!exists()) mkdirs()
        }

        try {
            // Copy QNN libraries from assets
            val qnnlibsAssets = context.assets.list("qnnlibs")
            qnnlibsAssets?.forEach { fileName ->
                val targetLib = File(runtimeDir, fileName)
                val needsCopy = !targetLib.exists() ||
                    run {
                        val assetInputStream = context.assets.open("qnnlibs/$fileName")
                        val assetSize = assetInputStream.use { it.available().toLong() }
                        targetLib.length() != assetSize
                    }

                if (needsCopy) {
                    val assetInputStream = context.assets.open("qnnlibs/$fileName")
                    assetInputStream.use { input ->
                        targetLib.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $fileName from assets to runtime directory")
                }

                targetLib.setReadable(true, true)
                targetLib.setExecutable(true, true)
            }

            // Copy safety checker for filter flavor
            try {
                val flavor = context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .applicationInfo
                    ?.metaData
                    ?.getString("flavor")
                if (flavor == "filter") {
                    val safetyCheckerSource = context.assets.open("safety_checker.mnn")
                    val safetyCheckerTarget = File(context.filesDir, "safety_checker.mnn")
                    safetyCheckerSource.use { input ->
                        safetyCheckerTarget.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    safetyCheckerTarget.setReadable(true, true)
                    Log.i(TAG, "Safety checker model copied")
                }
            } catch (_: Exception) {
                // safety checker is optional
            }

            runtimeDir.setReadable(true, true)
            runtimeDir.setExecutable(true, true)

            prepared = true
            Log.i(TAG, "Runtime directory prepared: ${runtimeDir.absolutePath}")
            return runtimeDir
        } catch (e: IOException) {
            Log.e(TAG, "Failed to prepare QNN libraries from assets", e)
            throw RuntimeException("Failed to prepare QNN libraries", e)
        }
    }
}
