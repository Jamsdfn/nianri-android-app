package com.nianri.app.widget

import com.nianri.app.data.WidgetResolution
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.CalendarOperationException
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import java.time.Clock
import java.time.LocalDate

sealed interface WidgetModel {
    data object Unconfigured : WidgetModel

    data object MissingDay : WidgetModel

    data class DateUnavailable(
        val id: Long,
        val name: String,
        val basisLabel: String,
        val display: CalendarSystem,
    ) : WidgetModel

    data class Content(
        val id: Long,
        val name: String,
        val days: Long,
        val basisLabel: String,
        val displayedDate: String,
        val display: CalendarSystem,
    ) : WidgetModel
}

class WidgetPresentationMapper(
    private val calculator: DateOccurrenceCalculator,
    private val converter: CalendarConverter,
    private val clock: Clock,
) {
    fun map(resolution: WidgetResolution): WidgetModel = when (resolution) {
        WidgetResolution.Unconfigured -> WidgetModel.Unconfigured
        WidgetResolution.MissingDay -> WidgetModel.MissingDay
        is WidgetResolution.Configured -> mapConfigured(resolution)
    }

    private fun mapConfigured(resolution: WidgetResolution.Configured): WidgetModel {
        val day = resolution.day
        val basisLabel = when (day.basis) {
            CalendarSystem.SOLAR -> "按新历"
            CalendarSystem.LUNAR -> "按农历"
        }
        return try {
            val occurrence = calculator.next(day, LocalDate.now(clock))
            val displayedDate = converter.displayDate(occurrence.solarDate, resolution.display)
            WidgetModel.Content(
                id = day.id,
                name = day.name,
                days = occurrence.daysRemaining,
                basisLabel = basisLabel,
                displayedDate = displayedDate.text,
                display = resolution.display,
            )
        } catch (_: CalendarOperationException) {
            WidgetModel.DateUnavailable(
                id = day.id,
                name = day.name,
                basisLabel = basisLabel,
                display = resolution.display,
            )
        }
    }
}
