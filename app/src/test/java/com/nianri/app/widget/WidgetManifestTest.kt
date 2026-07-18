package com.nianri.app.widget

import android.content.ComponentName
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetManifestTest {
    @Test
    fun `configuration activity is exported but stays in launcher task and out of recents`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declaration = Regex(
            """<activity\s+android:name=\"\.widget\.WidgetConfigActivity\"\s+android:exported=\"true\"\s+android:excludeFromRecents=\"true\"\s+android:taskAffinity=\"\"\s*/>""",
        )

        assertTrue(declaration.containsMatchIn(manifest))
    }

    @Test
    fun `midnight refresh receiver is installed and not exported`() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, MidnightWidgetRefreshReceiver::class.java),
            0,
        )

        assertFalse(receiver.exported)
    }
}
