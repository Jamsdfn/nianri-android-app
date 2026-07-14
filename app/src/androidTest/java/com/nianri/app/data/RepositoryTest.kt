package com.nianri.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private lateinit var database: NianriDatabase
    private lateinit var days: ImportantDayRepository
    private lateinit var widgets: WidgetRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NianriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        days = ImportantDayRepository(database)
        widgets = WidgetRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertUpdateAndDeleteRoundTripsDomainValues() = runBlocking {
        val id = days.save(day(name = "妈妈生日", reminders = setOf(14, 3)))

        assertEquals(
            day(id = id, name = "妈妈生日", reminders = setOf(14, 3)),
            days.get(id),
        )
        assertEquals(5, database.importantDayDao().get(id)?.reminderMask)

        days.save(day(id = id, name = "妈妈农历生日", basis = CalendarSystem.LUNAR))
        assertEquals("妈妈农历生日", days.get(id)?.name)
        assertEquals(CalendarSystem.LUNAR, days.get(id)?.basis)

        days.delete(id)
        assertNull(days.get(id))
    }

    @Test
    fun savingSecondPinnedDayClearsFirstPinnedDay() = runBlocking {
        val firstId = days.save(day(name = "第一个", isPinned = true))
        val secondId = days.save(day(name = "第二个", isPinned = true))

        assertEquals(false, days.get(firstId)?.isPinned)
        assertEquals(true, days.get(secondId)?.isPinned)
    }

    @Test
    fun replacingPinnedDayNeverEmitsAnIntermediateUnpinnedState() = runBlocking {
        days.save(day(name = "第一个", isPinned = true))
        val initialEmission = CompletableDeferred<Unit>()
        val replacementEmission = CompletableDeferred<Unit>()
        val emissions = mutableListOf<List<ImportantDay>>()
        val observation = launch {
            days.observeAll().collect { emission ->
                emissions += emission
                initialEmission.complete(Unit)
                if (emission.any { it.name == "第二个" }) {
                    replacementEmission.complete(Unit)
                }
            }
        }
        initialEmission.await()

        days.save(day(name = "第二个", isPinned = true))
        replacementEmission.await()
        observation.cancelAndJoin()

        assertTrue(emissions.size >= 2)
        assertEquals(1, emissions.first().count { it.isPinned })
        assertTrue(emissions.drop(1).all { emission -> emission.count { it.isPinned } == 1 })
    }

    @Test
    fun observationEmitsAfterSavedDataChanges() = runBlocking {
        val initialEmission = CompletableDeferred<Unit>()
        val emissions = async {
            days.observeAll()
                .onEach { initialEmission.complete(Unit) }
                .take(2)
                .toList()
        }
        initialEmission.await()

        val id = days.save(day(name = "观察日"))

        assertEquals(listOf(emptyList(), listOf(day(id = id, name = "观察日"))), emissions.await())
    }

    @Test
    fun widgetPreferencePersistsByAppWidgetIdAndTogglesIndependently() = runBlocking {
        val firstDayId = days.save(day(name = "纪念日"))
        val secondDayId = days.save(day(name = "生日"))
        widgets.select(41, firstDayId, CalendarSystem.SOLAR)
        widgets.select(42, secondDayId, CalendarSystem.LUNAR)

        assertEquals(
            WidgetResolution.Configured(days.get(firstDayId)!!, CalendarSystem.SOLAR),
            widgets.resolve(41),
        )
        assertEquals(CalendarSystem.LUNAR, widgets.toggleDisplay(41))
        assertEquals(
            WidgetResolution.Configured(days.get(firstDayId)!!, CalendarSystem.LUNAR),
            widgets.resolve(41),
        )
        assertEquals(
            WidgetResolution.Configured(days.get(secondDayId)!!, CalendarSystem.LUNAR),
            widgets.resolve(42),
        )
        assertEquals(1, widgets.countReferences(firstDayId))
    }

    @Test
    fun deletingDayKeepsWidgetPreferenceAsMissingDay() = runBlocking {
        val dayId = days.save(day(name = "将被删除"))
        widgets.select(99, dayId, CalendarSystem.SOLAR)

        days.delete(dayId)

        assertTrue(widgets.resolve(99) is WidgetResolution.MissingDay)
        assertEquals(1, widgets.countReferences(dayId))
        assertTrue(widgets.resolve(100) is WidgetResolution.Unconfigured)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRejectsBlankName() {
        runBlocking {
            days.save(day(name = " "))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRejectsInvalidMonth() {
        runBlocking {
            days.save(day(name = "有效名称", month = 13))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRejectsInvalidDay() {
        runBlocking {
            days.save(day(name = "有效名称", month = 4, day = 31))
        }
    }

    @Test
    fun appDisplayUpdateChangesOnlyDisplayPreference() = runBlocking {
        var timestamp = 100L
        val repository = ImportantDayRepository(database) { timestamp }
        val original = day(name = "展示切换", reminders = setOf(7), isPinned = true)
        val id = repository.save(original)
        val storedBeforeUpdate = database.importantDayDao().get(id)!!
        timestamp = 200L

        repository.updateAppDisplay(id, CalendarSystem.LUNAR)

        assertEquals(
            storedBeforeUpdate.copy(appDisplay = CalendarSystem.LUNAR),
            database.importantDayDao().get(id),
        )
    }

    private fun day(
        id: Long = 0,
        name: String = "测试日",
        basis: CalendarSystem = CalendarSystem.SOLAR,
        month: Int = 6,
        day: Int = 18,
        appDisplay: CalendarSystem = CalendarSystem.SOLAR,
        reminders: Set<Int> = setOf(14, 7, 3),
        isPinned: Boolean = false,
    ) = ImportantDay(
        id = id,
        name = name,
        basis = basis,
        month = month,
        day = day,
        appDisplay = appDisplay,
        reminders = reminders,
        isPinned = isPinned,
    )
}
