package com.nianri.app.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetManifestTest {
    @Test
    fun `configuration activity stays private until providers validate widget ownership`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declaration = Regex(
            """<activity\s+android:name=\"\.widget\.WidgetConfigActivity\"\s+android:exported=\"false\"\s*/>""",
        )

        assertTrue(declaration.containsMatchIn(manifest))
    }
}
