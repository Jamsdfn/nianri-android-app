package com.nianri.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import com.nianri.app.MainActivity
import com.nianri.app.data.UiPreferences
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.calendar.IcuCalendarConverter
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.ui.home.HomeScreen
import com.nianri.app.ui.home.HomeUiState
import com.nianri.app.ui.home.HomeViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun pinnedCardAppearsFirstAndTodayUsesFriendlyCopy() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    pinned = readyDay(id = 1, name = "妈妈生日", days = 0, pinned = true),
                    upcoming = listOf(readyDay(id = 2, name = "纪念日", days = 8)),
                    showCalendarExplanation = false,
                ),
            )
        }

        val pinnedTop = composeRule.onAllNodesWithText("妈妈生日")[0]
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot().top
        val upcomingTop = composeRule.onNodeWithText("纪念日")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot().top
        assertTrue(pinnedTop < upcomingTop)
        composeRule.onNodeWithText("就是今天").assertIsDisplayed()
        composeRule.onNodeWithText("接下来的日子").assertIsDisplayed()
    }

    @Test
    fun cardKeepsCountdownBasisAndDisplayLabelsVisible() {
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    pinned = readyDay(
                        id = 1,
                        name = "妈妈生日",
                        days = 23,
                        pinned = true,
                        basis = CalendarSystem.LUNAR,
                    ),
                    showCalendarExplanation = false,
                ),
            )
        }

        composeRule.onNodeWithText("按农历倒计时").assertIsDisplayed()
        composeRule.onNodeWithText("日期展示").assertIsDisplayed()
    }

    @Test
    fun switchingDisplayInvokesOnlyToggleCallback() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    pinned = readyDay(id = 42, name = "妈妈生日", days = 23, pinned = true),
                    showCalendarExplanation = false,
                ),
                onAdd = { events += "add" },
                onOpen = { events += "open:$it" },
                onToggleDisplay = { events += "toggle:$it" },
            )
        }

        composeRule.onNodeWithContentDescription("妈妈生日切换为农历展示").performClick()

        composeRule.runOnIdle { assertEquals(listOf("toggle:42"), events) }
    }

    @Test
    fun emptyStateOffersCreateAction() {
        var addCount = 0
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(showCalendarExplanation = false),
                onAdd = { addCount++ },
            )
        }

        composeRule.onNodeWithText("新建重要日子").performClick()
        composeRule.runOnIdle { assertEquals(1, addCount) }
    }

    @Test
    fun unavailableCardOffersEditAction() {
        var opened: Long? = null
        val day = ImportantDay(
            id = 17,
            name = "旧历生日",
            basis = CalendarSystem.LUNAR,
            month = 8,
            day = 30,
            appDisplay = CalendarSystem.SOLAR,
        )
        composeRule.setContent {
            HomeScreen(
                state = HomeUiState(
                    upcoming = listOf(DayCardModel.Unavailable(day)),
                    showCalendarExplanation = false,
                ),
                onOpen = { opened = it },
            )
        }

        composeRule.onNodeWithText("日期暂不可用").assertIsDisplayed()
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.runOnIdle { assertEquals(17L, opened) }
    }

    @Test
    fun explanationDismissalSurvivesViewModelRecreation() {
        val preferences = UiPreferences(context)
        val first = HomeViewModel(
            cards = flowOf(emptyList()),
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = preferences,
        )

        assertEquals(true, first.uiState.value.showCalendarExplanation)
        first.dismissCalendarExplanation()

        val recreated = HomeViewModel(
            cards = flowOf(emptyList()),
            updateAppDisplay = { _, _ -> },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        assertFalse(recreated.uiState.value.showCalendarExplanation)
    }

    @Test
    fun viewModelReprojectsDisplayWithoutChangingCountdown() {
        val original = readyDay(id = 8, name = "妈妈生日", days = 23, pinned = true)
        val updates = mutableListOf<Pair<Long, CalendarSystem>>()
        val viewModel = HomeViewModel(
            cards = MutableStateFlow(listOf(original)),
            updateAppDisplay = { id, display -> updates += id to display },
            converter = IcuCalendarConverter(),
            uiPreferences = UiPreferences(context),
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            HomeScreen(state = state, onToggleDisplay = viewModel::toggleDisplay)
        }
        composeRule.waitUntil { viewModel.uiState.value.pinned != null }

        composeRule.onNodeWithContentDescription("妈妈生日切换为农历展示").performClick()
        composeRule.waitForIdle()

        val updated = viewModel.uiState.value.pinned as DayCardModel.Ready
        assertEquals(CalendarSystem.LUNAR, updated.day.appDisplay)
        assertEquals(original.occurrence, updated.occurrence)
        assertEquals(original.occurrence.daysRemaining, updated.occurrence.daysRemaining)
        assertEquals(listOf(8L to CalendarSystem.LUNAR), updates)
    }

    @Test
    fun appShellUsesLightSystemBarIcons() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView,
                    ).isAppearanceLightStatusBars,
                )
            }
        }
    }

    private fun readyDay(
        id: Long,
        name: String,
        days: Long,
        pinned: Boolean = false,
        basis: CalendarSystem = CalendarSystem.SOLAR,
    ) = DayCardModel.Ready(
        day = ImportantDay(
            id = id,
            name = name,
            basis = basis,
            month = 8,
            day = 6,
            appDisplay = CalendarSystem.SOLAR,
            isPinned = pinned,
        ),
        occurrence = Occurrence(LocalDate.of(2026, 8, 6), days),
        displayedDate = DisplayDate(CalendarSystem.SOLAR, "2026年8月6日 · 星期四"),
    )
}
