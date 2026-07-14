package com.nianri.app.widget

import com.nianri.app.domain.model.CalendarSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutContractTest {
    private val content = WidgetModel.Content(
        id = 9,
        name = "一个非常非常长的重要日子名称",
        days = 23,
        basisLabel = "按农历",
        displayedDate = "新历 8月6日",
        display = CalendarSystem.SOLAR,
    )

    @Test
    fun `wide minimum layout remains exactly two compact rows`() {
        val contract = WidgetTextContract.wide(content)

        assertEquals(2, contract.rows.size)
        assertEquals("一个非常非常长的重要日子名称", contract.rows[0].leading)
        assertEquals("23天", contract.rows[0].trailing)
        assertEquals("按农历 ·", contract.rows[1].leading)
        assertEquals("新历 8/6 ↻", contract.rows[1].trailing)
        assertTrue(contract.nameSingleLineEllipsized)
        assertFalse(contract.hasSegmentedToggle)
    }

    @Test
    fun `today wording never renders zero days`() {
        val today = content.copy(days = 0)
        assertEquals("就是今天", WidgetTextContract.daysText(today.days))
    }

    @Test
    fun `square contains basis countdown and full display date control`() {
        val contract = WidgetTextContract.square(content)

        assertEquals("按农历倒计时", contract.basis)
        assertEquals("23天", contract.days)
        assertEquals("本次日期", contract.dateLabel)
        assertEquals("新历 8月6日 ↻", contract.dateControl)
    }

    @Test
    fun `deleted and unavailable recovery copy is explicit`() {
        assertEquals(listOf("这个日子已删除", "点按选择其他日子"), WidgetTextContract.missingRows)
        assertEquals(listOf("日期暂不可用", "点按编辑这个日子"), WidgetTextContract.unavailableRows)
    }
}
