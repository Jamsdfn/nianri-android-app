package com.nianri.app.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nianri.app.AppContainer
import com.nianri.app.CurrentSystemZoneClock
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.CalendarOperationException
import com.nianri.app.domain.calendar.DateOccurrenceCalculator
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.AndroidReminderPermissionController
import com.nianri.app.reminder.ReminderPermissionController
import com.nianri.app.reminder.ReminderPermissionState
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditDayUiState(
    val id: Long = 0,
    val name: String = "",
    val basis: CalendarSystem = CalendarSystem.SOLAR,
    val month: Int = 1,
    val day: Int = 1,
    val display: CalendarSystem = CalendarSystem.SOLAR,
    val reminders: Set<Int> = setOf(14, 7, 3),
    val pinned: Boolean = false,
    val preview: DayCardModel.Ready? = null,
    val nameError: String? = null,
    val dateError: String? = null,
    val activePicker: CalendarSystem = CalendarSystem.SOLAR,
    val permissionStatus: ReminderPermissionState = ReminderPermissionState.WaitingForNotificationPermission,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedId: Long? = null,
    val deleted: Boolean = false,
    val operationError: String? = null,
    val widgetReferences: Int = 0,
)

class EditDayViewModel(
    dayId: Long,
    private val loadDay: suspend (Long) -> ImportantDay?,
    private val saveDay: suspend (ImportantDay) -> Long,
    private val deleteDay: suspend (Long) -> Unit,
    private val countWidgetReferences: suspend (Long) -> Int = { 0 },
    private val calculator: DateOccurrenceCalculator,
    private val converter: CalendarConverter,
    private val clock: Clock,
    private val permissionController: ReminderPermissionController,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        EditDayUiState(id = dayId, isLoading = dayId != 0L).withPermissionState(),
    )
    val uiState: StateFlow<EditDayUiState> = mutableState.asStateFlow()

    init {
        if (dayId != 0L) {
            viewModelScope.launch {
                val day = loadDay(dayId)
                val widgetReferences = countWidgetReferences(dayId)
                mutableState.value = if (day == null) {
                    mutableState.value.copy(isLoading = false, operationError = "未找到这个日子")
                } else {
                    day.toUiState().copy(widgetReferences = widgetReferences).withPreview().withPermissionState()
                }
            }
        }
    }

    fun setName(name: String) = mutate { copy(name = name, nameError = null) }

    fun setBasis(basis: CalendarSystem) = mutate {
        copy(basis = basis, activePicker = basis, dateError = null)
    }

    fun setMonth(month: Int) = mutate { copy(month = month, dateError = null) }

    fun setDay(day: Int) = mutate { copy(day = day, dateError = null) }

    fun setDisplay(display: CalendarSystem) = mutate { copy(display = display) }

    fun toggleReminder(offset: Int) {
        require(offset in REMINDER_OFFSETS)
        mutate {
            copy(reminders = if (offset in reminders) reminders - offset else reminders + offset)
        }
    }

    fun setPinned(pinned: Boolean) = mutate { copy(pinned = pinned) }

    fun notificationPermissionRequestStarted() {
        if (mutableState.value.reminders.isEmpty()) return
        permissionController.notificationRequestStarted()
        refreshPermissionState()
    }

    fun exactAlarmPermissionRequestStarted() {
        if (mutableState.value.reminders.isEmpty()) return
        permissionController.exactAlarmRequestStarted()
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        mutableState.update { it.withPermissionState() }
    }

    fun clearOperationError() {
        mutableState.update { it.copy(operationError = null) }
    }

    fun save() {
        val current = mutableState.value
        val validation = validate(current)
        if (validation != null) {
            mutableState.update {
                it.copy(
                    nameError = validation.takeIf { error -> error.field == Field.NAME }?.message,
                    dateError = validation.takeIf { error -> error.field == Field.DATE }?.message,
                )
            }
            return
        }
        mutableState.update { it.copy(isSaving = true, operationError = null) }
        viewModelScope.launch {
            try {
                val id = saveDay(current.toImportantDay())
                mutableState.update { it.copy(isSaving = false, savedId = id) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(isSaving = false, operationError = "保存失败，请重试") }
            }
        }
    }

    fun delete() {
        val id = mutableState.value.id
        if (id == 0L) return
        viewModelScope.launch {
            try {
                deleteDay(id)
                mutableState.update { it.copy(deleted = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(operationError = "删除失败，请重试") }
            }
        }
    }

    private fun mutate(transform: EditDayUiState.() -> EditDayUiState) {
        mutableState.update { it.transform().withPreview().withPermissionState() }
    }

    private fun EditDayUiState.withPermissionState() = copy(
        permissionStatus = permissionController.state(reminders.isNotEmpty()),
    )

    private fun EditDayUiState.withPreview(): EditDayUiState {
        if (validate(this) != null) return copy(preview = null)
        return try {
            val importantDay = toImportantDay()
            val occurrence = calculator.next(importantDay, LocalDate.now(clock))
            copy(
                preview = DayCardModel.Ready(
                    day = importantDay,
                    occurrence = occurrence,
                    displayedDate = converter.displayDate(occurrence.solarDate, display),
                ),
            )
        } catch (_: CalendarOperationException) {
            copy(preview = null)
        } catch (_: DateTimeException) {
            copy(preview = null)
        }
    }

    private fun validate(state: EditDayUiState): ValidationError? {
        if (state.name.isBlank()) return ValidationError(Field.NAME, "请输入名称")
        if (state.month !in 1..12) return ValidationError(Field.DATE, "月份应为 1 至 12")
        return when (state.basis) {
            CalendarSystem.SOLAR -> {
                val max = Month.of(state.month).maxLength()
                if (state.day !in 1..max) ValidationError(Field.DATE, "该新历日期不存在") else null
            }
            CalendarSystem.LUNAR -> {
                if (state.day !in 1..30) ValidationError(Field.DATE, "农历日期应为初一至三十") else null
            }
        }
    }

    private fun EditDayUiState.toImportantDay() = ImportantDay(
        id = id,
        name = name.trim(),
        basis = basis,
        month = month,
        day = day,
        appDisplay = display,
        reminders = reminders,
        isPinned = pinned,
    )

    private fun ImportantDay.toUiState() = EditDayUiState(
        id = id,
        name = name,
        basis = basis,
        month = month,
        day = day,
        display = appDisplay,
        reminders = reminders,
        pinned = isPinned,
        activePicker = basis,
        isLoading = false,
    )

    class Factory(
        private val dayId: Long,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EditDayViewModel::class.java))
            return EditDayViewModel(
                dayId = dayId,
                loadDay = container.importantDays::get,
                saveDay = container.dayMutationCoordinator::save,
                deleteDay = container.dayMutationCoordinator::delete,
                countWidgetReferences = container.widgets::countReferences,
                calculator = container.occurrenceCalculator,
                converter = container.calendarConverter,
                clock = CurrentSystemZoneClock(),
                permissionController = AndroidReminderPermissionController(container.applicationContext),
            ) as T
        }
    }

    private data class ValidationError(val field: Field, val message: String)
    private enum class Field { NAME, DATE }

    private companion object {
        val REMINDER_OFFSETS = setOf(14, 7, 3)
    }
}
