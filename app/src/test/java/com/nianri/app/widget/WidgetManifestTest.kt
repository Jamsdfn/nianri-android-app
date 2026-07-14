package com.nianri.app.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetManifestTest {
    @Test
    fun `configuration activity is exported but stays in launcher task and out of recents`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declaration = Regex(
            """<activity\s+android:name=\"\.widget\.WidgetConfigActivity\"\s+android:exported=\"true\"\s+android:excludeFromRecents=\"true\"\s+android:taskAffinity=\"\"\s*/>""",
        )

        assertTrue(declaration.containsMatchIn(manifest))
    }
}
