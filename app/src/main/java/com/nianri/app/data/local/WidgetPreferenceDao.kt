package com.nianri.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nianri.app.domain.model.CalendarSystem

@Dao
interface WidgetPreferenceDao {
    @Query("SELECT * FROM widget_preferences WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetPreferenceEntity?

    @Upsert
    suspend fun upsert(entity: WidgetPreferenceEntity)

    @Query("SELECT COUNT(*) FROM widget_preferences WHERE importantDayId = :dayId")
    suspend fun countForDay(dayId: Long): Int

    @Query("SELECT appWidgetId FROM widget_preferences WHERE importantDayId = :dayId ORDER BY appWidgetId")
    suspend fun idsForDay(dayId: Long): List<Int>

    @Query("UPDATE widget_preferences SET display = :display WHERE importantDayId = :dayId")
    suspend fun updateDisplayForDay(dayId: Long, display: CalendarSystem)

    @Query("SELECT COUNT(*) FROM widget_preferences")
    suspend fun countAll(): Int

    @Query("SELECT appWidgetId FROM widget_preferences ORDER BY appWidgetId")
    suspend fun allIds(): List<Int>

    @Query("DELETE FROM widget_preferences WHERE appWidgetId = :id")
    suspend fun delete(id: Int)
}
