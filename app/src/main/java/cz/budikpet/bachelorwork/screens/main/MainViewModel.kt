package cz.budikpet.bachelorwork.screens.main

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.SharedPreferences
import android.util.Log
import com.google.api.services.calendar.model.CalendarListEntry
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.GoogleCalendarListItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.edit
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

// TODO: Parts of AllCalendarUpdate code can be reused
// TODO: Check created observables for possible exception throws

data class State(val username: String, val events: List<TimetableEvent>)

class MainViewModel : ViewModel(), MultidayViewFragment.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    /**
     * Holds data important for displaying events.
     */
    val state = MutableLiveData<State>()

    /**
     * Indicates that an update of all calendars ended successfully.
     */
    val calendarsUpdating = MutableLiveData<Boolean>()

    val thrownException = MutableLiveData<Throwable>()

    @Inject
    internal lateinit var repository: Repository

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private var compositeDisposable = CompositeDisposable()

    private val dateMonday = DateTime().withDayOfWeek(DateTimeConstants.MONDAY).withTimeAtStartOfDay()
    private val numOfWeeksToUpdate by lazy {
        sharedPreferences.getInt(
            SharedPreferencesKeys.NUM_OF_WEEKS_TO_UPDATE.toString(), 1
        )
    }

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        compositeDisposable.clear()
    }

    /**
     * Checks whether a user is fully authorized in Sirius API.
     *
     * If the user isn't fully authorized, authorization code exchange proceeds.
     *
     * If the user is fully authorized, tokens are refreshed.
     */
    fun checkSiriusAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        compositeDisposable.clear()

        val disposable = repository.checkSiriusAuthorization(response, exception)
            .observeOn(Schedulers.io()) // TODO: Why is it necessery?
            .flatMapObservable { accessToken ->
                repository.getLoggedUserInfo(accessToken)
            }
            .flatMapCompletable { userInfo ->
                if (!sharedPreferences.contains(SharedPreferencesKeys.SIRIUS_USERNAME.toString())) {
                    // Store the Sirius username
                    sharedPreferences.edit {
                        putString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), userInfo.username)
                    }
                }

                Completable.complete()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Authorization: $exception")
                false
            }
            .subscribe {
                //                Log.i(TAG, "Thread: ${Thread.currentThread().name}")
                Log.i(TAG, "Fully authorized & tokens restored.")
            }

        compositeDisposable.add(disposable)
    }

    fun signOut() {
        repository.signOut()
    }

    fun signedInToGoogle() {
        val username: String = sharedPreferences.getString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), "")
        state.postValue(State(username, listOf()))
    }

    /**
     * Uses Sirius API to get events of the specified thing.
     *
     * @param id identification of the item whose events we want.
     * @param itemType type of the item whose events we want.
     */
    fun getSiriusEventsOf(
        itemType: ItemType,
        id: String,
        dateStart: DateTime = dateMonday,
        dateEnd: DateTime = dateStart.plusWeeks(numOfWeeksToUpdate)
    ) {
        val disposable = repository.getSiriusEventsOf(itemType, id, dateStart, dateEnd)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    // TODO: Implement
//                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error}") }
            )

        compositeDisposable.add(disposable)
    }

    // MARK: Google Calendar

    /**
     * Updates all calendars used by the application with data from Sirius API.
     */
    fun updateAllCalendars() {
        compositeDisposable.clear()

        calendarsUpdating.postValue(true)

        val disposable = repository.getGoogleCalendarList()
            .flatMapCompletable { checkGoogleCalendars(it) }
            .andThen(repository.refreshCalendars())
            .andThen(repository.getLocalCalendarList())
            .flatMapCompletable { calendarListItem ->
                // Update the currently picked calendar with data from Sirius API
                val siriusObs = getSiriusEventsList(calendarListItem)
                    .retry(21)

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
//            .andThen(repository.refreshCalendars()) // TODO: Do after the postValue. Maybe even in a separate chain.
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete {
                thrownException.postValue(it)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Update done")
                calendarsUpdating.postValue(false)  // TODO: Do before the second refreshCalendar
            }

        compositeDisposable.add(disposable)
    }

    /**
     * Checks Google Calendar list for calendars that are hidden and for the missing personal calendar.
     */
    private fun checkGoogleCalendars(calendars: MutableList<CalendarListEntry>): Completable {
        val username = sharedPreferences.getString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), null)
        val personalCalendarName = "${username}_${MyApplication.calendarsName}"
        var personalCalendarFound = false
        var hiddenCalendars = mutableListOf<CalendarListEntry>()

        for (calendar in calendars) {
            if (calendar.hidden != null) {
                // Calendars that the app is using mustn't be hidden
                calendar.hidden = false
                hiddenCalendars.add(calendar)
            }

            if (calendar.summary == personalCalendarName) {
                personalCalendarFound = true
            }
        }

        var completable = Observable.fromIterable(hiddenCalendars)
            .flatMapCompletable { entry ->
                repository.updateGoogleCalendarList(entry)
                    .toCompletable()
            }

        if (!personalCalendarFound) {
            Log.i(TAG, "Creating personal calendar: $personalCalendarName")
            completable = completable.andThen(repository.addSecondaryGoogleCalendar(personalCalendarName))
        }

        return completable
    }

    /**
     * Gets Sirius API events of the selected calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getSiriusEventsList(
        calendarListItem: GoogleCalendarListItem,
        dateStart: DateTime = dateMonday.minusWeeks(numOfWeeksToUpdate),
        dateEnd: DateTime = dateStart.plusWeeks(numOfWeeksToUpdate * 2)
    ): Observable<ArrayList<TimetableEvent>> {
        // Get id from calendar display name - username, room number...
        val id = calendarListItem.displayName.substringBefore("_")

        return repository.searchSirius(id)
            .filter { searchItem -> searchItem.id == id }
            .flatMap { searchItem ->
                repository.getSiriusEventsOf(searchItem.type, searchItem.id, dateStart, dateEnd)
            }
            .flatMap { Observable.fromIterable(it.events) }
            .filter { !it.deleted }
            .map { event -> TimetableEvent.from(event) }
            .collect({ ArrayList<TimetableEvent>() }, { arrayList, item -> arrayList.add(item) })
            .map { list ->
                Log.i(TAG, "Received ${list.count()} events from SiriusAPI timetable ${list.first().siriusId}.")
                list.sortWith(Comparator { event1, event2 -> event1.siriusId!! - event2.siriusId!! })
                return@map list
            }
            .toObservable()
    }

    /**
     * Gets events from the selected Google calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getGoogleCalendarEventsList(
        calendarListItem: GoogleCalendarListItem,
        dateStart: DateTime = dateMonday.minusWeeks(numOfWeeksToUpdate),
        dateEnd: DateTime = dateStart.plusWeeks(numOfWeeksToUpdate * 2)
    ): Observable<ArrayList<TimetableEvent>> {
        return repository.getCalendarEvents(calendarListItem.id, dateStart, dateEnd)
            .filter { event -> event.siriusId != null && !event.deleted }   // TODO: Move to methods that use the list?
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
                // TODO: Update deleted status of events with SiriusID, delete the rest
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

    fun loadEventsFromCalendar(
        username: String,
        dateStart: DateTime = dateMonday.minusWeeks(numOfWeeksToUpdate),
        dateEnd: DateTime = dateStart.plusWeeks(numOfWeeksToUpdate * 2)
    ) {
        val disposable = repository.getLocalCalendarList()
            .filter { it.displayName == "${username}_${MyApplication.calendarsName}" }
            .flatMap { repository.getCalendarEvents(it.id, dateStart, dateEnd) }
            .collect({ ArrayList<TimetableEvent>() }, { arrayList, item -> arrayList.add(item) })
            .map { list ->
                list.sortWith(Comparator { event1, event2 -> event1.siriusId!! - event2.siriusId!! })
                return@map list
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { events ->
                    this.state.postValue(State(username, events))
                },
                { error ->
                    Log.e(TAG, "loadEventsFromCalendar: $error")
                }
            )

        compositeDisposable.add(disposable)
    }

    // MARK: Multiday

    override fun onAddEventClicked(startTime: DateTime, endTime: DateTime) {
        Log.i(
            TAG,
            "Add event clicked: ${startTime.toString("dd.MM")}<${startTime.toString("HH:mm")} – ${endTime.toString("HH:mm")}>"
        )
    }

    override fun onEventClicked(event: TimetableEvent) {
        Log.i(TAG, "Event clicked: $event")
    }

    // MARK: Mostly methods used for testing

    fun getGoogleCalendarList() {
        val disposable = repository.getGoogleCalendarList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    for (calendar in result) {
                        Log.i(TAG, "CalendarName: ${calendar.summary}")
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

//    fun getGoogleCalendarEvents(calId: Int) {
//        val disposable = repository.getCalendarEvents(calId)
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                { result ->
//                    Log.i(TAG, "AddGoogleCalendar")
//                    Log.i(TAG, result.toString())
//                },
//                { error ->
//                    Log.e(TAG, "AddGoogleCalendar: ${error}")
//                })
//        compositeDisposable.add(disposable)
//    }

    fun addSecondaryGoogleCalendar(name: String) {
        val disposable = repository.addSecondaryGoogleCalendar(name)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "AddSecondaryCalendar: $exception")
                true   // TODO: Show error toast
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
                    Log.e(TAG, "addGoogleCalendarEvent: $error")
                })

        compositeDisposable.add(disposable)
    }

    fun sharePersonalCalendar(email: String) {
        val disposable = repository.sharePersonalCalendar(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "sharePersonalCalendar")
                    Log.i(TAG, "ACL: $result")
                },
                { error ->
                    Log.e(TAG, "sharePersonalCalendar: $error")
                })

        compositeDisposable.add(disposable)
    }

    fun unsharePersonalCalendar(email: String) {
        val disposable = repository.unsharePersonalCalendar(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Unshare: $exception")
                true   // TODO: Show error toast
            }
            .subscribe {
                Log.i(TAG, "Calendar unshared successfully.")
            }

        compositeDisposable.add(disposable)
    }
}