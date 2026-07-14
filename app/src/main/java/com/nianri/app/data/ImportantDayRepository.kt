package com.nianri.app.data

import androidx.room.withTransaction
import com.nianri.app.data.local.ImportantDayEntity
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.time.Month
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ImportantDayRepository(
    private val database: NianriDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.importantDayDao()

    fun observeAll(): Flow<List<ImportantDay>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun get(id: Long): ImportantDay? = dao.get(id)?.toDomain()

    suspend fun save(day: ImportantDay): Long {
        validate(day)
        val timestamp = now()
        val existing = if (day.id == 0L) null else dao.get(day.id)
        val entity = day.toEntity(
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
        val insertedId = if (day.isPinned) {
            database.withTransaction {
                dao.clearPinned()
                dao.upsert(entity)
            }
        } else {
            dao.upsert(entity)
        }
        return day.id.takeIf { it != 0L } ?: insertedId
    }

    suspend fun updateAppDisplay(id: Long, display: CalendarSystem) {
        dao.updateAppDisplay(id, display)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
    }

    private fun validate(day: ImportantDay) {
        require(day.name.isNotBlank()) { "Name must not be blank" }
        require(day.month in 1..12) { "Month must be between 1 and 12" }
        val maximumDay = when (day.basis) {
            CalendarSystem.SOLAR -> Month.of(day.month).maxLength()
            CalendarSystem.LUNAR -> 30
        }
        require(day.day in 1..maximumDay) { "Day is invalid for the selected month" }
        require(day.reminders.all { it in REMINDER_BITS }) { "Unsupported reminder offset" }
    }

    private fun ImportantDay.toEntity(createdAt: Long, updatedAt: Long) = ImportantDayEntity(
        id = id,
        name = name,
        basis = basis,
        month = month,
        day = day,
        appDisplay = appDisplay,
        reminderMask = reminders.fold(0) { mask, offset -> mask or REMINDER_BITS.getValue(offset) },
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ImportantDayEntity.toDomain() = ImportantDay(
        id = id,
        name = name,
        basis = basis,
        month = month,
        day = day,
        appDisplay = appDisplay,
        reminders = REMINDER_BITS
            .filterValues { bit -> reminderMask and bit != 0 }
            .keys,
        isPinned = isPinned,
    )

    private companion object {
        val REMINDER_BITS = linkedMapOf(14 to 1, 7 to 2, 3 to 4)
    }
}
