package com.nianri.app.domain

import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.domain.calendar.CalendarOperationException
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface DayCardModel {
    val day: ImportantDay

    data class Ready(
        override val day: ImportantDay,
        val occurrence: Occurrence,
        val displayedDate: DisplayDate,
    ) : DayCardModel

    data class Unavailable(override val day: ImportantDay) : DayCardModel
}

class DayListProjector(
    private val days: ImportantDayRepository,
    private val calculator: DateOccurrenceCalculator,
    private val converter: CalendarConverter,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<DayCardModel>> = days.observeAll().map { importantDays ->
        val today = LocalDate.now(clock)
        importantDays
            .map { day -> project(day, today) }
            .sortedWith(
                compareBy<DayCardModel> { !it.day.isPinned }
                    .thenBy { it is DayCardModel.Unavailable }
                    .thenBy { (it as? DayCardModel.Ready)?.occurrence?.solarDate }
                    .thenBy { it.day.name },
            )
    }

    private fun project(day: ImportantDay, today: LocalDate): DayCardModel =
        try {
            val occurrence = calculator.next(day, today)
            DayCardModel.Ready(
                day = day,
                occurrence = occurrence,
                displayedDate = converter.displayDate(occurrence.solarDate, day.appDisplay),
            )
        } catch (_: CalendarOperationException) {
            DayCardModel.Unavailable(day)
        } catch (_: DateTimeException) {
            DayCardModel.Unavailable(day)
        }
}
