package com.nianri.app.domain.calendar

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DateAdjustment
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

class DateOccurrenceCalculator(private val converter: CalendarConverter) {
    fun next(day: ImportantDay, today: LocalDate): Occurrence =
        when (day.basis) {
            CalendarSystem.SOLAR -> nextSolar(day, today)
            CalendarSystem.LUNAR -> nextLunar(day, today)
        }

    private fun nextSolar(day: ImportantDay, today: LocalDate): Occurrence {
        val thisYear = solarCandidate(day.month, day.day, today.year)
        val (candidate, adjustment) = if (thisYear.first >= today) {
            thisYear
        } else {
            solarCandidate(day.month, day.day, today.year + 1)
        }
        return Occurrence(
            solarDate = candidate,
            daysRemaining = ChronoUnit.DAYS.between(today, candidate),
            adjustment = adjustment,
        )
    }

    private fun nextLunar(day: ImportantDay, today: LocalDate): Occurrence {
        for (offset in 0L..420L) {
            val candidate = today.plusDays(offset)
            val lunarDate = converter.lunarFromSolar(candidate)
            if (lunarDate.isLeapMonth) continue

            if (lunarDate.month == day.month && lunarDate.day == day.day) {
                return Occurrence(candidate, offset)
            }

            val isPossibleShortMonthFallback =
                day.day == 30 &&
                    lunarDate.month == day.month &&
                    lunarDate.day == 29
            if (isPossibleShortMonthFallback) {
                val nextLunarDate = converter.lunarFromSolar(candidate.plusDays(1))
                val leavesOrdinaryMonth =
                    nextLunarDate.isLeapMonth || nextLunarDate.month != lunarDate.month
                if (leavesOrdinaryMonth) {
                    return Occurrence(
                        solarDate = candidate,
                        daysRemaining = offset,
                        adjustment = DateAdjustment.SHORT_LUNAR_MONTH,
                    )
                }
            }
        }
        error("No lunar occurrence found within 420 days")
    }

    private fun solarCandidate(
        month: Int,
        day: Int,
        year: Int,
    ): Pair<LocalDate, DateAdjustment?> =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28) to DateAdjustment.NON_LEAP_YEAR
        } else {
            LocalDate.of(year, month, day) to null
        }
}
