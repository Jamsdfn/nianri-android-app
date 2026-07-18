package com.nianri.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRefreshInitializationTest {
    @Test
    fun `application startup keeps the audit and schedules midnight refresh`() {
        val calls = mutableListOf<String>()

        initializeBackgroundRefresh(
            enqueueAudit = { calls += "audit" },
            scheduleNextMidnight = { calls += "midnight" },
        )

        assertEquals(listOf("audit", "midnight"), calls)
    }
}
