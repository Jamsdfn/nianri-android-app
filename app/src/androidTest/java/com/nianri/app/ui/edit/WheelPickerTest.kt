package com.nianri.app.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nianri.app.ui.theme.NianriTheme
import org.junit.Rule
import org.junit.Test

class WheelPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedValueIsCenteredAndAdjacentValueCanBeTapped() {
        var selected by mutableIntStateOf(5)
        compose.setContent {
            NianriTheme {
                WheelPicker(
                    values = 1..12,
                    selectedValue = selected,
                    onValueChange = { selected = it },
                    valueLabel = { "$it 月" },
                    pickerDescription = "月份选择器",
                    testTag = "test-wheel",
                )
            }
        }

        compose.onNodeWithContentDescription("月份选择器").assertIsDisplayed()
        compose.onNodeWithTag("test-wheel-item-5").assertIsSelected()
        compose.onNodeWithTag("test-wheel-item-6").performClick()
        compose.waitUntil { selected == 6 }
        compose.onNodeWithTag("test-wheel-item-6").assertIsSelected()
    }

    @Test
    fun selectedValueIsCoercedIntoRange() {
        var selected by mutableIntStateOf(31)
        compose.setContent {
            NianriTheme {
                WheelPicker(
                    values = 1..30,
                    selectedValue = selected,
                    onValueChange = { selected = it },
                    valueLabel = { "$it 日" },
                    pickerDescription = "日期选择器",
                    testTag = "test-wheel",
                )
            }
        }

        compose.waitUntil { selected == 30 }
        compose.onNodeWithTag("test-wheel-item-30").assertIsSelected()
    }
}
