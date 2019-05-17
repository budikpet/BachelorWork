package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.google.api.services.calendar.model.AclRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_ShareCalendars : BaseMainViewModelTest() {

    val testObserver = mock<Observer<ArrayList<String>>>()

    val calendarName = MyApplication.calendarNameFromId(username)
    val emails = arrayListOf("budikpet@fit.cvut.cz", "balikm@fit.cvut.cz")

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)
    }

    @Test
    fun shareCalendars_update() {
        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.fromIterable(emails))

        viewModel.emails.observeForever(testObserver)
        viewModel.updateSharedEmails(username)

        // Asserts
        assert(viewModel.emails.value != null)
        assert(viewModel.emails.value!! == emails)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun shareCalendars_updateError() {
        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        viewModel.emails.observeForever(testObserver)
        viewModel.updateSharedEmails(username)

        // Asserts
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun shareCalendars_share() {
        // Data
        val newEmail = "test@gmail.com"

        emails.add(newEmail)

        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.fromIterable(emails))
        whenever(repository.sharePersonalCalendar(any()))
            .thenReturn(Single.just(AclRule()))

        viewModel.emails.observeForever(testObserver)
        viewModel.shareTimetable(newEmail, username)

        // Asserts
        assert(viewModel.emails.value != null)
        assert(viewModel.emails.value!! == emails)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun shareCalendars_shareError() {
        // Data
        val newEmail = "test@gmail.com"

        emails.add(newEmail)

        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.fromIterable(emails))
        whenever(repository.sharePersonalCalendar(any()))
            .thenReturn(Single.error(NoInternetConnectionException()))

        viewModel.emails.observeForever(testObserver)
        viewModel.shareTimetable(newEmail, username)

        // Asserts
        assert(viewModel.emails.value == null)
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun shareCalendars_unshare() {
        // Data
        val removedEmail = emails.last()

        emails.remove(removedEmail)

        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.fromIterable(emails))
        whenever(repository.unsharePersonalCalendar(any()))
            .thenReturn(Completable.complete())

        viewModel.emails.observeForever(testObserver)
        viewModel.unshareTimetable(removedEmail, username)

        // Asserts
        assert(viewModel.emails.value != null)
        assert(viewModel.emails.value!! == emails)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun shareCalendars_unshareError() {
        // Data
        val removedEmail = emails.last()

        // Stubs
        whenever(repository.getEmails(calendarName))
            .thenReturn(Observable.fromIterable(emails))
        whenever(repository.unsharePersonalCalendar(any()))
            .thenReturn(Completable.error(NoInternetConnectionException()))

        viewModel.emails.observeForever(testObserver)
        viewModel.unshareTimetable(removedEmail, username)

        // Asserts
        assert(viewModel.emails.value != null)
        assert(viewModel.emails.value!! == emails)
        assert(viewModel.thrownException.value != null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }
}