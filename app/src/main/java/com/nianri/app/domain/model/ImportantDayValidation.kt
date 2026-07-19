package com.nianri.app.domain.model

import java.time.Month

val SUPPORTED_REMINDER_OFFSETS = setOf(14, 7, 3)

fun requireValidImportantDay(day: ImportantDay) {
    require(day.name.trim().isNotEmpty()) { "Name must not be blank" }
    require(day.month in 1..12) { "Month must be between 1 and 12" }
    val maximumDay = when (day.basis) {
        CalendarSystem.SOLAR -> Month.of(day.month).maxLength()
        CalendarSystem.LUNAR -> 30
    }
    require(day.day in 1..maximumDay) { "Day is invalid for the selected month" }
    require(day.reminders.all { it in SUPPORTED_REMINDER_OFFSETS }) {
        "Unsupported reminder offset"
    }
    require(day.reminderTimeMinutes in 0 until 24 * 60) {
        "Reminder time must be between 00:00 and 23:59"
    }
}
