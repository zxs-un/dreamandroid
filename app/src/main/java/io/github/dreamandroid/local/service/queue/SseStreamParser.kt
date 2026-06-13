package io.github.dreamandroid.local.service.queue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class SseStreamParser(
    private val inputStream: InputStream
) {
    sealed class SseEvent {
        data class Progress(
            val step: Int,
            val totalSteps: Int,
            val imageBase64: String
        ) : SseEvent()

        data class Complete(
            val imageBase64: String,
            val seed: Long,
            val width: Int,
            val height: Int
        ) : SseEvent()

        data class Error(val message: String) : SseEvent()
    }

    fun events(): Flow<SseEvent> = flow {
        withContext(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.startsWith("data: ")) {
                        val json = l.removePrefix("data: ")
                        if (json == "[DONE]") break
                        emit(parseEvent(json))
                    }
                }
            }
        }
    }

    private fun parseEvent(json: String): SseEvent {
        val obj = JSONObject(json)
        return when (obj.getString("type")) {
            "progress" -> SseEvent.Progress(
                step = obj.getInt("step"),
                totalSteps = obj.getInt("total_steps"),
                imageBase64 = obj.getString("image")
            )
            "complete" -> SseEvent.Complete(
                imageBase64 = obj.getString("image"),
                seed = obj.optLong("seed"),
                width = obj.getInt("width"),
                height = obj.getInt("height")
            )
            "error" -> SseEvent.Error(obj.getString("message"))
            else -> SseEvent.Error("Unknown event type: ${obj.getString("type")}")
        }
    }
}
