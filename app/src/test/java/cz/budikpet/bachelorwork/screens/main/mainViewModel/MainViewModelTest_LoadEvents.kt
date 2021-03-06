package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.data.models.Links
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.util.listsEqual
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Observable
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_LoadEvents: BaseMainViewModelTest() {

    private val testObserver = mock<Observer<List<TimetableEvent>>>()

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)
    }

    @After
    override fun clear() {
        assert(viewModel.compositeDisposable.size() > 0)
        super.clear()
    }

    @Test
    fun loadEvents_loadFromCalendar() {
        // Data
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val calendars = listOf(
            CalendarListItem(11L, MyApplication.calendarNameFromId(username), true),
            CalendarListItem(12L, MyApplication.calendarNameFromId("${username}_test"), true),
            CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)
        )

        val result = arrayListOf(
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
        assert(listsEqual(viewModel.events.value!!, result))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!!.number == 0)

        verify(testObserver, times(1)).onChanged(any())
    }

    @Test
    fun loadEvents_loadFromSirius() {
        // Data
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val calendars = listOf(
            CalendarListItem(12L, MyApplication.calendarNameFromId("${username}_test"), true),
            CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)
        )

        val siriusEvents =

            listOf(
                Event(
                    deleted = false,
                    id = 0,
                    starts_at = start.toDate(),
                    ends_at = end.toDate(),
                    links = Links(course = "TestEvent1", teachers = arrayListOf())
                ),
                Event(
                    deleted = true,
                    id = 1,
                    starts_at = start.toDate(),
                    ends_at = end.toDate(),
                    links = Links(course = "TestEvent2", teachers = arrayListOf())
                )
            )


        val result = arrayListOf(
            TimetableEvent.from(siriusEvents[0]),
            TimetableEvent.from(siriusEvents[1])
        )

        // Stubs

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(result))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.fromIterable(siriusEvents))

        viewModel.events.observeForever(testObserver)
        viewModel.loadEvents(start.plusDays(1))

        // Asserts

        assert(viewModel.events.value != null)
        assert(viewModel.events.value!!.count() == result.count())
        assert(viewModel.events.value!!.any { it.deleted })
        assert(listsEqual(viewModel.events.value!!, result))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!!.number == 0)

        verify(testObserver, times(1)).onChanged(any())
    }

    @Test
    fun loadEvents_error() {
        // Data
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val calendars = listOf(
            CalendarListItem(12L, MyApplication.calendarNameFromId("${username}_test"), true),
            CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)
        )

        // Stubs

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        viewModel.events.observeForever(testObserver)
        viewModel.loadEvents(start.plusDays(1))

        // Asserts

        assert(viewModel.events.value == null)
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!!.number == 0)

        verify(testObserver, times(0)).onChanged(any())
    }

}