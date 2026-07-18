package com.nianri.app.domain.model

import java.time.LocalDate

enum class CalendarSystem { SOLAR, LUNAR }

enum class DateAdjustment { NON_LEAP_YEAR, SHORT_LUNAR_MONTH }

const val DEFAULT_REMINDER_TIME_MINUTES = 9 * 60

data class ImportantDay(
    val id: Long = 0,
    val name: String,
    val basis: CalendarSystem,
    val month: Int,
    val day: Int,
    val appDisplay: CalendarSystem,
    val reminders: Set<Int> = setOf(14, 7, 3),
    val reminderTimeMinutes: Int = DEFAULT_REMINDER_TIME_MINUTES,
    val isPinned: Boolean = false,
)

data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean)

data class DisplayDate(val calendar: CalendarSystem, val text: String)

data class Occurrence(
    val solarDate: LocalDate,
    val daysRemaining: Long,
    val adjustment: DateAdjustment? = null,
)
