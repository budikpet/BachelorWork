package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.util.listsEqual
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.GoogleAccountNotFoundException
import io.reactivex.Observable
import io.reactivex.Single
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_ManipulateEvents: BaseMainViewModelTest() {
    private val observerEventToEdit = mock<Observer<TimetableEvent?>>()
    private val observerEvents = mock<Observer<List<TimetableEvent>>>()

    private val start = DateTime().minusDays(1)
    private val end = DateTime().plusDays(2)
    private val events = arrayListOf(
        TimetableEvent(siriusId = 5, fullName = "TestEvent1", starts_at = start, ends_at = end).also { it.googleId = 2 },
        TimetableEvent(siriusId = 6, fullName = "TestEvent2", starts_at = start, ends_at = end),
        TimetableEvent(fullName = "TestEvent4", starts_at = start, ends_at = end, deleted = true),
        TimetableEvent(fullName = "TestEvent3", starts_at = start, ends_at = end).also { it.googleId = 1 }
    )

    private val calendars = listOf(
        CalendarListItem(11L, MyApplication.calendarNameFromId(username), true),
        CalendarListItem(12L, MyApplication.calendarNameFromId("${username}_test"), true),
        CalendarListItem(13L, MyApplication.calendarNameFromId("balikm"), true)
    )

    @Before
    override fun initTest() {
        super.initTest()
        reset(observerEventToEdit)

        // Stubs
        whenever(repository.getLocalCalendarListItems())
            .thenReturn(Observable.fromIterable(calendars))

        whenever(repository.getSiriusEventsOf(any(), any(), any(), any()))
            .thenReturn(Observable.error(GoogleAccountNotFoundException()))

        viewModel.events.value = events
        viewModel.events.observeForever(observerEvents)
    }

    @After
    override fun clear() {
        assert(viewModel.compositeDisposable.size() > 0)
        super.clear()
    }

    @Test
    fun manipulateEvents_remove() {
        // Data
        val removedEvent = events.last()
        val updatedEvent = events.first()

        // Remove non-Sirius event, update Sirius event
        events.remove(removedEvent)
        events.removeAt(0)
        events.add(0, updatedEvent.also { it.deleted = true })

        // Stubs
        whenever(repository.deleteCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(events))

        viewModel.eventToEdit.observeForever(observerEventToEdit)
        viewModel.removeCalendarEvent(removedEvent)
        viewModel.removeCalendarEvent(updatedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges == null)
        assert(viewModel.eventToEdit.value == null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateEvents_removeError() {
        // Data
        val removedEvent = events.last()
        val updatedEvent = events.first().also { it.deleted = true }

        // Stubs
        whenever(repository.deleteCalendarEvent(any(), any()))
            .thenReturn(Single.error(Exception()))

        whenever(repository.deleteCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        setEventToEdit(updatedEvent)
        viewModel.eventToEdit.observeForever(observerEventToEdit)
        viewModel.removeCalendarEvent(removedEvent)
        viewModel.removeCalendarEvent(updatedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges != null)
        assert(viewModel.eventToEdit.value != null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateEvents_add() {
        // Data
        val addedEvent = TimetableEvent(fullName = "TestEvent2", starts_at = start, ends_at = end)

        events.add(addedEvent)

        // Stubs
        whenever(repository.addCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(events))

        viewModel.eventToEdit.observeForever(observerEventToEdit)
        viewModel.addOrUpdateCalendarEvent(addedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges == null)
        assert(viewModel.eventToEdit.value == null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateEvents_addError() {
        // Data
        val addedEvent = TimetableEvent(fullName = "TestEvent2", starts_at = start, ends_at = end)

        // Stubs
        whenever(repository.addCalendarEvent(any(), any()))
            .thenReturn(Single.error(Exception()))

        whenever(repository.deleteCalendarEvent(any(), any()))
            .thenReturn(Single.just(1))

        viewModel.eventToEdit.observeForever(observerEventToEdit)

        setEventToEdit(addedEvent)
        viewModel.addOrUpdateCalendarEvent(addedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges != null)
        assert(viewModel.eventToEdit.value != null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateEvents_update() {
        // Data
        val updatedEvent = events.first()

        events.removeAt(0)
        events.add(updatedEvent.also { it.changed = true; it.fullName = "HelpMe" })

        // Stubs
        whenever(repository.updateCalendarEvent(any()))
            .thenReturn(Single.just(1))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(events))

        viewModel.eventToEdit.observeForever(observerEventToEdit)
        viewModel.addOrUpdateCalendarEvent(updatedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges == null)
        assert(viewModel.eventToEdit.value == null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun manipulateEvents_updateError() {
        // Data
        val updatedEvent = events.first()

        // Stubs
        whenever(repository.updateCalendarEvent(any()))
            .thenReturn(Single.error(Exception()))

        whenever(repository.getCalendarEvents(any(), any(), any()))
            .thenReturn(Observable.fromIterable(events))

        setEventToEdit(updatedEvent)
        viewModel.eventToEdit.observeForever(observerEventToEdit)
        viewModel.addOrUpdateCalendarEvent(updatedEvent)

        // Asserts
        assert(viewModel.eventToEditChanges != null)
        assert(viewModel.eventToEdit.value != null)
        assert(viewModel.events.value != null)
        assert(listsEqual(viewModel.events.value!!, events))
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    private fun setEventToEdit(timetableEvent: TimetableEvent) {
        viewModel.eventToEdit.value = timetableEvent
        viewModel.eventToEditChanges = timetableEvent
    }
}