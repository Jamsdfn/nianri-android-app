package com.nianri.app.domain.calendar

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.LunarDate
import java.time.LocalDate

interface CalendarConverter {
    fun lunarFromSolar(solarDate: LocalDate): LunarDate

    fun displayDate(solarDate: LocalDate, calendarSystem: CalendarSystem): DisplayDate
}
