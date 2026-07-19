package com.nianri.app.data

import androidx.room.Room
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ImportantDayRepositoryImportTest {
    private lateinit var database: NianriDatabase
    private lateinit var repository: ImportantDayRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NianriDatabase::class.java,
        ).build()
        repository = ImportantDayRepository(database) { 1_721_382_400_000L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `batch import assigns new ids and preserves all business fields`() = runBlocking {
        val first = day("A").copy(reminders = setOf(14, 3), reminderTimeMinutes = 20 * 60 + 5)
        val second = day("B").copy(
            basis = CalendarSystem.LUNAR,
            month = 8,
            day = 15,
            appDisplay = CalendarSystem.LUNAR,
            isPinned = true,
        )

        val ids = repository.importAll(listOf(first, second))
        val stored = repository.getAll().sortedBy(ImportantDay::name)

        assertEquals(2, ids.size)
        assertTrue(ids.all { it > 0 })
        assertEquals(first.copy(id = ids[0]), stored[0])
        assertEquals(second.copy(id = ids[1]), stored[1])
    }

    @Test
    fun `invalid row prevents the entire batch`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.importAll(listOf(day("valid"), day(" ")))
            }
        }

        assertTrue(repository.getAll().isEmpty())
    }

    private fun day(name: String) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
    )
}
