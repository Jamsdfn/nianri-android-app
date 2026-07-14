package com.nianri.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nianri.app.domain.model.CalendarSystem

@Entity(tableName = "widget_preferences")
data class WidgetPreferenceEntity(
    @PrimaryKey val appWidgetId: Int,
    val importantDayId: Long,
    val display: CalendarSystem,
)
