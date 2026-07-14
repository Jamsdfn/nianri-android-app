package com.nianri.app.domain

import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.ReminderScheduler

fun interface WidgetUpdater {
    suspend fun updateAll()
}

class DayMutationCoordinator(
    private val days: ImportantDayRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
) {
    suspend fun save(day: ImportantDay): Long {
        val id = days.save(day)
        reminders.replace(id)
        widgets.updateAll()
        return id
    }

    suspend fun delete(id: Long) {
        reminders.cancel(id)
        days.delete(id)
        widgets.updateAll()
    }
}
