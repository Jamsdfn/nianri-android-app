package com.nianri.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.reminder.ReminderPermissionState
import com.nianri.app.ui.theme.NianriTheme
import com.nianri.app.ui.countdownCopy
import java.time.LocalDate
import java.time.Month

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDayScreen(
    state: EditDayUiState,
    widgetReferences: Int = state.widgetReferences,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onBasisChange: (CalendarSystem) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onDisplayChange: (CalendarSystem) -> Unit,
    onToggleReminder: (Int) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onSaved: (Long) -> Unit = {},
    onDeleted: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onRequestExactAlarmPermission: () -> Unit = {},
    onOpenReminderSettings: () -> Unit = {},
) {
    var showDateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新建日子" else "编辑日子") },
                navigationIcon = { TextButton(onClick = onBack) { Text("取消") } },
                actions = { TextButton(onClick = onSave, enabled = !state.isSaving) { Text("保存") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { error -> ({ Text(error) }) },
            )

            SectionTitle("① 倒计时基准", "基准决定每年倒计时对应哪一天，保存后仍可编辑")
            CalendarChoices(
                selected = state.basis,
                solarLabel = "新历基准",
                lunarLabel = "农历基准",
                groupTag = "basis-options",
                onSelected = onBasisChange,
            )
            OutlinedButton(onClick = { showDateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                val prefix = if (state.activePicker == CalendarSystem.SOLAR) "新历" else "农历"
                Text("$prefix ${state.month} 月 ${state.day} 日")
            }
            state.dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            SectionTitle("② 默认日期展示", "只改变日期写法，不改变倒计时基准")
            CalendarChoices(
                selected = state.display,
                solarLabel = "默认展示新历",
                lunarLabel = "默认展示农历",
                groupTag = "display-options",
                onSelected = onDisplayChange,
            )

            Text("提醒", style = MaterialTheme.typography.titleMedium)
            Text(
                "当天 09:00 · 固定开启",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(14, 7, 3).forEach { offset ->
                    FilterChip(
                        selected = offset in state.reminders,
                        onClick = { onToggleReminder(offset) },
                        label = { Text("提前 $offset 天") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            PermissionStatusRow(
                state = state,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onOpenReminderSettings = onOpenReminderSettings,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.pinned,
                        role = Role.Switch,
                        onValueChange = onPinnedChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("设为置顶日子")
                Switch(checked = state.pinned, onCheckedChange = null)
            }

            state.preview?.let { PreviewCard(it) }
            state.operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (state.id != 0L) {
                TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这个日子", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDateDialog) {
        MonthDayDialog(
            calendar = state.activePicker,
            initialMonth = state.month,
            initialDay = state.day,
            onDismiss = { showDateDialog = false },
            onConfirm = { month, day ->
                onMonthChange(month)
                onDayChange(day)
                showDateDialog = false
            },
        )
    }
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            widgetReferences = widgetReferences,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String, helper: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarChoices(
    selected: CalendarSystem,
    solarLabel: String,
    lunarLabel: String,
    groupTag: String,
    onSelected: (CalendarSystem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(groupTag).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            CalendarSystem.SOLAR to solarLabel,
            CalendarSystem.LUNAR to lunarLabel,
        ).forEach { (calendar, label) ->
            val isSelected = selected == calendar
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelected(calendar) },
                    ),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    state: EditDayUiState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenReminderSettings: () -> Unit,
) {
    val text = when (state.permissionStatus) {
        ReminderPermissionState.NotNeeded -> "未开启提醒"
        ReminderPermissionState.WaitingForNotificationPermission -> "等待通知授权"
        ReminderPermissionState.WaitingForExactAlarmPermission -> "等待闹钟和提醒授权"
        ReminderPermissionState.Denied -> "提醒未生效"
        ReminderPermissionState.Ready -> "通知与闹钟权限已就绪"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (state.permissionStatus != ReminderPermissionState.NotNeeded) {
                Text(
                    "小米手机可在系统设置检查通知与省电限制，无需开启自启动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state.permissionStatus) {
                ReminderPermissionState.WaitingForNotificationPermission ->
                    TextButton(onClick = onRequestNotificationPermission) { Text("授权通知") }
                ReminderPermissionState.WaitingForExactAlarmPermission ->
                    TextButton(onClick = onRequestExactAlarmPermission) { Text("开启闹钟和提醒") }
                ReminderPermissionState.Denied ->
                    TextButton(onClick = onOpenReminderSettings) { Text("打开系统设置") }
                ReminderPermissionState.NotNeeded, ReminderPermissionState.Ready -> Unit
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: DayCardModel.Ready) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("保存预览", style = MaterialTheme.typography.labelLarge)
            Text(countdownCopy(preview.occurrence.daysRemaining), style = MaterialTheme.typography.headlineMedium)
            Text("${calendarName(preview.day.basis)}为倒计时基准")
            Text("${calendarName(preview.displayedDate.calendar)} ${preview.displayedDate.text}")
        }
    }
}

@Composable
private fun MonthDayDialog(
    calendar: CalendarSystem,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var month by remember(initialMonth) { mutableIntStateOf(initialMonth.coerceIn(1, 12)) }
    var day by remember(initialDay, calendar, initialMonth) {
        mutableIntStateOf(initialDay.coerceIn(1, dayMaximum(calendar, initialMonth.coerceIn(1, 12))))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择${calendarName(calendar)}日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberSelector("月份", month, 1, 12) { selectedMonth ->
                    month = selectedMonth
                    day = day.coerceAtMost(dayMaximum(calendar, selectedMonth))
                }
                NumberSelector("日期", day, 1, dayMaximum(calendar, month)) { day = it }
                Text("可选择 2 月 29 日或农历三十，缺少该日期的年份将按规则调整。")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(month, day) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun dayMaximum(calendar: CalendarSystem, month: Int): Int = when (calendar) {
    CalendarSystem.SOLAR -> Month.of(month).maxLength()
    CalendarSystem.LUNAR -> 30
}

@Composable
private fun NumberSelector(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        OutlinedButton(onClick = { onChange(if (value == minimum) maximum else value - 1) }) { Text("−") }
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onChange(if (value == maximum) minimum else value + 1) }) { Text("＋") }
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    widgetReferences: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除？") },
        text = {
            Text(
                if (widgetReferences > 0) "删除后，相关小部件需要重新选择日子"
                else "删除后无法恢复",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun calendarName(calendar: CalendarSystem) =
    if (calendar == CalendarSystem.SOLAR) "新历" else "农历"

@Preview(showBackground = true)
@Composable
private fun EditDayScreenPreview() {
    NianriTheme {
        EditDayScreen(
            state = EditDayUiState(
                name = "妈妈生日",
                basis = CalendarSystem.LUNAR,
                month = 6,
                day = 24,
                display = CalendarSystem.SOLAR,
                preview = DayCardModel.Ready(
                    day = ImportantDay(
                        name = "妈妈生日",
                        basis = CalendarSystem.LUNAR,
                        month = 6,
                        day = 24,
                        appDisplay = CalendarSystem.SOLAR,
                    ),
                    occurrence = Occurrence(LocalDate.of(2026, 8, 6), 23),
                    displayedDate = DisplayDate(CalendarSystem.SOLAR, "2026年8月6日"),
                ),
            ),
            onBack = {}, onNameChange = {}, onBasisChange = {}, onMonthChange = {},
            onDayChange = {}, onDisplayChange = {}, onToggleReminder = {},
            onPinnedChange = {}, onSave = {}, onDelete = {},
        )
    }
}
