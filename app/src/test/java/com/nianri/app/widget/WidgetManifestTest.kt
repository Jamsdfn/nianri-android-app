package com.nianri.app.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetManifestTest {
    @Test
    fun `configuration activity is exported after real providers validate widget ownership`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declaration = Regex(
            """<activity\s+android:name=\"\.widget\.WidgetConfigActivity\"\s+android:exported=\"true\"\s*/>""",
        )

        assertTrue(declaration.containsMatchIn(manifest))
    }
}
