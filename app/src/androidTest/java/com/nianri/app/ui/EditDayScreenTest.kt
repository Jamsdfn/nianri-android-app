package com.nianri.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.ui.edit.EditDayScreen
import com.nianri.app.ui.edit.EditDayUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditDayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun formShowsNumberedSectionsHelperCopyIndependentRemindersAndPreview() {
        compose.setContent {
            EditDayScreen(
                state = state(),
                widgetReferences = 0,
                onBack = {},
                onNameChange = {},
                onBasisChange = {},
                onMonthChange = {},
                onDayChange = {},
                onDisplayChange = {},
                onToggleReminder = {},
                onPinnedChange = {},
                onSave = {},
                onDelete = {},
            )
        }

        compose.onNodeWithText("① 倒计时基准").assertIsDisplayed()
        compose.onNodeWithText("基准决定每年倒计时对应哪一天，保存后仍可编辑").assertIsDisplayed()
        compose.onNodeWithText("② 默认日期展示").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("只改变日期写法，不改变倒计时基准").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("提前 14 天").assertIsSelected()
        compose.onNodeWithText("提前 7 天").assertIsSelected()
        compose.onNodeWithText("提前 3 天").assertIsSelected()
        compose.onNodeWithText("还有 23 天").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("新历 2026年8月6日").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存时将申请通知与闹钟权限").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun basisSelectionIsExclusiveAndControlsAreIndependent() {
        val basisChanges = mutableListOf<CalendarSystem>()
        val reminderChanges = mutableListOf<Int>()
        var pinned = false
        var saved = false
        compose.setContent {
            EditDayScreen(
                state = state(),
                widgetReferences = 0,
                onBack = {},
                onNameChange = {},
                onBasisChange = { basisChanges += it },
                onMonthChange = {},
                onDayChange = {},
                onDisplayChange = {},
                onToggleReminder = { reminderChanges += it },
                onPinnedChange = { pinned = it },
                onSave = { saved = true },
                onDelete = {},
            )
        }

        compose.onNodeWithText("新历基准").assertIsSelected()
        compose.onNodeWithText("农历基准").assertIsNotSelected().performClick()
        compose.onNodeWithText("提前 14 天").performClick()
        compose.onNodeWithText("提前 3 天").performClick()
        compose.onNodeWithText("设为置顶日子").performScrollTo().performClick()
        compose.onNodeWithText("保存").performClick()

        compose.runOnIdle {
            assertEquals(listOf(CalendarSystem.LUNAR), basisChanges)
            assertEquals(listOf(14, 3), reminderChanges)
            assertTrue(pinned)
            assertTrue(saved)
        }
    }

    @Test
    fun allRemindersOffShowsHonestStatus() {
        compose.setContent {
            EditDayScreen(
                state = state().copy(reminders = emptySet()),
                widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("未开启提醒").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteRequiresConfirmationAndWarnsAboutReferencedWidgets() {
        var deleted = false
        compose.setContent {
            EditDayScreen(
                state = state().copy(id = 42),
                widgetReferences = 2,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {},
                onDelete = { deleted = true },
            )
        }

        compose.onNodeWithText("删除这个日子").performScrollTo().performClick()
        compose.onNodeWithText("删除后，相关小部件需要重新选择日子").assertIsDisplayed()
        compose.onNodeWithText("确认删除").performClick()

        compose.runOnIdle { assertTrue(deleted) }
    }

    private fun state() = EditDayUiState(
        id = 0,
        name = "妈妈生日",
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        display = CalendarSystem.SOLAR,
        preview = DayCardModel.Ready(
            day = ImportantDay(
                name = "妈妈生日",
                basis = CalendarSystem.SOLAR,
                month = 8,
                day = 6,
                appDisplay = CalendarSystem.SOLAR,
            ),
            occurrence = Occurrence(LocalDate.of(2026, 8, 6), 23),
            displayedDate = DisplayDate(CalendarSystem.SOLAR, "2026年8月6日"),
        ),
    )
}
