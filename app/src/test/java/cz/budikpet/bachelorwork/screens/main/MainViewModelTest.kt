package cz.budikpet.bachelorwork.screens.main

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.OngoingStubbing


internal class MainViewModelTest {
    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @InjectMocks
    val repository = mock<Repository>()

    val testObserver = mock<Observer<List<TimetableEvent>>>()

    val viewmodel by lazy {
        val schedulersProvider = object : BaseSchedulerProvider {
            override fun io() = Schedulers.trampoline()

            override fun computation() = Schedulers.trampoline()

            override fun ui() = Schedulers.trampoline()
        }

        MainViewModel(repository, schedulersProvider)
    }

    val username = "budikpet"

    @Before
    fun init() {
        MockitoAnnotations.initMocks(this);
        reset(testObserver)
        viewmodel.timetableOwner.value = Pair(username, ItemType.PERSON)
        viewmodel.operationsRunning.value = 0
    }

    @Test
    fun showDataFromApi() {
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val result = listOf(
            TimetableEvent(siriusId = 5, fullName = "TestEvent1", starts_at = start, ends_at = end),
            TimetableEvent(siriusId = 6, fullName = "TestEvent2", starts_at = start, ends_at = end),
            TimetableEvent(fullName = "TestEvent3", starts_at = start, ends_at = end),
            TimetableEvent(fullName = "TestEvent4", starts_at = start, ends_at = end)
        )

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.just(CalendarListItem(11L, MyApplication.calendarNameFromId(username), true)))

        whenever(repository.getCalendarEvents(eq(11L), any(), any()))
            .thenReturn(Observable.fromIterable(result))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.empty())

        viewmodel.events.observeForever(testObserver)
        viewmodel.loadEvents(start.plusDays(1))

        assert(viewmodel.events.value != null)
        assert(viewmodel.events.value!!.count() == result.count())

    }
}

inline fun <reified T> mock() = Mockito.mock(T::class.java)