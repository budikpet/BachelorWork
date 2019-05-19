package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.google.api.services.calendar.model.CalendarListEntry
import com.nhaarman.mockitokotlin2.*
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.screens.main.util.mock
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Test
import java.util.*


internal class MainViewModelTest_Update : BaseMainViewModelTest() {

    private val eventsObserver = mock<Observer<List<TimetableEvent>>>()

    private val personalGoogleCalendar = CalendarListEntry().also { it.summary = "${username}_CTUTimetable" }
    private val googleCalendars = arrayListOf(
        CalendarListEntry().also { it.summary = "hidden_CTUTimetable"; it.hidden = true },
        CalendarListEntry().also { it.summary = "normal_CTUTimetable" }
    )

    private val localCalendars = arrayListOf(

        CalendarListItem(3, "${username}_CTUTimetable", false),
        CalendarListItem(1, "hidden_CTUTimetable", false),
        CalendarListItem(2, "normal_CTUTimetable", true)
    )

    private val calendarsSearchItems = arrayListOf(
        SearchItem("${username}", type = ItemType.PERSON),
        SearchItem("hidden", type = ItemType.PERSON),
        SearchItem("normal", type = ItemType.PERSON)
    )

    private val siriusEvents = arrayListOf(
        Event(id = 1, links = Links(course = "E1", room = "T9:311")),
        Event(id = 2, links = Links(course = "FieldChange", room = "T9:322")),
        Event(id = 3, links = Links(course = "E3", room = "T9:333")),
        Event(id = 5, links = Links(course = "NewEvent", room = "T9:355"))    // New event, added
    )

    @Before
    override fun initTest() {
        super.initTest()
        reset(eventsObserver)

        whenever(repository.refreshCalendars())
            .thenReturn(Completable.complete())
    }

    @Test
    fun update_prepareLocalCalendarsForUpdate() {
        // Stubs
        val completableObserver = TestObserver<Completable>()

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(localCalendars))

        prepareLocalCalendarsForUpdateStubs()

        viewModel.prepareLocalCalendarsForUpdate().subscribe(completableObserver)

        // Asserts
        completableObserver.assertComplete()
        completableObserver.assertNoErrors()

        assert(viewModel.thrownException.value == null)

        verify(repository, times(1)).updateGoogleCalendarList(any())
        verify(repository, times(1)).addGoogleCalendar(any())
        verify(repository, times(2)).updateLocalCalendarListItem(any())
    }

    private fun prepareLocalCalendarsForUpdateStubs() {
        whenever(repository.updateLocalCalendarListItem(any()))
            .doReturn(Single.just(1))

        whenever(repository.updateGoogleCalendarList(any()))
            .thenReturn(Single.just(CalendarListEntry()))

        whenever(repository.addGoogleCalendar(any()))
            .thenReturn(Completable.complete())

        whenever(repository.getGoogleCalendarList())
            .thenReturn(Observable.fromIterable(googleCalendars))
    }

    @Test
    fun update_usernameProvided() {
        // Data
        val completableObserver = TestObserver<Completable>()
        val checkedLocalCalendars = localCalendars.map { it.copy(syncEvents = true) }

        val googleEvents = arrayListOf(
            // Removed in Sirius, delete completely
            TimetableEvent(siriusId = 4, acronym = "DeletedSiriusEvent", room = "T9:333"),
            // Not Sirius event, ignored
            TimetableEvent(acronym = "UserEvent", room = "T9:333")
        )


        loop@ for(event in siriusEvents) {
            val timetableEvent = TimetableEvent.from(event)

            when(timetableEvent.siriusId) {
                2 -> {
                    // Changed by user, ignore
                    timetableEvent.changed = true
                    timetableEvent.note = "AddedNote"
                }
                3 -> {
                    // Changed by user, ignore
                    timetableEvent.acronym = "ConstructorChange"
                    timetableEvent.changed = true
                }
                5 -> break@loop
            }


            googleEvents.add(timetableEvent)
        }


        // Stubs
        prepareLocalCalendarsForUpdateStubs()

        whenever(repository.searchSirius(any()))
            .thenReturn(Observable.fromIterable(calendarsSearchItems))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.fromIterable(siriusEvents))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(googleEvents))

        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(checkedLocalCalendars), Observable.fromIterable(localCalendars))

        whenever(repository.deleteCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        whenever(repository.addCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        viewModel.updateCalendarsCompletable(username).subscribe(completableObserver)

        // Asserts
        completableObserver.assertComplete()
        completableObserver.assertNoErrors()

        verify(repository, times(1)).deleteCalendarEvent(any(), any())
        verify(repository, times(1)).addCalendarEvent(any(), any())

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }
}