package com.nianri.app.widget

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidnightWidgetRefreshReceiverTest {
    @Test
    fun `dependency resolution failure finishes before refresh begins`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefreshBroadcast(
            refresh = {
                calls += "resolve"
                error("application unavailable")
            },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("resolve", "finish"), calls)
    }

    @Test
    fun `successful midnight refresh updates then renews then finishes`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefreshBroadcast(
            refresh = {
                runMidnightWidgetRefresh(
                    updateWidgets = { calls += "widgets" },
                    scheduleNext = { calls += "schedule" },
                )
            },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `widget failure still renews and finishes once`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefreshBroadcast(
            refresh = {
                runMidnightWidgetRefresh(
                    updateWidgets = {
                        calls += "widgets"
                        error("database unavailable")
                    },
                    scheduleNext = { calls += "schedule" },
                )
            },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `renewal failure still finishes once`() = runBlocking {
        val calls = mutableListOf<String>()

        runMidnightWidgetRefreshBroadcast(
            refresh = {
                runMidnightWidgetRefresh(
                    updateWidgets = { calls += "widgets" },
                    scheduleNext = {
                        calls += "schedule"
                        error("alarm unavailable")
                    },
                )
            },
            finish = { calls += "finish" },
        )

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }

    @Test
    fun `cancellation still renews and finishes before cancellation is preserved`() {
        val calls = mutableListOf<String>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runMidnightWidgetRefreshBroadcast(
                    refresh = {
                        runMidnightWidgetRefresh(
                            updateWidgets = {
                                calls += "widgets"
                                throw CancellationException("stopped")
                            },
                            scheduleNext = { calls += "schedule" },
                        )
                    },
                    finish = { calls += "finish" },
                )
            }
        }

        assertEquals(listOf("widgets", "schedule", "finish"), calls)
    }
}
