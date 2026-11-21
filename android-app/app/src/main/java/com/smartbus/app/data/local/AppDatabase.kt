package com.smartbus.app.data.local

import androidx.room.*
import com.smartbus.app.data.model.OfflineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OfflineEventEntity)

    @Query("SELECT * FROM offline_events WHERE isSynced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedEventsFlow(): Flow<List<OfflineEventEntity>>

    @Query("SELECT * FROM offline_events WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<OfflineEventEntity>

    @Query("UPDATE offline_events SET isSynced = 1 WHERE clientEventId = :clientEventId")
    suspend fun markAsSynced(clientEventId: String)

    @Query("DELETE FROM offline_events WHERE isSynced = 1")
    suspend fun clearSyncedEvents()

    @Query("SELECT COUNT(*) FROM offline_events WHERE isSynced = 0")
    fun getPendingCountFlow(): Flow<Int>
}

@Database(entities = [OfflineEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineEventDao(): OfflineEventDao
}
