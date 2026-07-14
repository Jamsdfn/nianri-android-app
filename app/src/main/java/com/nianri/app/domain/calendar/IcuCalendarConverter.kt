package com.nianri.app.domain.calendar

import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.LunarDate
import java.time.LocalDate
import java.time.ZoneOffset

class IcuCalendarConverter internal constructor(
    private val calendarProvider: () -> ChineseCalendar,
) : CalendarConverter {
    constructor() : this({ ChineseCalendar(TimeZone.getTimeZone("UTC")) })

    override fun lunarFromSolar(solarDate: LocalDate): LunarDate {
        val epochMillis = solarDate
            .atTime(12, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        return try {
            val calendar = calendarProvider()
            calendar.timeInMillis = epochMillis
            LunarDate(
                month = calendar.get(ChineseCalendar.MONTH) + 1,
                day = calendar.get(ChineseCalendar.DAY_OF_MONTH),
                isLeapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1,
            )
        } catch (failure: IllegalArgumentException) {
            throw CalendarConversionException(
                message = "ICU could not convert solar date $solarDate to a lunar date",
                cause = failure,
            )
        }
    }

    override fun displayDate(
        solarDate: LocalDate,
        calendarSystem: CalendarSystem,
    ): DisplayDate = when (calendarSystem) {
        CalendarSystem.SOLAR -> DisplayDate(
            calendar = CalendarSystem.SOLAR,
            text = "${solarDate.year}年${solarDate.monthValue}月${solarDate.dayOfMonth}日",
        )

        CalendarSystem.LUNAR -> DisplayDate(
            calendar = CalendarSystem.LUNAR,
            text = lunarFromSolar(solarDate).toChineseText(),
        )
    }

    private fun LunarDate.toChineseText(): String {
        val monthText = listOf(
            "正", "二", "三", "四", "五", "六",
            "七", "八", "九", "十", "冬", "腊",
        )[month - 1]
        val dayText = when (day) {
            in 1..9 -> "初${chineseDigit(day)}"
            10 -> "初十"
            in 11..19 -> "十${chineseDigit(day - 10)}"
            20 -> "二十"
            in 21..29 -> "廿${chineseDigit(day - 20)}"
            30 -> "三十"
            else -> error("Unsupported lunar day: $day")
        }
        return "${if (isLeapMonth) "闰" else ""}${monthText}月$dayText"
    }

    private fun chineseDigit(value: Int): String =
        listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")[value]
}
