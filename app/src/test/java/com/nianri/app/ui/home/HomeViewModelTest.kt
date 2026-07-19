package com.nianri.app.ui.home

import android.content.Context
import android.os.Looper
import com.nianri.app.data.UiPreferences
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.IcuCalendarConverter
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun waitsForFirstCardsEmissionBeforeLeavingLoadingState() {
        val cards = MutableSharedFlow<List<DayCardModel>>(replay = 1)
        val viewModel = HomeViewModel(
            cards = cards,
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )

        assertTrue(viewModel.uiState.value.isLoading)

        assertTrue(cards.tryEmit(emptyList()))
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.upcoming.isEmpty())
    }
}
