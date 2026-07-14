package com.nianri.app.domain

import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.ReminderScheduler

fun interface WidgetUpdater {
    suspend fun updateAll()

    suspend fun prepareMutation() = Unit
}

class WidgetUpdateUnavailableException(message: String) : IllegalStateException(message)

class DayMutationCoordinator(
    private val days: ImportantDayRepository,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
) {
    suspend fun save(day: ImportantDay): Long {
        widgets.prepareMutation()
        val id = days.save(day)
        reminders.replace(id)
        widgets.updateAll()
        return id
    }

    suspend fun delete(id: Long) {
        widgets.prepareMutation()
        reminders.cancel(id)
        days.delete(id)
        widgets.updateAll()
    }
}
