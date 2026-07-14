package com.nianri.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WidgetPreferenceDao {
    @Query("SELECT * FROM widget_preferences WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetPreferenceEntity?

    @Upsert
    suspend fun upsert(entity: WidgetPreferenceEntity)

    @Query("SELECT COUNT(*) FROM widget_preferences WHERE importantDayId = :dayId")
    suspend fun countForDay(dayId: Long): Int
}
