package com.nianri.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nianri.app.AppContainer
import com.nianri.app.data.UiPreferences
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.CalendarConverter
import com.nianri.app.domain.calendar.CalendarOperationException
import com.nianri.app.domain.model.CalendarSystem
import java.time.DateTimeException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class HomeUiState(
    val pinned: DayCardModel? = null,
    val upcoming: List<DayCardModel> = emptyList(),
    val showCalendarExplanation: Boolean = true,
    val displayError: String? = null,
)

class HomeViewModel(
    cards: Flow<List<DayCardModel>>,
    private val updateAppDisplay: suspend (Long, CalendarSystem) -> Unit,
    private val converter: CalendarConverter,
    private val uiPreferences: UiPreferences,
) : ViewModel() {
    private val displayOverrides = MutableStateFlow<Map<Long, CalendarSystem>>(emptyMap())
    private val displayError = MutableStateFlow<String?>(null)
    private val sourceCards = cards.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            sourceCards.collect { projectedCards ->
                displayOverrides.update { overrides ->
                    overrides.filterNot { (dayId, display) ->
                        projectedCards.any { model ->
                            model.day.id == dayId && model.day.appDisplay == display
                        }
                    }
                }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        sourceCards,
        displayOverrides,
        uiPreferences.calendarExplanationSeen,
        displayError,
    ) { projectedCards, overrides, explanationSeen, error ->
        val cardsWithOverrides = projectedCards.map { model ->
            val display = overrides[model.day.id]
            if (display == null) model else model.withDisplayOverride(display)
        }
        val pinned = cardsWithOverrides.firstOrNull { it.day.isPinned }
        HomeUiState(
            pinned = pinned,
            upcoming = cardsWithOverrides.filterNot { it === pinned },
            showCalendarExplanation = !explanationSeen,
            displayError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(
            showCalendarExplanation = !uiPreferences.calendarExplanationSeen.value,
        ),
    )

    fun toggleDisplay(dayId: Long) {
        val model = (listOfNotNull(uiState.value.pinned) + uiState.value.upcoming)
            .firstOrNull { it.day.id == dayId }
            ?: return
        val display = when (model.day.appDisplay) {
            CalendarSystem.SOLAR -> CalendarSystem.LUNAR
            CalendarSystem.LUNAR -> CalendarSystem.SOLAR
        }
        displayOverrides.update { it + (dayId to display) }
        viewModelScope.launch {
            try {
                updateAppDisplay(dayId, display)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                displayOverrides.update { overrides ->
                    if (overrides[dayId] == display) overrides - dayId else overrides
                }
                displayError.value = DISPLAY_ERROR
            }
        }
    }

    fun clearDisplayError() {
        displayError.value = null
    }

    fun dismissCalendarExplanation() {
        uiPreferences.markCalendarExplanationSeen()
    }

    private fun DayCardModel.withDisplayOverride(display: CalendarSystem): DayCardModel =
        when (this) {
            is DayCardModel.Ready -> {
                val overriddenDay = day.copy(appDisplay = display)
                try {
                    copy(
                        day = overriddenDay,
                        displayedDate = converter.displayDate(occurrence.solarDate, display),
                    )
                } catch (_: CalendarOperationException) {
                    DayCardModel.Unavailable(overriddenDay)
                } catch (_: DateTimeException) {
                    DayCardModel.Unavailable(overriddenDay)
                }
            }

            is DayCardModel.Unavailable -> copy(day = day.copy(appDisplay = display))
        }

    class Factory(
        private val container: AppContainer,
        private val uiPreferences: UiPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(
                cards = container.dayListProjector.observeAll(),
                updateAppDisplay = container.importantDays::updateAppDisplay,
                converter = container.calendarConverter,
                uiPreferences = uiPreferences,
            ) as T
        }
    }

    private companion object {
        const val DISPLAY_ERROR = "切换失败，请重试"
    }
}
