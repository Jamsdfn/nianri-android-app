package com.nianri.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ImportantDayEntity::class, WidgetPreferenceEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class NianriDatabase : RoomDatabase() {
    abstract fun importantDayDao(): ImportantDayDao

    abstract fun widgetPreferenceDao(): WidgetPreferenceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE important_days " +
                        "ADD COLUMN reminderTimeMinutes INTEGER NOT NULL DEFAULT 540",
                )
            }
        }
    }
}
