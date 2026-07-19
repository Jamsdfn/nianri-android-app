package com.nianri.app.data.transfer

import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {
    private val date = LocalDate.of(2026, 7, 19)

    @Test
    fun `name without a conflict is preserved`() {
        val plan = ImportPlanner.plan(
            existing = listOf(day("已有日子")),
            incoming = listOf(day("新日子")),
            importDate = date,
        )

        assertEquals("新日子", plan.days.single().name)
        assertEquals(0, plan.renamedCount)
    }

    @Test
    fun `existing name receives dated suffix and numeric fallback`() {
        val existing = listOf(day("纪念日"), day("纪念日-20260719导入"))
        val incoming = listOf(day("纪念日"), day("纪念日"))

        val plan = ImportPlanner.plan(existing, incoming, date)

        assertEquals(
            listOf("纪念日-20260719导入-2", "纪念日-20260719导入-3"),
            plan.days.map(ImportantDay::name),
        )
        assertEquals(2, plan.renamedCount)
    }

    @Test
    fun `B pin wins`() {
        val plan = ImportPlanner.plan(
            existing = listOf(day("B", pinned = true)),
            incoming = listOf(day("A", pinned = true)),
            importDate = date,
        )

        assertFalse(plan.days.single().isPinned)
    }

    @Test
    fun `A pin is inherited when B has none`() {
        val plan = ImportPlanner.plan(
            existing = listOf(day("B")),
            incoming = listOf(day("A", pinned = true)),
            importDate = date,
        )

        assertTrue(plan.days.single().isPinned)
    }

    @Test
    fun `names are trimmed before comparing and saving`() {
        val plan = ImportPlanner.plan(
            existing = listOf(day("纪念日")),
            incoming = listOf(day(" 纪念日 ")),
            importDate = date,
        )

        assertEquals("纪念日-20260719导入", plan.days.single().name)
    }

    private fun day(name: String, pinned: Boolean = false) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
        isPinned = pinned,
    )
}
