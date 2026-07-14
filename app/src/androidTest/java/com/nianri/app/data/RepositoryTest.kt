package com.nianri.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nianri.app.data.local.NianriDatabase
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import com.nianri.app.widget.ConfiguredWidgetUpdater
import com.nianri.app.widget.WidgetInstanceUpdater
import com.nianri.app.widget.WidgetToggleController
import com.nianri.app.widget.WidgetConfigurationCommitter
import com.nianri.app.widget.WidgetConfigurationResult
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

    @Test
    fun removingWidgetDeletesOnlyThatWidgetPreference() = runBlocking {
        val dayId = days.save(day(name = "纪念日"))
        widgets.select(201, dayId, CalendarSystem.SOLAR)
        widgets.select(202, dayId, CalendarSystem.LUNAR)

        widgets.remove(201)

        assertTrue(widgets.resolve(201) is WidgetResolution.Unconfigured)
        assertEquals(
            WidgetResolution.Configured(days.get(dayId)!!, CalendarSystem.LUNAR),
            widgets.resolve(202),
        )
    }

    @Test
    fun selectingExistingDayAtomicallyRejectsADeletedRecord() = runBlocking {
        val dayId = days.save(day(name = "保存前删除"))
        days.delete(dayId)

        val selected = widgets.selectExistingDay(211, dayId, CalendarSystem.SOLAR)

        assertEquals(false, selected)
        assertTrue(widgets.resolve(211) is WidgetResolution.Unconfigured)
    }

    @Test
    fun selectingExistingDayAtomicallyPersistsAStillExistingRecord() = runBlocking {
        val dayId = days.save(day(name = "仍然存在"))

        val selected = widgets.selectExistingDay(212, dayId, CalendarSystem.LUNAR)

        assertEquals(true, selected)
        assertEquals(
            WidgetResolution.Configured(days.get(dayId)!!, CalendarSystem.LUNAR),
            widgets.resolve(212),
        )
    }

    @Test
    fun configurationCommitRejectedAfterClickedDayIsDeletedDoesNotUpdateWidget() = runBlocking {
        val dayId = days.save(day(name = "点击后删除"))
        val updated = mutableListOf<Int>()
        val committer = WidgetConfigurationCommitter(widgets, WidgetInstanceUpdater { updated += it })
        days.delete(dayId)

        val result = committer.commit(213, dayId, CalendarSystem.SOLAR)

        assertEquals(WidgetConfigurationResult.MissingDay, result)
        assertTrue(widgets.resolve(213) is WidgetResolution.Unconfigured)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun toggleControllerChangesAndUpdatesOnlyTheRequestedWidget() = runBlocking {
        val dayId = days.save(day(name = "纪念日"))
        widgets.select(301, dayId, CalendarSystem.SOLAR)
        widgets.select(302, dayId, CalendarSystem.LUNAR)
        val updated = mutableListOf<Int>()
        val controller = WidgetToggleController(widgets, WidgetInstanceUpdater(updated::add))

        controller.toggle(301)

        assertEquals(CalendarSystem.LUNAR, (widgets.resolve(301) as WidgetResolution.Configured).display)
        assertEquals(CalendarSystem.LUNAR, (widgets.resolve(302) as WidgetResolution.Configured).display)
        assertEquals(listOf(301), updated)
    }

    @Test
    fun toggleControllerSafelyIgnoresUnconfiguredAndMissingWidgets() = runBlocking {
        val dayId = days.save(day(name = "删除日"))
        widgets.select(312, dayId, CalendarSystem.SOLAR)
        days.delete(dayId)
        val updated = mutableListOf<Int>()
        val controller = WidgetToggleController(widgets, WidgetInstanceUpdater(updated::add))

        controller.toggle(311)
        controller.toggle(312)

        assertTrue(widgets.resolve(311) is WidgetResolution.Unconfigured)
        assertTrue(widgets.resolve(312) is WidgetResolution.MissingDay)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun configuredUpdaterRefreshesEveryStoredWidgetIncludingMissingReferences() = runBlocking {
        val present = days.save(day(name = "仍存在"))
        val deleted = days.save(day(name = "将删除"))
        widgets.select(321, present, CalendarSystem.SOLAR)
        widgets.select(322, deleted, CalendarSystem.LUNAR)
        days.delete(deleted)
        val updated = mutableListOf<Int>()
        val updater = ConfiguredWidgetUpdater(widgets, WidgetInstanceUpdater(updated::add))

        updater.updateAll()

        assertEquals(listOf(321, 322), updated.sorted())
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
