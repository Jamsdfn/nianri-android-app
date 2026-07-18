package com.nianri.app.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
class ReminderManifestTest {
    @Test
    fun `manifest declares reminder capabilities without network or privileged exact alarm access`() {
        val context: Context = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()

        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.SCHEDULE_EXACT_ALARM in permissions)
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertFalse(Manifest.permission.INTERNET in permissions)
        assertFalse("android.permission.USE_EXACT_ALARM" in permissions)

        val sourceManifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("android.permission.VIBRATE" in sourceManifest)
        assertFalse("android.permission.WAKE_LOCK" in sourceManifest)
        assertFalse("android.permission.FOREGROUND_SERVICE" in sourceManifest)
        assertFalse("android.permission.MODIFY_AUDIO_SETTINGS" in sourceManifest)
    }
}
