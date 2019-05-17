package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import com.nhaarman.mockitokotlin2.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Observable
import io.reactivex.Single
import org.joda.time.*
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_FreeTimeEvents : BaseMainViewModelTest() {

    val testObserver = mock<Observer<List<TimetableEvent>>>()

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)
    }

    @Test
    fun getFreeTimeEvents_sirius() {
        // Data
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val calendars = listOf(
            CalendarListItem(11L, MyApplication.calendarNameFromId(username), true),
            CalendarListItem(12L, MyApplication.calendarNameFromId("${username}_test"), true),
            CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)
        )

        val result = listOf(
            TimetableEvent(siriusId = 5, fullName = "TestEvent1", starts_at = start, ends_at = end),
            TimetableEvent(siriusId = 6, fullName = "TestEvent2", starts_at = start, ends_at = end),
            TimetableEvent(fullName = "TestEvent3", starts_at = start, ends_at = end),
            TimetableEvent(fullName = "TestEvent4", starts_at = start, ends_at = end, deleted = true)
        )

        // Stubs
        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(result))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.empty())

        viewModel.events.observeForever(testObserver)
        viewModel.loadEvents(start.plusDays(1))

        // Asserts
        assert(viewModel.events.value != null)
        assert(viewModel.events.value!!.count() == result.count())
        assert(viewModel.events.value!!.any { it.deleted })
        assert(viewModel.events.value!! == result)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)

        verify(repository, times(1)).getSiriusEventsOf(any(), any(), any(), any())
    }

    @Test
    fun getFreeTimeEvents_calendar() {
        // Data
        val startTime = LocalTime(7, 30, 0)
        val endTime = LocalTime(21, 0, 0)
        val start = DateTime().withTimeAtStartOfDay()
        val end = DateTime().plusDays(2).withTimeAtStartOfDay()
        val timetables = arrayListOf(
            SearchItem("budikpet", type = ItemType.PERSON),
            SearchItem("balikm", type = ItemType.PERSON)
        )
        val rangeSet = initRangeSet(startTime, endTime, start, end)

        val budikpetEvents = arrayListOf(
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
            TimetableEvent(fullName = "PTestEvent4", starts_at = start.plusHours(13), ends_at = start.plusHours(23), deleted = true)
        )

        val balikmEvents = arrayListOf(
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

        val result = getResult(rangeSet, allEvents)

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
        assert(viewModel.freeTimeEvents.value!! == result)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
        assert(viewModel.compositeDisposable.size() > 0)
    }

    private fun getResult(rangeSet: TreeRangeSet<DateTime>, allEvents: ArrayList<TimetableEvent>): ArrayList<TimetableEvent> {
        val pairs = allEvents
            .filter { !it.deleted }
            .map { Pair(it.starts_at, it.ends_at) }
        for(pair in pairs) {
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
        for(event in result) {
            println("${event.starts_at.toString("HH:mm")} - ${event.ends_at.toString("HH:mm")}")
        }
    }

}