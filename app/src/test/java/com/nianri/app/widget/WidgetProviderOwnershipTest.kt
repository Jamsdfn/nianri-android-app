package com.nianri.app.widget

import android.content.ComponentName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetProviderOwnershipTest {
    private val wide = ComponentName("com.nianri.app", "com.nianri.app.widget.NianriWideWidgetReceiver")
    private val square = ComponentName("com.nianri.app", "com.nianri.app.widget.NianriSquareWidgetReceiver")

    @Test
    fun `only ids owned by either Nianri provider are accepted`() {
        val providers = mapOf(41 to wide, 42 to square)
        val ownership = WidgetProviderOwnership(
            wideProvider = wide,
            squareProvider = square,
            providerForId = providers::get,
        )

        assertTrue(ownership.owns(41))
        assertTrue(ownership.owns(42))
        assertFalse(ownership.owns(43))
        assertFalse(ownership.owns(-1))
    }

    @Test
    fun `foreign and same-package non-widget providers are rejected`() {
        val ownership = WidgetProviderOwnership(
            wideProvider = wide,
            squareProvider = square,
            providerForId = { id ->
                when (id) {
                    51 -> ComponentName("foreign.package", "ForeignWidget")
                    else -> ComponentName("com.nianri.app", "com.nianri.app.reminder.ReminderReceiver")
                }
            },
        )

        assertFalse(ownership.owns(51))
        assertFalse(ownership.owns(52))
    }
}
