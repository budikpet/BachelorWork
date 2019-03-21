package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class MainActivityViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val events = MutableLiveData<List<Event>>()

    @Inject
    internal lateinit var repository: Repository

    private var compositeDisposable = CompositeDisposable()

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        compositeDisposable.clear()
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        repository.checkAuthorization(response, exception)
    }

    fun signOut() {
        repository.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Event>> {
        return events
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        val disposable = repository.searchSiriusApiEvents(itemType, id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error}") }
            )

        compositeDisposable.add(disposable)
    }

    // MARK: Google Calendar

    fun updateAllCalendars() {

    }

    fun getGoogleCalendarList() {
        val disposable = repository.getGoogleCalendarList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "GetCalendarEvents")
                    for (item in result.items) {
                        Log.i(TAG, "Name: ${item.summary}")
                    }
                },
                { error ->
                    Log.e(TAG, "GetCalendarEvents: $error")
                }
            )

        compositeDisposable.add(disposable)
    }

    /**
     * Gets a list of calendar display names and ids using the android calendar provider.
     */
    fun getLocalCalendarList() {
        val disposable = repository.getLocalCalendarList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "$result")
                },
                { error ->
                    Log.e(TAG, "GetCalendarEvents: $error")
                }
            )

        compositeDisposable.add(disposable)
    }

    fun getGoogleCalendarEvents(calId: Int) {
        val disposable = repository.getGoogleCalendarEvents(calId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "AddGoogleCalendar")
                    Log.i(TAG, result.toString())
                },
                { error ->
                    Log.e(TAG, "AddGoogleCalendar: ${error}")
                })
        compositeDisposable.add(disposable)
    }

    fun addSecondaryGoogleCalendar(name: String) {
        val disposable = repository.addSecondaryGoogleCalendar(name)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "AddSecondaryCalendar: $exception")
                exception is TimeoutException
            }
            .subscribe {
                Log.i(TAG, "Calendar added successfully.")
            }

        compositeDisposable.add(disposable)
    }

    fun addGoogleCalendarEvent() {
        val dateStart = DateTime().withDate(2019, 3, 20).withTime(10, 0, 0, 0)
        val dateEnd = DateTime().withDate(2019, 3, 20).withTime(11, 30, 0, 0)

        val timetableEvent = TimetableEvent(
            5, "T9:105", acronym = "BI-BIJ", capacity = 180,
            event_type = EventType.LECTURE, fullName = "Bijec", teachers = arrayListOf("kalvotom"),
            starts_at = dateStart, ends_at = dateEnd
        )

        val disposable = repository.addGoogleCalendarEvent(3, timetableEvent)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "addGoogleCalendarEvent")
                    Log.i(TAG, "Event id: $result")
                },
                { error ->
                    Log.e(TAG, "addGoogleCalendarEvent: ${error}")
                })

        compositeDisposable.add(disposable)
    }
}