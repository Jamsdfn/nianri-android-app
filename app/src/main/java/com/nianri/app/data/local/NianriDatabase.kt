package com.nianri.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ImportantDayEntity::class, WidgetPreferenceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class NianriDatabase : RoomDatabase() {
    abstract fun importantDayDao(): ImportantDayDao

    abstract fun widgetPreferenceDao(): WidgetPreferenceDao
}
