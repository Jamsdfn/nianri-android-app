package com.nianri.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.ui.detail.DetailScreen
import com.nianri.app.ui.detail.DetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unavailableConversionKeepsEditAndDeleteActions() {
        var editedId = 0L
        var deleted = false
        compose.setContent {
            DetailScreen(
                state = DetailUiState(
                    day = ImportantDay(
                        id = 42,
                        name = "纪念日",
                        basis = CalendarSystem.SOLAR,
                        month = 8,
                        day = 6,
                        appDisplay = CalendarSystem.LUNAR,
                    ),
                    occurrence = null,
                    reminderSummary = "提前 14 天",
                    widgetReferences = 0,
                    isLoading = false,
                    error = "日期暂不可用",
                ),
                onBack = {},
                onEdit = { editedId = it },
                onDelete = { deleted = true },
            )
        }

        compose.onNodeWithText("日期暂不可用").assertIsDisplayed()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("确认删除").performClick()

        compose.runOnIdle {
            assertEquals(42L, editedId)
            assertTrue(deleted)
        }
    }

    @Test
    fun todayUsesFriendlyCopy() {
        compose.setContent {
            DetailScreen(
                state = DetailUiState(
                    day = ImportantDay(
                        id = 42,
                        name = "今天",
                        basis = CalendarSystem.SOLAR,
                        month = 8,
                        day = 6,
                        appDisplay = CalendarSystem.SOLAR,
                    ),
                    occurrence = com.nianri.app.domain.model.Occurrence(
                        java.time.LocalDate.of(2026, 8, 6),
                        0,
                    ),
                    isLoading = false,
                ),
                onBack = {}, onEdit = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("就是今天").assertIsDisplayed()
    }

    @Test
    fun conversionOnlyFailureShowsUnavailableCopy() {
        compose.setContent {
            DetailScreen(
                state = DetailUiState(
                    day = ImportantDay(
                        id = 42,
                        name = "纪念日",
                        basis = CalendarSystem.SOLAR,
                        month = 8,
                        day = 6,
                        appDisplay = CalendarSystem.LUNAR,
                    ),
                    occurrence = com.nianri.app.domain.model.Occurrence(
                        java.time.LocalDate.of(2026, 8, 6),
                        3,
                    ),
                    isLoading = false,
                    error = "日期暂不可用",
                ),
                onBack = {}, onEdit = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("日期暂不可用").assertIsDisplayed()
    }
}
