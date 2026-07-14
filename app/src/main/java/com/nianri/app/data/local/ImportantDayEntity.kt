package com.nianri.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.nianri.app.domain.model.CalendarSystem

@Entity(tableName = "important_days")
data class ImportantDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val basis: CalendarSystem,
    val month: Int,
    val day: Int,
    val appDisplay: CalendarSystem,
    val reminderMask: Int,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

class DatabaseConverters {
    @TypeConverter
    fun calendarToString(value: CalendarSystem): String = value.name

    @TypeConverter
    fun stringToCalendar(value: String): CalendarSystem = CalendarSystem.valueOf(value)
}
