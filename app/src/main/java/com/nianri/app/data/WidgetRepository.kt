package com.nianri.app.data

import androidx.room.withTransaction
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.data.local.WidgetPreferenceEntity
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay

sealed interface WidgetResolution {
    data object Unconfigured : WidgetResolution

    data object MissingDay : WidgetResolution

    data class Configured(
        val day: ImportantDay,
        val display: CalendarSystem,
    ) : WidgetResolution
}

data class LinkedDisplayChange(
    val dayId: Long,
    val display: CalendarSystem,
    val appWidgetIds: List<Int>,
)

class WidgetRepository(private val database: NianriDatabase) {
    private val preferences = database.widgetPreferenceDao()
    private val days = ImportantDayRepository(database)
    private val dayRecords = database.importantDayDao()

    suspend fun select(
        appWidgetId: Int,
        importantDayId: Long,
        display: CalendarSystem,
    ) {
        preferences.upsert(WidgetPreferenceEntity(appWidgetId, importantDayId, display))
    }

    suspend fun selectExistingDay(
        appWidgetId: Int,
        importantDayId: Long,
        display: CalendarSystem,
    ): Boolean = database.withTransaction {
        if (database.importantDayDao().get(importantDayId) == null) return@withTransaction false
        preferences.upsert(WidgetPreferenceEntity(appWidgetId, importantDayId, display))
        true
    }

    suspend fun setLinkedDisplay(
        dayId: Long,
        display: CalendarSystem,
    ): LinkedDisplayChange? = database.withTransaction {
        dayRecords.get(dayId) ?: return@withTransaction null
        dayRecords.updateAppDisplay(dayId, display)
        preferences.updateDisplayForDay(dayId, display)
        LinkedDisplayChange(dayId, display, preferences.idsForDay(dayId))
    }

    suspend fun toggleLinkedDisplay(appWidgetId: Int): LinkedDisplayChange? = database.withTransaction {
        val current = preferences.get(appWidgetId) ?: return@withTransaction null
        val day = dayRecords.get(current.importantDayId) ?: return@withTransaction null
        val toggled = when (day.appDisplay) {
            CalendarSystem.SOLAR -> CalendarSystem.LUNAR
            CalendarSystem.LUNAR -> CalendarSystem.SOLAR
        }
        dayRecords.updateAppDisplay(day.id, toggled)
        preferences.updateDisplayForDay(day.id, toggled)
        LinkedDisplayChange(day.id, toggled, preferences.idsForDay(day.id))
    }

    suspend fun resolve(appWidgetId: Int): WidgetResolution {
        val preference = preferences.get(appWidgetId) ?: return WidgetResolution.Unconfigured
        val day = days.get(preference.importantDayId) ?: return WidgetResolution.MissingDay
        return WidgetResolution.Configured(day, day.appDisplay)
    }

    suspend fun countReferences(dayId: Long): Int = preferences.countForDay(dayId)

    suspend fun hasConfiguredWidgets(): Boolean = preferences.countAll() > 0

    suspend fun configuredWidgetIds(): List<Int> = preferences.allIds()

    suspend fun remove(appWidgetId: Int) {
        preferences.delete(appWidgetId)
    }
}
