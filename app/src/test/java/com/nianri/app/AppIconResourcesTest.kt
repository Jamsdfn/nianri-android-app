package com.nianri.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconResourcesTest {
    @Test
    fun `manifest and launcher resources provide adaptive round and monochrome icons`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android:icon=\"@mipmap/ic_launcher\"" in manifest)
        assertTrue("android:roundIcon=\"@mipmap/ic_launcher_round\"" in manifest)

        val required = listOf(
            "res/mipmap-anydpi/ic_launcher.xml",
            "res/mipmap-anydpi/ic_launcher_round.xml",
            "res/mipmap-anydpi-v26/ic_launcher.xml",
            "res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "res/mipmap-anydpi-v33/ic_launcher.xml",
            "res/mipmap-anydpi-v33/ic_launcher_round.xml",
            "res/drawable/ic_launcher_background.xml",
            "res/drawable/ic_launcher_foreground.xml",
            "res/drawable/ic_launcher_monochrome.xml",
        )
        required.forEach { assertTrue("missing $it", File("src/main/$it").isFile) }
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            assertTrue("<monochrome" in File("src/main/res/mipmap-anydpi-v33/$name").readText())
        }
    }
}
