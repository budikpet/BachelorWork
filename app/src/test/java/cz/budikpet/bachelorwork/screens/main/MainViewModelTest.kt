package cz.budikpet.bachelorwork.screens.main

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mockito
import org.mockito.Mockito.reset
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.OngoingStubbing


internal class MainViewModelTest {
    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    val testObserver = mock<Observer<List<TimetableEvent>>>()

    @InjectMocks
    val repository = mock<Repository>()
    //    val sharedPrefs = Mockito.mock(SharedPreferences::class.java)
//    val context = mock<Context>()
//
//    val repository2 = Mockito.spy(Repository(context, sharedPrefs))
    val schedulersProvider = object : BaseSchedulerProvider {
        override fun io() = Schedulers.trampoline()

        override fun computation() = Schedulers.trampoline()

        override fun ui() = Schedulers.trampoline()
    }

    val viewmodel by lazy { MainViewModel(repository, schedulersProvider) }

    @Before
    fun init() {
        MockitoAnnotations.initMocks(this);
        reset(testObserver)
//        Mockito.`when`(
//            context.getSharedPreferences(anyString(), anyInt()))
//                .thenReturn(sharedPrefs)

    }

    @Test
    fun showDataFromApi() {
        val start = DateTime().minusDays(1)
        val end = DateTime().plusDays(2)

        val username = "budikpet"

        val result = listOf(
            TimetableEvent(siriusId = 5, fullName = "TestEvent1", starts_at = start, ends_at = end),
            TimetableEvent(siriusId = 6, fullName = "TestEvent2", starts_at = start, ends_at = end),
            TimetableEvent(fullName = "TestEvent3", starts_at = start, ends_at = end)
        )

//        Mockito.doReturn(1).`when`<Any>(contentResolver).delete(UserProvider.CONTENT_USER_URI, null, null)
//        Mockito
//            .`when`(sharedPrefs.getString(anyString(), anyString()))
//            .thenReturn(username)
//        Mockito
//            .`when`(repository2.getLocalCalendarListItem(username))
//            .thenReturn(Single.just(CalendarListItem(anyLong(), MyApplication.calendarNameFromId(username), true)))

        Mockito
            .`when`(repository.getLocalCalendarListItems())
            .thenReturn(Observable.just(CalendarListItem(11, MyApplication.calendarNameFromId(username), true)))

        Mockito
            .`when`(repository.getCalendarEvents(11, start, end))
            .thenReturn(Observable.fromIterable(result))

        viewmodel.events.observeForever(testObserver)
        viewmodel.loadEvents(start.plusDays(1))

//        val captor = argumentCaptor<List<Repo>>()

        assert(viewmodel.events.value != null)
        assert(viewmodel.events.value!!.count() == 3)
    }
}

inline fun <reified T> mock() = Mockito.mock(T::class.java)
inline fun <T> whenever(methodCall: T): OngoingStubbing<T> = Mockito.`when`(methodCall)