package io.github.dreamandroid.local.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity): Long

    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM generation_history WHERE modelId = :modelId")
    suspend fun deleteAllForModel(modelId: String): Int

    @Query("SELECT * FROM generation_history WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntity?

    @RawQuery(observedEntities = [HistoryEntity::class])
    fun query(q: SupportSQLiteQuery): Flow<List<HistoryEntity>>

    @RawQuery
    suspend fun queryOnce(q: SupportSQLiteQuery): List<HistoryEntity>

    @Query("SELECT COUNT(*) FROM generation_history WHERE modelId = :modelId AND timestamp = :timestamp")
    suspend fun countByKey(modelId: String, timestamp: Long): Int

    @Query("SELECT DISTINCT modelId FROM generation_history ORDER BY modelId")
    fun observeKnownModelIds(): Flow<List<String>>

    @Query("SELECT DISTINCT scheduler FROM generation_history ORDER BY scheduler")
    fun observeKnownSchedulers(): Flow<List<String>>

    @Query("SELECT DISTINCT (width || 'x' || height) FROM generation_history ORDER BY width * height DESC")
    fun observeKnownSizes(): Flow<List<String>>
}
