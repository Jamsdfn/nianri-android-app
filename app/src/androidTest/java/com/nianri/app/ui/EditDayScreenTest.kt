package com.nianri.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.ui.edit.EditDayScreen
import com.nianri.app.ui.edit.EditDayUiState
import com.nianri.app.reminder.ReminderPermissionState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditDayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dateTextIsReadOnlyAndOnlyEditButtonOpensPicker() {
        compose.setContent {
            EditDayScreen(
                state = state(), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("新历 8 月 6 日").assertHasNoClickAction()
        compose.onNodeWithContentDescription("编辑日期")
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("选择新历日期").assertIsDisplayed()
    }

    @Test
    fun dateDialogUsesWheelsInsteadOfStepButtons() {
        compose.setContent {
            EditDayScreen(
                state = state(), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithTag("month-wheel").assertIsDisplayed()
        compose.onNodeWithTag("day-wheel").assertIsDisplayed()
        compose.onAllNodesWithText("−").assertCountEquals(0)
        compose.onAllNodesWithText("＋").assertCountEquals(0)
    }

    @Test
    fun changingToShorterSolarMonthCoercesDayBeforeConfirming() {
        var month = 0
        var day = 0
        compose.setContent {
            EditDayScreen(
                state = state().copy(month = 3, day = 31), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = { month = it }, onDayChange = { day = it },
                onDisplayChange = {}, onToggleReminder = {}, onPinnedChange = {},
                onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithTag("month-wheel-item-4").performClick()
        compose.onNodeWithText("确定").performClick()

        compose.runOnIdle {
            assertEquals(4, month)
            assertEquals(30, day)
        }
    }

    @Test
    fun cancellingWheelEditsDoesNotWriteMonthOrDay() {
        val months = mutableListOf<Int>()
        val days = mutableListOf<Int>()
        compose.setContent {
            EditDayScreen(
                state = state(), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = { months += it }, onDayChange = { days += it },
                onDisplayChange = {}, onToggleReminder = {}, onPinnedChange = {},
                onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithTag("month-wheel-item-9").performClick()
        compose.onNodeWithContentDescription("取消日期编辑").performClick()

        compose.runOnIdle {
            assertTrue(months.isEmpty())
            assertTrue(days.isEmpty())
        }
    }

    @Test
    fun editModeUsesTheSameWheelDialog() {
        compose.setContent {
            EditDayScreen(
                state = state().copy(id = 42), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("编辑日子").assertIsDisplayed()
        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithTag("month-wheel").assertIsDisplayed()
        compose.onNodeWithTag("day-wheel").assertIsDisplayed()
    }

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
        compose.onNodeWithText("等待通知授权").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("小米手机可在系统设置检查通知与省电限制，无需开启自启动")
            .performScrollTo()
            .assertIsDisplayed()
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
    fun basisAndDisplayChoicesExposeRadioButtonSemantics() {
        compose.setContent {
            EditDayScreen(
                state = state(), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithTag("basis-options")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithTag("display-options")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithText("新历基准")
            .assertIsSelectable()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("默认展示农历")
            .assertIsSelectable()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun solarPickerPreservesThirtyFirst() {
        var selectedDay = 0
        compose.setContent {
            EditDayScreen(
                state = state().copy(month = 12, day = 31), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = { selectedDay = it }, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithText("确定").performClick()

        compose.runOnIdle { assertEquals(31, selectedDay) }
    }

    @Test
    fun solarPickerPreservesFebruaryTwentyNinth() {
        var selectedDay = 0
        compose.setContent {
            EditDayScreen(
                state = state().copy(month = 2, day = 29), widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = { selectedDay = it }, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithContentDescription("编辑日期").performClick()
        compose.onNodeWithText("确定").performClick()

        compose.runOnIdle { assertEquals(29, selectedDay) }
    }

    @Test
    fun previewUsesFriendlyTodayCopy() {
        compose.setContent {
            EditDayScreen(
                state = state().copy(
                    preview = state().preview?.copy(
                        occurrence = Occurrence(LocalDate.of(2026, 8, 6), 0),
                    ),
                ),
                widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("就是今天").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun optionalRemindersOffStillShowsMandatoryDayOfReminder() {
        compose.setContent {
            EditDayScreen(
                state = state().copy(
                    reminders = emptySet(),
                    permissionStatus = ReminderPermissionState.Ready,
                ),
                widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
            )
        }

        compose.onNodeWithText("提醒时间 09:00 · 固定开启").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("通知与闹钟权限已就绪").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun customReminderTimeIsShownAndRequestsSystemPicker() {
        var requests = 0
        var screenState by mutableStateOf(
            state().copy(reminderTimeMinutes = 8 * 60 + 35),
        )
        compose.setContent {
            EditDayScreen(
                state = screenState,
                widgetReferences = 0,
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {},
                onPinnedChange = {}, onSave = {}, onDelete = {},
                onPickReminderTime = {
                    requests += 1
                    screenState = screenState.copy(reminderTimeMinutes = 20 * 60 + 5)
                },
            )
        }

        compose.onNodeWithText("提醒时间 08:35 · 固定开启")
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("提醒时间 20:05 · 固定开启").assertIsDisplayed()
    }

    @Test
    fun permissionRowsExposeOnlyTheActionForTheirCurrentState() {
        var notificationRequests = 0
        var exactAlarmRequests = 0
        var settingsRequests = 0
        var permission: ReminderPermissionState by mutableStateOf(
            ReminderPermissionState.WaitingForNotificationPermission,
        )
        compose.setContent {
            EditDayScreen(
                state = state().copy(permissionStatus = permission),
                onBack = {}, onNameChange = {}, onBasisChange = {},
                onMonthChange = {}, onDayChange = {}, onDisplayChange = {},
                onToggleReminder = {}, onPinnedChange = {}, onSave = {}, onDelete = {},
                onRequestNotificationPermission = { notificationRequests += 1 },
                onRequestExactAlarmPermission = { exactAlarmRequests += 1 },
                onOpenReminderSettings = { settingsRequests += 1 },
            )
        }

        compose.onNodeWithText("授权通知").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, notificationRequests) }

        compose.runOnIdle { permission = ReminderPermissionState.WaitingForExactAlarmPermission }
        compose.onNodeWithText("开启闹钟和提醒").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exactAlarmRequests) }

        compose.runOnIdle { permission = ReminderPermissionState.Denied }
        compose.onNodeWithText("打开系统设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settingsRequests) }
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
