package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.google.api.services.calendar.model.CalendarListEntry
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test

internal class MainViewModelTest_manipulateCalendars : BaseMainViewModelTest() {

    val testObserver = mock<Observer<ArrayList<SearchItem>>>()

    val calendarName = MyApplication.calendarNameFromId(username)
    val savedTimetables = arrayListOf(
        SearchItem("budikpet", type = ItemType.PERSON),
        SearchItem("balikm", type = ItemType.PERSON)
    )

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)

        viewModel.savedTimetables.value = savedTimetables
    }

    @Test
    fun manipulateCalendars_add() {
        // Data
        val addedCalendar = SearchItem("T9:343", type = ItemType.ROOM)
        val calendarName = MyApplication.calendarNameFromId(addedCalendar.id)

        savedTimetables.add(addedCalendar)

        // Stubs
        whenever(repository.getSavedTimetables())
            .thenReturn(Single.just(savedTimetables))

        whenever(repository.refreshCalendars())
            .thenReturn(Completable.complete())

        whenever(repository.addGoogleCalendar(calendarName))
            .thenReturn(Completable.complete())

        viewModel.savedTimetables.observeForever(testObserver)
        viewModel.addCalendar(calendarName)

        // Asserts
        assert(viewModel.savedTimetables.value != null)
        assert(viewModel.savedTimetables.value!! == savedTimetables)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateCalendars_addError() {
        // Data
        val addedCalendar = SearchItem("T9:343", type = ItemType.ROOM)
        val calendarName = MyApplication.calendarNameFromId(addedCalendar.id)

        // Stubs
        whenever(repository.getSavedTimetables())
            .thenReturn(Single.just(savedTimetables))

        whenever(repository.refreshCalendars())
            .thenReturn(Completable.complete())

        whenever(repository.addGoogleCalendar(calendarName))
            .thenReturn(Completable.error(NoInternetConnectionException()))

        viewModel.savedTimetables.observeForever(testObserver)
        viewModel.addCalendar(calendarName)

        // Asserts
        assert(viewModel.savedTimetables.value != null)
        assert(viewModel.savedTimetables.value!! == savedTimetables)
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateCalendars_remove() {
        // Data
        val removedCalendar = savedTimetables.first()
        val calendarName = MyApplication.calendarNameFromId(removedCalendar.id)

        savedTimetables.remove(removedCalendar)

        // Stubs
        whenever(repository.getSavedTimetables())
            .thenReturn(Single.just(savedTimetables))

        whenever(repository.refreshCalendars())
            .thenReturn(Completable.complete())

        whenever(repository.addGoogleCalendar(calendarName))
            .thenReturn(Completable.complete())

        viewModel.savedTimetables.observeForever(testObserver)
        viewModel.addCalendar(calendarName)

        // Asserts
        assert(viewModel.savedTimetables.value != null)
        assert(viewModel.savedTimetables.value!! == savedTimetables)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateCalendars_removeError() {
        // Data
        val removedCalendar = savedTimetables.first()
        val calendarName = MyApplication.calendarNameFromId(removedCalendar.id)

        // Stubs
        whenever(repository.getSavedTimetables())
            .thenReturn(Single.just(savedTimetables))

        whenever(repository.refreshCalendars())
            .thenReturn(Completable.complete())

        whenever(repository.getGoogleCalendar(calendarName))
            .thenReturn(Single.error(NoInternetConnectionException()))

        viewModel.savedTimetables.observeForever(testObserver)
        viewModel.removeCalendar(calendarName)

        // Asserts
        assert(viewModel.savedTimetables.value != null)
        assert(viewModel.savedTimetables.value!! == savedTimetables)
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }
}