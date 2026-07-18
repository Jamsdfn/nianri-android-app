package com.nianri.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NianriDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NianriDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesDayAndDefaultsReminderTimeToNine() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO important_days " +
                    "(id,name,basis,month,day,appDisplay,reminderMask,isPinned,createdAt,updatedAt) " +
                    "VALUES (42,'妈妈生日','SOLAR',8,6,'SOLAR',5,1,100,200)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            NianriDatabase.MIGRATION_1_2,
        ).query("SELECT * FROM important_days WHERE id = 42").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("妈妈生日", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(5, cursor.getInt(cursor.getColumnIndexOrThrow("reminderMask")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")))
            assertEquals(540, cursor.getInt(cursor.getColumnIndexOrThrow("reminderTimeMinutes")))
        }
    }

    private companion object {
        const val TEST_DB = "reminder-time-migration"
    }
}
