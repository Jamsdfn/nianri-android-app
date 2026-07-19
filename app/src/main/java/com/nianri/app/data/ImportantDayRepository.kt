package com.nianri.app.data

import androidx.room.withTransaction
import com.nianri.app.data.local.ImportantDayEntity
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.domain.model.requireValidImportantDay
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

    suspend fun getAll(): List<ImportantDay> = dao.getAll().map { it.toDomain() }

    suspend fun importAll(days: List<ImportantDay>): List<Long> {
        days.forEach(::requireValidImportantDay)
        require(days.count(ImportantDay::isPinned) <= 1) { "Only one day can be pinned" }
        val timestamp = now()
        return database.withTransaction {
            dao.insertAll(
                days.map { day ->
                    day.copy(id = 0).toEntity(
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    )
                },
            )
        }
    }

    suspend fun save(day: ImportantDay): Long {
        requireValidImportantDay(day)
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

    private fun ImportantDay.toEntity(createdAt: Long, updatedAt: Long) = ImportantDayEntity(
        id = id,
        name = name,
        basis = basis,
        month = month,
        day = day,
        appDisplay = appDisplay,
        reminderMask = reminders.fold(0) { mask, offset -> mask or REMINDER_BITS.getValue(offset) },
        reminderTimeMinutes = reminderTimeMinutes,
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
        reminderTimeMinutes = reminderTimeMinutes,
        isPinned = isPinned,
    )

    private companion object {
        val REMINDER_BITS = linkedMapOf(14 to 1, 7 to 2, 3 to 4)
    }
}
