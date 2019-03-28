package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.SharedPreferences
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.data.models.GoogleCalendarListItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.collections.ArrayList

class MainActivityViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val events = MutableLiveData<List<Event>>()

    @Inject
    internal lateinit var repository: Repository

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private var compositeDisposable = CompositeDisposable()

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        compositeDisposable.clear()
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        val disposable = repository.checkAuthorization(response, exception)
            .observeOn(Schedulers.io())
            .flatMapObservable { accessToken -> repository.getLoggedUserInfo(accessToken) }
            .flatMapCompletable { userInfo ->
                if (sharedPreferences.contains(SharedPreferencesKeys.SIRIUS_USERNAME.toString())) {
                    val editor = sharedPreferences.edit()
                    editor.putString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), userInfo.username)
                    editor.apply()
                }

                Completable.complete()
            }
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .onErrorComplete { exception ->
                Log.e(TAG, "Update: $exception")
                false
            }
            .subscribe {
                Log.i(TAG, "Fully authorized & tokens restored.")
            }

        compositeDisposable.add(disposable)
    }

    fun signOut() {
        repository.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Event>> {
        return events
    }

    fun getSiriusEventsOf(itemType: ItemType, id: String) {
        val disposable = repository.getSiriusEventsOf(itemType, id)
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

    fun firstCalendarsUpdate() {
        val disposable = updateAllCalendarsFlow()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Update: $exception")
                exception is TimeoutException
            }
            .subscribe {
                Log.i(TAG, "Update done")
            }

        compositeDisposable.add(disposable)
    }

    fun updateAllCalendars() {
        val disposable = updateAllCalendarsFlow()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Update: $exception")
                exception is TimeoutException
            }
            .subscribe {
                Log.i(TAG, "Update done")
            }

        compositeDisposable.add(disposable)
    }

    /**
     * General flow which updates all used calendars.
     *
     * Gets Sirius API events of all saved calendars and updates them.
     */
    private fun updateAllCalendarsFlow(): Completable {
        return repository.refreshCalendars()
            .observeOn(Schedulers.io())
            .andThen(repository.getLocalCalendarList())
            .flatMapCompletable { calendarListItem ->
                val siriusObs = getSiriusEventsList(calendarListItem)

                val calendarObs = getGoogleCalendarEventsList(calendarListItem)

                val updateObs = Observable.zip(siriusObs, calendarObs,
                    BiFunction { siriusEvents: ArrayList<TimetableEvent>, calendarEvents: ArrayList<TimetableEvent> ->
                        Pair(siriusEvents, calendarEvents)
                    })
                    .flatMapCompletable { pair ->
                        return@flatMapCompletable getActionsCompletable(calendarListItem.id, pair)
                    }

                return@flatMapCompletable updateObs
            }
            .andThen(repository.refreshCalendars()) // TODO: Remove?
    }

    /**
     * Gets Sirius API events of the selected calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getSiriusEventsList(calendarListItem: GoogleCalendarListItem): Observable<ArrayList<TimetableEvent>> {
        val id = calendarListItem.displayName.substringBefore("_")
        return repository.searchSirius(id)
            .filter { searchItem -> searchItem.id == id }
            .flatMap { searchItem ->
                repository.getSiriusEventsOf(searchItem.type, searchItem.id)
            }
            .flatMap { Observable.fromIterable(it.events) }
            .filter { !it.deleted }
            .map { event -> TimetableEvent.from(event) }
            .collect({ ArrayList<TimetableEvent>() }, { arrayList, item -> arrayList.add(item) })
            .map { list ->
                list.sortWith(Comparator { event1, event2 -> event1.siriusId!! - event2.siriusId!! })
                return@map list
            }
            .toObservable()
    }

    /**
     * Gets events from the selected Google calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getGoogleCalendarEventsList(calendarListItem: GoogleCalendarListItem): Observable<ArrayList<TimetableEvent>> {
        return repository.getGoogleCalendarEvents(calendarListItem.id)
            .filter { event -> event.siriusId != null && !event.deleted }
            .collect({ ArrayList<TimetableEvent>() }, { arrayList, item -> arrayList.add(item) })
            .map { list ->
                list.sortWith(Comparator { event1, event2 -> event1.siriusId!! - event2.siriusId!! })
                return@map list
            }
            .toObservable()
    }

    /**
     * Creates and uses completables to update a calendar.
     *
     * @param pair a pair containing a list of events from Sirius and a list of events from Google Calendar
     * @param calendarId an id of the calendar to update
     */
    private fun getActionsCompletable(
        calendarId: Int,
        pair: Pair<ArrayList<TimetableEvent>, ArrayList<TimetableEvent>>
    ): Completable {
        // Sort events out into lists by what action they should be used for
        val new = pair.first.minus(pair.second)
        val deleted = pair.second.minus(pair.first)
            .filter { !it.deleted }
        val changed = pair.first.intersect(pair.second)
            .filter { it.changed }

        // Get google event IDs of changed events
        for (event in changed) {
            val eventFromGoogleCalendar = pair.second.find { it.siriusId == event.siriusId }
            event.googleId = eventFromGoogleCalendar?.googleId
        }

        // Create action observables
        val createObs = Observable.fromIterable(new)
            .flatMap { currEvent ->
                repository.addGoogleCalendarEvent(calendarId, currEvent).toObservable()
            }
            .ignoreElements()

        val deleteObs = Observable.fromIterable(deleted)
            .map {
                it.deleted = true
                return@map it
            }
            .flatMap { currEvent ->
                repository.updateGoogleCalendarEvent(currEvent.googleId!!, currEvent).toObservable()
            }
            .ignoreElements()


        val changedObs = Observable.fromIterable(changed)
            .flatMap { currEvent ->
                repository.updateGoogleCalendarEvent(currEvent.googleId!!, currEvent).toObservable()
            }
            .ignoreElements()

        // Create a completable which starts all actions
        return Completable.mergeArray(createObs, deleteObs, changedObs)
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
            5, null, "T9:105", acronym = "BI-BIJ", capacity = 180,
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