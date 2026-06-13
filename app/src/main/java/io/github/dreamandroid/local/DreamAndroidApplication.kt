package io.github.dreamandroid.local

import android.app.Application
import io.github.dreamandroid.local.data.HistoryMigration
import io.github.dreamandroid.local.data.MigrationState
import io.github.dreamandroid.local.data.db.AppDatabase
import io.github.dreamandroid.local.service.backend.BackendManager
import io.github.dreamandroid.local.service.backend.RuntimeDirPreparer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DreamAndroidApplication : Application() {

    // ── Coroutine Scopes ──

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Dependencies (initialized lazily) ──

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val backendManager: BackendManager by lazy {
        BackendManager(this).also {
            // Pre-warm runtime dir
            appScope.launch {
                try {
                    RuntimeDirPreparer.prepare(this@DreamAndroidApplication)
                } catch (_: Exception) {
                    // Non-fatal; will retry on first backend use
                }
            }
        }
    }

    // ── Migration State ──

    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    private var migrationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startMigration()
    }

    private fun startMigration() {
        migrationJob?.cancel()
        migrationJob = appScope.launch {
            try {
                if (HistoryMigration.isDone(this@DreamAndroidApplication)) {
                    _migrationState.value = MigrationState.NotNeeded
                    return@launch
                }
                HistoryMigration.migrate(
                    this@DreamAndroidApplication,
                    database,
                    _migrationState,
                )
            } catch (e: Throwable) {
                _migrationState.value = MigrationState.Failed(e)
            }
        }
    }

    fun retryMigration() {
        _migrationState.value = MigrationState.Idle
        startMigration()
    }

    fun skipMigration() {
        migrationJob?.cancel()
        appScope.launch {
            try {
                HistoryMigration.markDoneExternal(this@DreamAndroidApplication)
            } catch (_: Throwable) {
                // ignore — UI will still proceed
            }
            _migrationState.value = MigrationState.Done
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}
