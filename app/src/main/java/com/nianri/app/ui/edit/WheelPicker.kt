package com.nianri.app.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val WheelItemHeight = 48.dp
private const val VisibleWheelItems = 5

@Composable
internal fun WheelPicker(
    values: IntRange,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    valueLabel: (Int) -> String,
    pickerDescription: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    require(!values.isEmpty())
    val coercedValue = selectedValue.coerceIn(values.first, values.last)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = coercedValue - values.first,
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()

    LaunchedEffect(values.first, values.last, coercedValue) {
        if (selectedValue != coercedValue) {
            onValueChange(coercedValue)
        }
        val targetIndex = coercedValue - values.first
        if (!state.isScrollInProgress && centeredIndex(state) != targetIndex) {
            state.animateScrollToItem(targetIndex)
        }
    }
    LaunchedEffect(state, values.first, values.last) {
        snapshotFlow { centeredIndex(state) }
            .distinctUntilChanged()
            .collect { index ->
                index?.let { onValueChange(values.first + it) }
            }
    }

    Box(
        modifier = modifier
            .height(WheelItemHeight * VisibleWheelItems)
            .clip(RoundedCornerShape(12.dp))
            .testTag(testTag)
            .semantics {
                contentDescription = pickerDescription
                stateDescription = valueLabel(coercedValue)
            },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
        ) {}
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = WheelItemHeight * 2),
        ) {
            items(
                count = values.count(),
                key = { index -> values.first + index },
            ) { index ->
                val value = values.first + index
                val distance = abs(value - coercedValue)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .alpha(
                            when (distance) {
                                0 -> 1f
                                1 -> 0.55f
                                else -> 0.25f
                            },
                        )
                        .scale(if (distance == 0) 1.08f else 0.92f)
                        .testTag("$testTag-item-$value")
                        .semantics { selected = value == coercedValue }
                        .clickable(role = Role.Button) {
                            onValueChange(value)
                            scope.launch { state.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = valueLabel(value),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (distance == 0) 24.sp else 18.sp,
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun centeredIndex(state: androidx.compose.foundation.lazy.LazyListState): Int? {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { item ->
        abs(item.offset + item.size / 2 - center)
    }?.index
}
