package com.nianri.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nianri.app.AppContainer
import com.nianri.app.CurrentSystemZoneClock
import com.nianri.app.domain.WidgetUpdateUnavailableException
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.adjustmentCopy
import com.nianri.app.domain.model.Occurrence
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DetailUiState(
    val day: ImportantDay? = null,
    val occurrence: Occurrence? = null,
    val solarDate: DisplayDate? = null,
    val lunarDate: DisplayDate? = null,
    val adjustmentCopy: String? = null,
    val reminderSummary: String = "未开启提醒",
    val widgetReferences: Int = 0,
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
    val error: String? = null,
)

class DetailViewModel(
    private val dayId: Long,
    private val day: Flow<ImportantDay?>,
    private val countWidgetReferences: suspend (Long) -> Int,
    private val deleteDay: suspend (Long) -> Unit,
    private val calculator: DateOccurrenceCalculator,
    private val converter: CalendarConverter,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            day.collectLatest(::project)
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                deleteDay(dayId)
                mutableState.update { it.copy(deleted = true) }
            } catch (error: WidgetUpdateUnavailableException) {
                mutableState.update { it.copy(error = error.message ?: "已有小部件配置，暂时无法修改") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = "删除失败，请重试") }
            }
        }
    }

    private suspend fun project(currentDay: ImportantDay?) {
        if (currentDay == null) {
            mutableState.value = DetailUiState(isLoading = false, error = "未找到这个日子")
            return
        }
        val widgetReferences = try {
            countWidgetReferences(dayId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            0
        }
        var occurrence: Occurrence? = null
        var solarDate: DisplayDate? = null
        var lunarDate: DisplayDate? = null
        var errorCopy: String? = null
        try {
            occurrence = calculator.next(currentDay, LocalDate.now(clock))
            solarDate = converter.displayDate(occurrence.solarDate, CalendarSystem.SOLAR)
            lunarDate = converter.displayDate(occurrence.solarDate, CalendarSystem.LUNAR)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            errorCopy = "日期暂不可用"
        }
        mutableState.value = DetailUiState(
            day = currentDay,
            occurrence = occurrence,
            solarDate = solarDate,
            lunarDate = lunarDate,
            adjustmentCopy = adjustmentCopy(occurrence?.adjustment),
            reminderSummary = reminderSummary(currentDay.reminders),
            widgetReferences = widgetReferences,
            isLoading = false,
            error = errorCopy,
        )
    }

    class Factory(
        private val dayId: Long,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DetailViewModel::class.java))
            return DetailViewModel(
                dayId = dayId,
                day = container.importantDays.observeAll().map { days ->
                    days.firstOrNull { it.id == dayId }
                },
                countWidgetReferences = container.widgets::countReferences,
                deleteDay = container.dayMutationCoordinator::delete,
                calculator = container.occurrenceCalculator,
                converter = container.calendarConverter,
                clock = CurrentSystemZoneClock(),
            ) as T
        }
    }
}


internal fun reminderSummary(reminders: Set<Int>): String = if (reminders.isEmpty()) {
    "未开启提醒"
} else {
    val ordered = listOf(14, 7, 3).filter { it in reminders }
    "提前 ${ordered.joinToString("、")} 天"
}
