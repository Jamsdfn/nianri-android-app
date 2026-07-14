package com.nianri.app.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderMetadataTest {
    @Test
    fun `wide metadata is fixed Xiaomi 2 by 1 size`() {
        val xml = File("src/main/res/xml/nianri_wide_widget_info.xml").readText()
        assertTrue(xml.contains("android:minWidth=\"110dp\""))
        assertTrue(xml.contains("android:minHeight=\"40dp\""))
        assertTrue(xml.contains("android:targetCellWidth=\"2\""))
        assertTrue(xml.contains("android:targetCellHeight=\"1\""))
        assertCommonMetadata(xml)
    }

    @Test
    fun `square metadata is fixed Xiaomi 2 by 2 size`() {
        val xml = File("src/main/res/xml/nianri_square_widget_info.xml").readText()
        assertTrue(xml.contains("android:minWidth=\"110dp\""))
        assertTrue(xml.contains("android:minHeight=\"110dp\""))
        assertTrue(xml.contains("android:targetCellWidth=\"2\""))
        assertTrue(xml.contains("android:targetCellHeight=\"2\""))
        assertCommonMetadata(xml)
    }

    @Test
    fun `manifest exposes only owned configuration and labels both providers as Nianri`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".widget.NianriWideWidgetReceiver"))
        assertTrue(manifest.contains(".widget.NianriSquareWidgetReceiver"))
        assertTrue(manifest.contains("android:resource=\"@xml/nianri_wide_widget_info\""))
        assertTrue(manifest.contains("android:resource=\"@xml/nianri_square_widget_info\""))
        assertTrue(manifest.contains("android:label=\"@string/app_name\""))
        assertTrue(Regex("WidgetConfigActivity[\\s\\S]{0,100}android:exported=\"true\"").containsMatchIn(manifest))
        assertFalse(manifest.contains("android:label=\"念日\""))
    }

    private fun assertCommonMetadata(xml: String) {
        assertTrue(xml.contains("android:resizeMode=\"none\""))
        assertTrue(xml.contains("android:widgetFeatures=\"reconfigurable|configuration_optional\""))
        assertTrue(xml.contains("android:configure=\"com.nianri.app.widget.WidgetConfigActivity\""))
    }
}
