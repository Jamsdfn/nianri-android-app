package com.nianri.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.nianri.app.domain.model.CalendarSystem
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportantDayDao {
    @Query("SELECT * FROM important_days")
    fun observeAll(): Flow<List<ImportantDayEntity>>

    @Query("SELECT * FROM important_days WHERE id = :id")
    suspend fun get(id: Long): ImportantDayEntity?

    @Query("SELECT * FROM important_days")
    suspend fun getAll(): List<ImportantDayEntity>

    @Insert
    suspend fun insertAll(entities: List<ImportantDayEntity>): List<Long>

    @Upsert
    suspend fun upsert(entity: ImportantDayEntity): Long

    @Query("UPDATE important_days SET isPinned = 0 WHERE isPinned = 1")
    suspend fun clearPinned()

    @Query("UPDATE important_days SET appDisplay = :display WHERE id = :id")
    suspend fun updateAppDisplay(id: Long, display: CalendarSystem)

    @Query("DELETE FROM important_days WHERE id = :id")
    suspend fun delete(id: Long)
}
