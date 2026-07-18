package com.nianri.app.widget

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidnightWidgetRefreshReceiverTest {
    @Test
    fun `successful midnight refresh updates then renews then finishes`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefresh(
            updateWidgets = { calls += "widgets" },
            scheduleNext = { calls += "schedule" },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `widget failure still renews and finishes before surfacing the failure`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = {
                        calls += "widgets"
                        error("database unavailable")
                    },
                    scheduleNext = { calls += "schedule" },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `renewal failure still finishes the pending result`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = { calls += "widgets" },
                    scheduleNext = {
                        calls += "schedule"
                        error("alarm unavailable")
                    },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `cancellation still renews and finishes before cancellation is preserved`() {
        val calls = mutableListOf<String>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runMidnightWidgetRefresh(
                    updateWidgets = {
                        calls += "widgets"
                        throw CancellationException("stopped")
                    },
                    scheduleNext = { calls += "schedule" },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }
}
