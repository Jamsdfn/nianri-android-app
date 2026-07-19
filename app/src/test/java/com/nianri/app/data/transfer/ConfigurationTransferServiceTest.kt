package com.nianri.app.data.transfer

import androidx.room.Room
import com.nianri.app.data.ImportantDayRepository
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.WidgetUpdater
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.reminder.ReminderScheduleResult
import com.nianri.app.reminder.ReminderScheduler
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConfigurationTransferServiceTest {
    private lateinit var database: NianriDatabase
    private lateinit var repository: ImportantDayRepository
    private lateinit var codec: TransferCodec
    private lateinit var events: MutableList<String>
    private val clock = Clock.fixed(Instant.parse("2026-07-19T09:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NianriDatabase::class.java,
        ).build()
        repository = ImportantDayRepository(database)
        codec = TransferCodec()
        events = mutableListOf()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `export captures all days without local ids`() = runBlocking {
        repository.save(day("A"))
        repository.save(day("B"))

        val decoded = codec.decode(service().exportConfiguration())

        assertEquals(clock.instant(), decoded.exportedAt)
        assertEquals(setOf("A", "B"), decoded.days.mapTo(mutableSetOf(), ImportantDay::name))
        assertTrue(decoded.days.all { it.id == 0L })
    }

    @Test
    fun `import commits before rebuilding reminders and widgets`() = runBlocking {
        val service = service()

        val result = service.importConfiguration(documentJson(day("A"), day("B")))

        assertEquals(2, result.importedCount)
        assertEquals(0, result.renamedCount)
        assertFalse(result.refreshFailed)
        assertEquals(listOf("prepare:0", "reminders:2", "widgets:2"), events)
    }

    @Test
    fun `invalid document performs no mutation or refresh`() = runBlocking {
        val service = service()
        val versionTwo =
            """{"format":"nianri-configuration","version":2,"exportedAt":"2026-07-19T09:30:00Z","days":[]}"""

        assertThrows(TransferFormatException.UnsupportedVersion::class.java) {
            runBlocking { service.importConfiguration(versionTwo) }
        }

        assertTrue(repository.getAll().isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `refresh failure returns warning without deleting imported rows`() = runBlocking {
        val service = service(reminderFailure = IOException("alarm unavailable"))

        val result = service.importConfiguration(documentJson(day("A")))

        assertEquals(1, repository.getAll().size)
        assertTrue(result.refreshFailed)
        assertEquals(listOf("prepare:0", "reminders:1", "widgets:1"), events)
    }

    private fun service(reminderFailure: Exception? = null) = ConfigurationTransferService(
        days = repository,
        codec = codec,
        reminders = object : ReminderScheduler {
            override suspend fun replace(dayId: Long) = ReminderScheduleResult.Scheduled(0)

            override suspend fun cancel(dayId: Long) = Unit

            override suspend fun rebuildAll() {
                events += "reminders:${repository.getAll().size}"
                reminderFailure?.let { throw it }
            }
        },
        widgets = object : WidgetUpdater {
            override suspend fun prepareMutation() {
                events += "prepare:${repository.getAll().size}"
            }

            override suspend fun updateAll() {
                events += "widgets:${repository.getAll().size}"
            }
        },
        clock = clock,
    )

    private fun documentJson(vararg days: ImportantDay): String = codec.encode(
        TransferDocument(
            exportedAt = Instant.parse("2026-07-18T08:00:00Z"),
            days = days.toList(),
        ),
    )

    private fun day(name: String) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
    )
}
