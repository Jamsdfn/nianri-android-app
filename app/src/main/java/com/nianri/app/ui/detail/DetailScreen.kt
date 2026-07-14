package com.nianri.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.DisplayDate
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.Occurrence
import com.nianri.app.ui.edit.DeleteConfirmationDialog
import com.nianri.app.ui.countdownCopy
import com.nianri.app.ui.theme.NianriTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.day?.name ?: "重要日子") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val day = state.day
            val occurrence = state.occurrence
            if (day == null) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${calendarName(day.basis)}为倒计时基准")
                        Text(
                            occurrence?.let { countdownCopy(it.daysRemaining) } ?: "日期暂不可用",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(day.name, style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (state.error != null && occurrence != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                DateRow("本次对应的新历日期", state.solarDate)
                DateRow("本次对应的农历日期", state.lunarDate)
                state.adjustmentCopy?.let {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("本次日期调整：$it", modifier = Modifier.padding(14.dp))
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("提醒", style = MaterialTheme.typography.titleMedium)
                        Text(state.reminderSummary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onEdit(day.id) }, modifier = Modifier.weight(1f)) { Text("编辑") }
                    TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            widgetReferences = state.widgetReferences,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }
}

@Composable
private fun DateRow(label: String, date: DisplayDate?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(date?.text ?: "暂不可用", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun calendarName(calendar: CalendarSystem) =
    if (calendar == CalendarSystem.SOLAR) "新历" else "农历"

@Preview(showBackground = true)
@Composable
private fun DetailScreenPreview() {
    NianriTheme {
        DetailScreen(
            state = DetailUiState(
                day = ImportantDay(
                    id = 42,
                    name = "妈妈生日",
                    basis = CalendarSystem.LUNAR,
                    month = 6,
                    day = 24,
                    appDisplay = CalendarSystem.SOLAR,
                ),
                occurrence = Occurrence(LocalDate.of(2026, 8, 6), 23),
                solarDate = DisplayDate(CalendarSystem.SOLAR, "2026年8月6日 · 星期四"),
                lunarDate = DisplayDate(CalendarSystem.LUNAR, "农历六月廿四"),
                reminderSummary = "提前 14、7、3 天",
                isLoading = false,
            ),
            onBack = {}, onEdit = {}, onDelete = {},
        )
    }
}
