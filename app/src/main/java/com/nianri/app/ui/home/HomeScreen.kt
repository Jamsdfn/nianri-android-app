package com.nianri.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nianri.app.domain.DayCardModel
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.ui.countdownCopy
import com.nianri.app.ui.theme.Night800
import com.nianri.app.ui.theme.Night950
import com.nianri.app.ui.theme.TextMuted
import com.nianri.app.ui.theme.TextPrimary
import com.nianri.app.ui.theme.Violet300
import com.nianri.app.ui.theme.Violet500

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAdd: () -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onToggleDisplay: (Long) -> Unit = {},
    onDismissCalendarExplanation: () -> Unit = {},
    onDisplayErrorShown: () -> Unit = {},
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.displayError) {
        val error = state.displayError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        onDisplayErrorShown()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-root")
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF353B8B), Night950),
                    radius = 1200f,
                ),
            )
            .windowInsetsPadding(safeDrawingInsets),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 28.dp,
                end = 20.dp,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "念日",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("home-title"),
                    )
                    TextButton(
                        onClick = onAdd,
                        modifier = Modifier
                            .testTag("home-add")
                            .semantics { contentDescription = "新建重要日子" },
                    ) {
                        Text("＋", fontSize = 26.sp, color = TextPrimary)
                    }
                }
            }

            if (state.showCalendarExplanation) {
                item {
                    CalendarExplanation(onDismissCalendarExplanation)
                }
            }

            state.pinned?.let { pinned ->
                item {
                    DayCard(
                        model = pinned,
                        isHero = true,
                        onOpen = onOpen,
                        onToggleDisplay = onToggleDisplay,
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item {
                    Text(
                        text = "接下来的日子",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(state.upcoming, key = { it.day.id }) { model ->
                    DayCard(
                        model = model,
                        isHero = false,
                        onOpen = onOpen,
                        onToggleDisplay = onToggleDisplay,
                    )
                }
            }

            if (state.pinned == null && state.upcoming.isEmpty()) {
                item {
                    EmptyState(onAdd)
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("home-fab")
                .size(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet500),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("＋", fontSize = 28.sp, color = TextPrimary)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun CalendarExplanation(onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFF303573),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("ⓘ", color = Violet300, modifier = Modifier.padding(top = 2.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    "切换只改变日期怎么显示",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "剩余天数始终按创建时选择的历法计算。",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "关闭说明" },
            ) {
                Text("×", color = TextMuted, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun DayCard(
    model: DayCardModel,
    isHero: Boolean,
    onOpen: (Long) -> Unit,
    onToggleDisplay: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(if (isHero) 24.dp else 16.dp)
    val background = if (isHero) {
        Brush.linearGradient(listOf(Color(0xFF3A3779), Color(0xFF242751)))
    } else {
        Brush.linearGradient(listOf(Night800, Night800))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable { onOpen(model.day.id) }
            .padding(if (isHero) 20.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = (if (model.day.basis == CalendarSystem.LUNAR) "按农历" else "按新历") + "倒计时",
                color = Violet300,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color(0x336158C2), RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
            DisplayToggle(model, onToggleDisplay)
        }

        Spacer(Modifier.height(if (isHero) 18.dp else 12.dp))
        when (model) {
            is DayCardModel.Ready -> ReadyCardBody(model, isHero)
            is DayCardModel.Unavailable -> UnavailableCardBody(model, onOpen)
        }
    }
}

@Composable
private fun DisplayToggle(
    model: DayCardModel,
    onToggleDisplay: (Long) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text("日期展示", color = TextMuted, fontSize = 10.sp)
        Row(
            modifier = Modifier
                .padding(top = 3.dp)
                .background(Color(0x6611132C), RoundedCornerShape(99.dp))
                .padding(horizontal = 2.dp)
                .selectableGroup(),
        ) {
            listOf(CalendarSystem.SOLAR to "新历", CalendarSystem.LUNAR to "农历").forEach { (calendar, label) ->
                val selected = model.day.appDisplay == calendar
                val action = if (calendar == CalendarSystem.LUNAR) "农历" else "新历"
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected) onToggleDisplay(model.day.id)
                            },
                        )
                        .semantics {
                            contentDescription = "${model.day.name}切换为${action}展示"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) TextPrimary else TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) Violet500 else Color.Transparent)
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyCardBody(model: DayCardModel.Ready, isHero: Boolean) {
    if (isHero) {
        Text(
            text = countdownCopy(model.occurrence.daysRemaining),
            color = Violet300,
            fontSize = if (model.occurrence.daysRemaining == 0L) 34.sp else 58.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = model.day.name,
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            text = model.displayedDate.text,
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            text = "仅改变日期展示 · 倒计时基准保持不变",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.day.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    model.displayedDate.text,
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                countdownCopy(model.occurrence.daysRemaining),
                color = Violet300,
                fontSize = if (model.occurrence.daysRemaining == 0L) 18.sp else 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UnavailableCardBody(model: DayCardModel.Unavailable, onOpen: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(model.day.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "日期暂不可用",
                color = TextMuted,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        TextButton(onClick = { onOpen(model.day.id) }) {
            Text("编辑", color = Violet300)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("把想念的日子放进夜空里", color = TextMuted)
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 10.dp)) {
            Text("新建重要日子", color = Violet300, fontWeight = FontWeight.SemiBold)
        }
    }
}
