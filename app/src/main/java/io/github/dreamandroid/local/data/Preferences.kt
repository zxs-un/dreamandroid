package io.github.dreamandroid.local.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "generation_prefs")

class GenerationPreferences(private val context: Context) {
    private fun getPromptKey(modelId: String) = stringPreferencesKey("${modelId}_prompt")
    private fun getNegativePromptKey(modelId: String) = stringPreferencesKey("${modelId}_negative_prompt")

    private fun getStepsKey(modelId: String) = floatPreferencesKey("${modelId}_steps")
    private fun getCfgKey(modelId: String) = floatPreferencesKey("${modelId}_cfg")
    private fun getSeedKey(modelId: String) = stringPreferencesKey("${modelId}_seed")
    private fun getWidthKey(modelId: String) = intPreferencesKey("${modelId}_width")
    private fun getHeightKey(modelId: String) = intPreferencesKey("${modelId}_height")
    private fun getDenoiseStrengthKey(modelId: String) = floatPreferencesKey("${modelId}_denoise_strength")

    private fun getUseOpenCLKey(modelId: String) = booleanPreferencesKey("${modelId}_use_opencl")

    private fun getBatchCountsKey(modelId: String) = intPreferencesKey("${modelId}_batch_counts")
    private fun getSchedulerKey(modelId: String) = stringPreferencesKey("${modelId}_scheduler")
    private fun getAspectRatioKey(modelId: String) = stringPreferencesKey("${modelId}_aspect_ratio")

    private val BASE_URL_KEY = stringPreferencesKey("base_url")
    private val SELECTED_SOURCE_KEY = stringPreferencesKey("selected_source")
    private val SHARE_USE_BASE64_KEY = booleanPreferencesKey("share_use_base64")
    private val SHARE_CLEAR_CLIPBOARD_KEY =
        booleanPreferencesKey("share_clear_clipboard_on_import")

    fun observeShareUseBase64(): Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[SHARE_USE_BASE64_KEY] ?: true }

    fun observeShareClearClipboardOnImport(): Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[SHARE_CLEAR_CLIPBOARD_KEY] ?: true }

    suspend fun setShareUseBase64(value: Boolean) {
        context.dataStore.edit { it[SHARE_USE_BASE64_KEY] = value }
    }

    suspend fun setShareClearClipboardOnImport(value: Boolean) {
        context.dataStore.edit { it[SHARE_CLEAR_CLIPBOARD_KEY] = value }
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = url
        }
    }

    suspend fun getBaseUrl(): String = context.dataStore.data
        .map { preferences ->
            preferences[BASE_URL_KEY] ?: "https://huggingface.co/"
        }.first()

    suspend fun saveSelectedSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_SOURCE_KEY] = source
        }
    }

    suspend fun getSelectedSource(): String = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_SOURCE_KEY] ?: "huggingface"
        }.first()

    suspend fun saveAllFields(
        modelId: String,
        prompt: String,
        negativePrompt: String,
        steps: Float,
        cfg: Float,
        seed: String,
        width: Int,
        height: Int,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        batchCounts: Int,
        scheduler: String,
        aspectRatio: String = "1:1",
    ) {
        context.dataStore.edit { preferences ->
            preferences[getPromptKey(modelId)] = prompt
            preferences[getNegativePromptKey(modelId)] = negativePrompt
            preferences[getStepsKey(modelId)] = steps
            preferences[getCfgKey(modelId)] = cfg
            preferences[getSeedKey(modelId)] = seed
            preferences[getWidthKey(modelId)] = width
            preferences[getHeightKey(modelId)] = height
            preferences[getDenoiseStrengthKey(modelId)] = denoiseStrength
            preferences[getUseOpenCLKey(modelId)] = useOpenCL
            preferences[getBatchCountsKey(modelId)] = batchCounts
            preferences[getSchedulerKey(modelId)] = scheduler
            preferences[getAspectRatioKey(modelId)] = aspectRatio
        }
    }

    suspend fun saveResolution(modelId: String, width: Int, height: Int) {
        context.dataStore.edit { preferences ->
            preferences[getWidthKey(modelId)] = width
            preferences[getHeightKey(modelId)] = height
        }
    }

    fun getPreferences(modelId: String): Flow<GenerationPrefs> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            GenerationPrefs(
                prompt = preferences[getPromptKey(modelId)] ?: "",
                negativePrompt = preferences[getNegativePromptKey(modelId)] ?: "",
                steps = preferences[getStepsKey(modelId)] ?: 20f,
                cfg = preferences[getCfgKey(modelId)] ?: 7f,
                seed = preferences[getSeedKey(modelId)] ?: "",
                width = preferences[getWidthKey(modelId)] ?: -1,
                height = preferences[getHeightKey(modelId)] ?: -1,
                denoiseStrength = preferences[getDenoiseStrengthKey(modelId)] ?: 0.6f,
                useOpenCL = preferences[getUseOpenCLKey(modelId)] ?: false,
                batchCounts = preferences[getBatchCountsKey(modelId)] ?: 1,
                scheduler = preferences[getSchedulerKey(modelId)] ?: "dpm",
                aspectRatio = preferences[getAspectRatioKey(modelId)] ?: "1:1",
            )
        }

    suspend fun clearPreferencesForModel(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(getPromptKey(modelId))
            preferences.remove(getNegativePromptKey(modelId))
            preferences.remove(getStepsKey(modelId))
            preferences.remove(getCfgKey(modelId))
            preferences.remove(getSeedKey(modelId))
            preferences.remove(getWidthKey(modelId))
            preferences.remove(getHeightKey(modelId))
            preferences.remove(getDenoiseStrengthKey(modelId))
            preferences.remove(getUseOpenCLKey(modelId))
            preferences.remove(getBatchCountsKey(modelId))
            preferences.remove(getSchedulerKey(modelId))
            preferences.remove(getAspectRatioKey(modelId))
        }
    }
}

@Immutable
data class GenerationPrefs(
    val prompt: String = "",
    val negativePrompt: String = "",
    val steps: Float = 20f,
    val cfg: Float = 7f,
    val seed: String = "",
    val width: Int = -1,
    val height: Int = -1,
    val denoiseStrength: Float = 0.6f,
    val useOpenCL: Boolean = false,
    val batchCounts: Int = 1,
    val scheduler: String = "dpm",
    val aspectRatio: String = "1:1",
)
