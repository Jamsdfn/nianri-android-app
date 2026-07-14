package com.nianri.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetUpdatesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `owning provider receives explicit update for only the requested id`() = runBlocking {
        val broadcasts = mutableListOf<Intent>()
        val provider = ComponentName(context.packageName, "WideReceiver")
        val updater = AndroidWidgetInstanceUpdater(
            context = context,
            providerResolver = { provider },
            broadcastDispatcher = broadcasts::add,
        )

        updater.update(701)

        assertEquals(1, broadcasts.size)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, broadcasts.single().action)
        assertEquals(provider, broadcasts.single().component)
        assertNull(broadcasts.single().`package`)
        assertArrayEquals(
            intArrayOf(701),
            broadcasts.single().getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
        )
    }

    @Test
    fun `unbound widget update stays inside the app package`() = runBlocking {
        val broadcasts = mutableListOf<Intent>()
        val updater = AndroidWidgetInstanceUpdater(
            context = context,
            providerResolver = { null },
            broadcastDispatcher = broadcasts::add,
        )

        updater.update(702)

        assertEquals(context.packageName, broadcasts.single().`package`)
        assertNull(broadcasts.single().component)
        assertArrayEquals(
            intArrayOf(702),
            broadcasts.single().getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
        )
    }

    @Test
    fun `foreign provider is rejected without broadcasting`() = runBlocking {
        val broadcasts = mutableListOf<Intent>()
        val updater = AndroidWidgetInstanceUpdater(
            context = context,
            providerResolver = { ComponentName("foreign.package", "ForeignReceiver") },
            broadcastDispatcher = broadcasts::add,
        )

        updater.update(703)

        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun `missing day decision stays open clears selection and explains retry`() {
        val decision = WidgetConfigSaveDecision.from(
            selectedId = 704,
            result = WidgetConfigurationResult.MissingDay,
        )

        assertEquals(false, decision.completed)
        assertEquals(0L, decision.selectedId)
        assertEquals("这个日子刚刚被删除，请重新选择", decision.error)
    }

    @Test
    fun `saved decision completes without changing selection`() {
        val decision = WidgetConfigSaveDecision.from(
            selectedId = 705,
            result = WidgetConfigurationResult.Saved,
        )

        assertEquals(true, decision.completed)
        assertEquals(705L, decision.selectedId)
        assertNull(decision.error)
    }

    @Test
    fun `ownership loss decision cancels configuration without retry copy`() {
        val decision = WidgetConfigSaveDecision.from(
            selectedId = 706,
            result = WidgetConfigurationResult.NotOwned,
        )

        assertEquals(false, decision.completed)
        assertEquals(true, decision.cancelActivity)
        assertEquals(0L, decision.selectedId)
        assertNull(decision.error)
    }

    @Test
    fun `bulk refresh continues after ordinary failures and aggregates them`() {
        val updated = mutableListOf<Int>()

        val error = try {
            runBlocking {
                updateWidgetInstances(listOf(1, 2, 3)) { id ->
                    updated += id
                    if (id == 1) error("first")
                    if (id == 3) error("third")
                }
            }
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(listOf(1, 2, 3), updated)
        assertEquals("first", error?.message)
        assertEquals(listOf("third"), error?.suppressed?.map { it.message })
    }

    @Test
    fun `bulk refresh immediately preserves coroutine cancellation`() {
        val updated = mutableListOf<Int>()

        try {
            runBlocking {
                updateWidgetInstances(listOf(1, 2, 3)) { id ->
                    updated += id
                    if (id == 2) throw CancellationException("stopped")
                }
            }
        } catch (_: CancellationException) {
            // Expected.
        }

        assertEquals(listOf(1, 2), updated)
    }
}
