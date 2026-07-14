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

class WidgetRepository(private val database: NianriDatabase) {
    private val preferences = database.widgetPreferenceDao()
    private val days = ImportantDayRepository(database)

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

    suspend fun toggleDisplay(appWidgetId: Int): CalendarSystem? {
        val current = preferences.get(appWidgetId) ?: return null
        val toggled = when (current.display) {
            CalendarSystem.SOLAR -> CalendarSystem.LUNAR
            CalendarSystem.LUNAR -> CalendarSystem.SOLAR
        }
        preferences.upsert(current.copy(display = toggled))
        return toggled
    }

    suspend fun resolve(appWidgetId: Int): WidgetResolution {
        val preference = preferences.get(appWidgetId) ?: return WidgetResolution.Unconfigured
        val day = days.get(preference.importantDayId) ?: return WidgetResolution.MissingDay
        return WidgetResolution.Configured(day, preference.display)
    }

    suspend fun countReferences(dayId: Long): Int = preferences.countForDay(dayId)

    suspend fun hasConfiguredWidgets(): Boolean = preferences.countAll() > 0

    suspend fun configuredWidgetIds(): List<Int> = preferences.allIds()

    suspend fun remove(appWidgetId: Int) {
        preferences.delete(appWidgetId)
    }
}
