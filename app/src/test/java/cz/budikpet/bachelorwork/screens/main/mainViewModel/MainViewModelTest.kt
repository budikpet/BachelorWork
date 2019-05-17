package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.main.mock
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.MockitoAnnotations


internal class MainViewModelTest {
    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @InjectMocks
    val repository = mock<Repository>()

    val testObserver = mock<Observer<List<TimetableEvent>>>()

    val viewModel by lazy {
        val schedulersProvider = object : BaseSchedulerProvider {
            override fun io() = Schedulers.trampoline()

            override fun computation() = Schedulers.trampoline()

            override fun ui() = Schedulers.trampoline()
        }

        MainViewModel(repository, schedulersProvider)
    }

    val username = "budikpet"

    @Before
    fun initTest() {
        MockitoAnnotations.initMocks(this);
        reset(testObserver)
        viewModel.timetableOwner.value = Pair(username, ItemType.PERSON)
        viewModel.operationsRunning.value = 0
    }

    @Test
    fun loadEvents_loadFromCalendar() {
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

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getCalendarEvents(eq(11L), any(), any()))
            .thenReturn(Observable.fromIterable(result))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.empty())

        viewModel.events.observeForever(testObserver)
        viewModel.loadEvents(start.plusDays(1))

        assert(viewModel.events.value != null)
        assert(viewModel.events.value!!.count() == result.count())
        assert(viewModel.events.value!!.any { it.deleted })
        assert(viewModel.events.value!! == result)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)

        verify(repository, times(1)).getSiriusEventsOf(any(), any(), any(), any())
    }

    @Test
    fun loadEvents_loadFromSirius() {
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


        val result = listOf(
            TimetableEvent.from(siriusEvents[0]),
            TimetableEvent.from(siriusEvents[1])
        )

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getCalendarEvents(eq(11L), any(), any()))
            .thenReturn(Observable.fromIterable(result))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.fromIterable(siriusEvents))

        viewModel.events.observeForever(testObserver)
        viewModel.loadEvents(start.plusDays(1))

        assert(viewModel.events.value != null)
        assert(viewModel.events.value!!.count() == result.count())
        assert(viewModel.events.value!!.any { it.deleted })
        assert(viewModel.events.value!! == result)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)

        verify(repository, times(2)).getSiriusEventsOf(any(), any(), any(), any())
    }

    private fun dummyEventsResult(events: List<Event>): EventsResult {
        return EventsResult(Meta(10, 0, 100), events)
    }
}