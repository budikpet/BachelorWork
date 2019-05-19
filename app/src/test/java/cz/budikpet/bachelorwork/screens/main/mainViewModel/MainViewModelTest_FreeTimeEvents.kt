package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.screens.main.util.listsEqual
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Observable
import io.reactivex.Single
import org.joda.time.*
import org.junit.After
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_FreeTimeEvents : BaseMainViewModelTest() {
    private lateinit var result: ArrayList<TimetableEvent>
    private lateinit var budikpetEvents: ArrayList<TimetableEvent>
    private lateinit var balikmEvents: ArrayList<TimetableEvent>
    private lateinit var timetables: ArrayList<SearchItem>
    private lateinit var startTime: LocalTime
    private lateinit var endTime: LocalTime
    private lateinit var start: DateTime
    private lateinit var end: DateTime

    private val testObserver = mock<Observer<List<TimetableEvent>>>()

    @After
    override fun clear() {
        assert(viewModel.compositeDisposable.size() > 0)
        super.clear()
    }

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)

        startTime = LocalTime(7, 30, 0)
        endTime = LocalTime(21, 0, 0)
        start = DateTime().withTimeAtStartOfDay()
        end = DateTime().plusDays(2).withTimeAtStartOfDay()
        timetables = arrayListOf(
            SearchItem("budikpet", type = ItemType.PERSON),
            SearchItem("balikm", type = ItemType.PERSON)
        )
        val rangeSet = initRangeSet(startTime, endTime, start, end)

        budikpetEvents = arrayListOf(
            TimetableEvent(
                siriusId = 5,
                fullName = "PTestEvent1",
                starts_at = start.plusHours(3),
                ends_at = start.plusHours(8)
            ),
            TimetableEvent(
                siriusId = 6,
                fullName = "PTestEvent2",
                starts_at = start.plusHours(10),
                ends_at = start.plusHours(11)
            ),
            TimetableEvent(fullName = "PTestEvent3", starts_at = start.plusHours(21), ends_at = start.plusHours(22)),
            TimetableEvent(
                fullName = "PTestEvent4",
                starts_at = start.plusHours(13),
                ends_at = start.plusHours(23),
                deleted = true
            )
        )

        balikmEvents = arrayListOf(
            TimetableEvent(
                siriusId = 5,
                fullName = "BTestEvent1",
                starts_at = start.plusHours(8),
                ends_at = start.plusHours(9)
            ),
            TimetableEvent(
                siriusId = 6,
                fullName = "BTestEvent2",
                starts_at = start.plusHours(12),
                ends_at = start.plusHours(14)
            ),
            TimetableEvent(fullName = "BTestEvent3", starts_at = start.plusHours(17), ends_at = start.plusHours(18))
        )

        val allEvents = arrayListOf<TimetableEvent>()
        allEvents.addAll(budikpetEvents)
        allEvents.addAll(balikmEvents)

        result = getResult(rangeSet, allEvents)
    }

    @Test
    fun getFreeTimeEvents_error() {
        // Data
        val rangeSet = initRangeSet(startTime, endTime, start, end)
        val allEvents = arrayListOf<TimetableEvent>()
        allEvents.addAll(budikpetEvents)

        result = getResult(rangeSet, allEvents)

        // Stubs
        viewModel.savedTimetables.value = arrayListOf(timetables.first())

        whenever(repository.getLocalCalendarListItem(eq("budikpet")))
            .thenReturn(Single.just(CalendarListItem(12L, MyApplication.calendarNameFromId("budikpet"), true)))
        whenever(repository.getCalendarEvents(eq(12L), any(), any()))
            .thenReturn(Observable.fromIterable(budikpetEvents))

        whenever(repository.getSiriusEventsOf(eq(ItemType.PERSON), eq("balikm"), any(), any()))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        viewModel.events.observeForever(testObserver)
        viewModel.getFreeTimeEvents(start, end, startTime, endTime, timetables)

        // Asserts

        assert(viewModel.freeTimeEvents.value != null)
        assert(listsEqual(viewModel.freeTimeEvents.value!!, result))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun getFreeTimeEvents_both() {
        // Data
        val balikmEvents = listOf(
            Event(
                deleted = false,
                id = 5,
                starts_at = start.plusHours(8).toDate(),
                ends_at = start.plusHours(9).toDate(),
                links = Links(course = "BTestEvent1", teachers = arrayListOf())
            ),
            Event(
                deleted = false,
                id = 6,
                starts_at = start.plusHours(12).toDate(),
                ends_at = start.plusHours(14).toDate(),
                links = Links(course = "BTestEvent2", teachers = arrayListOf())
            ),
            Event(
                deleted = false,
                id = 7,
                starts_at = start.plusHours(17).toDate(),
                ends_at = start.plusHours(18).toDate(),
                links = Links(course = "BTestEvent3", teachers = arrayListOf())
            )
        )

        // Stubs
        viewModel.savedTimetables.value = arrayListOf(timetables.first())

        whenever(repository.getLocalCalendarListItem(eq("budikpet")))
            .thenReturn(Single.just(CalendarListItem(12L, MyApplication.calendarNameFromId("budikpet"), true)))
        whenever(repository.getCalendarEvents(eq(12L), any(), any()))
            .thenReturn(Observable.fromIterable(budikpetEvents))

        whenever(repository.getSiriusEventsOf(eq(ItemType.PERSON), eq("balikm"), any(), any()))
            .thenReturn(Observable.fromIterable(balikmEvents))

        viewModel.events.observeForever(testObserver)
        viewModel.getFreeTimeEvents(start, end, startTime, endTime, timetables)

        // Asserts

        assert(viewModel.freeTimeEvents.value != null)
        assert(listsEqual(viewModel.freeTimeEvents.value!!, result))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun getFreeTimeEvents_calendarOnly() {
        // Stubs
        viewModel.savedTimetables.value = timetables

        whenever(repository.getLocalCalendarListItem(eq("budikpet")))
            .thenReturn(Single.just(CalendarListItem(12L, MyApplication.calendarNameFromId("budikpet"), true)))
        whenever(repository.getCalendarEvents(eq(12L), any(), any()))
            .thenReturn(Observable.fromIterable(budikpetEvents))

        whenever(repository.getLocalCalendarListItem(eq("balikm")))
            .thenReturn(Single.just(CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)))
        whenever(repository.getCalendarEvents(eq(13L), any(), any()))
            .thenReturn(Observable.fromIterable(balikmEvents))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        viewModel.events.observeForever(testObserver)
        viewModel.getFreeTimeEvents(start, end, startTime, endTime, timetables)

        // Asserts

        assert(viewModel.freeTimeEvents.value != null)
        assert(listsEqual(viewModel.freeTimeEvents.value!!, result))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    private fun getResult(
        rangeSet: TreeRangeSet<DateTime>,
        allEvents: ArrayList<TimetableEvent>
    ): ArrayList<TimetableEvent> {
        val pairs = allEvents
            .filter { !it.deleted }
            .map { Pair(it.starts_at, it.ends_at) }
        for (pair in pairs) {
            val range = Range.closed(pair.first, pair.second)
            rangeSet.remove(range)
        }

        val events = arrayListOf<TimetableEvent>()
        for (range in rangeSet.asRanges()) {
            val startsAt = range.lowerEndpoint()
            val endsAt = range.upperEndpoint()
            val hours = Hours.hoursBetween(startsAt, endsAt).hours
            val minutes = Minutes.minutesBetween(startsAt, endsAt).minutes

            var acronym = ""
            if (minutes % 60 != 0) {
                acronym = "${minutes % 60} m"
            }
            if (hours > 0) {
                acronym = "$hours h $acronym"
            }

            val event = TimetableEvent(starts_at = startsAt, ends_at = endsAt, acronym = acronym)
            events.add(event)
        }

        return events
    }


    private fun initRangeSet(
        startTime: LocalTime,
        endTime: LocalTime,
        weekStart: DateTime,
        weekEnd: DateTime
    ): TreeRangeSet<DateTime> {
        val numOfDays = Days.daysBetween(weekStart.toLocalDate(), weekEnd.toLocalDate()).days

        val rangeSet = TreeRangeSet.create<DateTime>()
        for (i in 0 until numOfDays) {
            val range = Range.closed(weekStart.plusDays(i).withTime(startTime), weekStart.plusDays(i).withTime(endTime))
            rangeSet.add(range)
        }

        return rangeSet
    }

    private fun printTimes(result: ArrayList<TimetableEvent>) {
        println()
        println()
        for (event in result) {
            println("${event.starts_at.toString("HH:mm")} - ${event.ends_at.toString("HH:mm")}")
        }
    }

}