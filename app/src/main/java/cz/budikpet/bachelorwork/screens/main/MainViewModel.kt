package cz.budikpet.bachelorwork.screens.main

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.SharedPreferences
import android.util.Log
import com.google.api.services.calendar.model.CalendarListEntry
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.MyApplication.Companion.calendarNameFromId
import cz.budikpet.bachelorwork.MyApplication.Companion.idFromCalendarName
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.multidayView.MultidayViewFragment
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.edit
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.Interval
import javax.inject.Inject

// TODO: Parts of AllCalendarUpdate code can be reused

class MainViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var repository: Repository

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private var compositeDisposable = CompositeDisposable()

    /** Username of the CTU account that was used to log in. */
    val ctuUsername: String by lazy { repository.ctuUsername }

    // MARK: Data

    /** Username, ItemType of the currently selected timetable. */
    val timetableOwner = MutableLiveData<Pair<String, ItemType>>()

    /** Events of the currently selected timetable. */
    val events = MutableLiveData<List<TimetableEvent>>()

    /** All timetables that were saved to the Google Calendar. */
    val savedTimetables = MutableLiveData<ArrayList<SearchItem>>()

    // MARK: State

    /** Contains ID of the selected sidebar item */
    var selectedSidebarItem = MutableLiveData<Int>()

    /** Event that was selected to be displayed. */
    val selectedEvent = MutableLiveData<TimetableEvent>()

    /** Event that is currently being edited. */
    var eventToEdit = MutableLiveData<TimetableEvent?>()

    /** Changes when editing are stored here. */
    var eventToEditChanges: TimetableEvent? = null

    /** Indicates whether some operation is running. */
    val operationRunning = MutableLiveData<Boolean>()

    /** Any exception that was thrown and must be somehow shown to the user. */
    val thrownException = MutableLiveData<Throwable>()

    /** Represents items received from Sirius API search endpoint. */
    val searchItems = MutableLiveData<List<SearchItem>>()
    var lastSearchQuery = ""

    /**
     * The date that corresponds to the currently selected MultidayFragment.
     *
     * Example:
     *
     * Today is tuesday 2. 1. 2018. If the Week (7 days) variant of MultidayView is used then [currentlySelectedDate]
     * has initial value equal to monday 1. 1. 2018. Otherwise it's equal to today.
     */
    var currentlySelectedDate = DateTime()
        set(value) {
            var today = value.withTimeAtStartOfDay()

            if (daysPerMultidayViewFragment == MultidayViewFragment.MAX_COLUMNS)
                today = today.withDayOfWeek(DateTimeConstants.MONDAY)

            field = today
        }

    var daysPerMultidayViewFragment = 7
        set(value) {
            field = when {
                value < 1 -> 1
                value > MultidayViewFragment.MAX_COLUMNS -> MultidayViewFragment.MAX_COLUMNS
                else -> value
            }
        }

    /** Represents a time interval for events that are currently loaded in [MainViewModel.events]. */
    var loadedEventsInterval = withMiddleDate(currentlySelectedDate)
        private set

    /** Events that chronologically belong to this time interval have already been updated. */
    var updatedEventsInterval: Interval? = null

    init {
        MyApplication.appComponent.inject(this)

        // Use custom setter
        currentlySelectedDate = DateTime()
    }

    fun onDestroy() {
        compositeDisposable.clear()
    }

    /**
     * Checks whether the device has internet connection. WiFi and/or Cellular if enabled.
     * @return true if the device is connected to the internet.
     */
    fun isInternetAvailable(): Boolean {
        return repository.isInternetAvailable()
    }

    fun goToLastMultidayView() {
        val id = when (daysPerMultidayViewFragment) {
            1 -> R.id.sidebarDayView
            3 -> R.id.sidebarThreeDayView
            else -> R.id.sidebarWeekView
        }
        selectedSidebarItem.postValue(id)
    }

    fun canBeClicked(searchItem: SearchItem): Boolean {
        val savedTimetables = savedTimetables.value
        return isInternetAvailable() || (savedTimetables != null && savedTimetables.contains(searchItem))
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
                if (!sharedPreferences.contains(SharedPreferencesKeys.CTU_USERNAME.toString())) {
                    // Store the Sirius username
                    sharedPreferences.edit {
                        putString(SharedPreferencesKeys.CTU_USERNAME.toString(), userInfo.username)
                    }
                }

                Completable.complete()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Authorization: $exception")
                thrownException.postValue(exception)
                true
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

    fun searchSirius(query: String, itemType: ItemType? = null) {
        val disposable = repository.searchSirius(query)
            .filter {
                when {
                    itemType != null -> it.type == itemType
                    else -> true
                }
            }
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { list ->
                    searchItems.postValue(list)
                },
                { error ->
                    Log.e(TAG, "SearchSirius error: $error")
                    // Throwing error not necessary
                }
            )

        compositeDisposable.add(disposable)
    }

    // MARK: Google Calendar

    fun signedInToGoogle() {
        val currOwner = timetableOwner.value

        if (currOwner == null) {
            selectedSidebarItem.postValue(R.id.sidebarWeekView)
            timetableOwner.postValue(Pair(ctuUsername, ItemType.PERSON))
            updateCalendars(ctuUsername)
        }
    }

    /**
     * Updates one or all calendars used by the application with data from Sirius API. Refreshes loaded events in [MainViewModel.events].
     *
     * Uses [MainViewModel.loadedEventsInterval]. Events that are chronologically in the [MainViewModel.loadedEventsInterval]
     * are the only ones updated and loaded.
     *
     * @param username if a username is provided then only its calendar is updated and loaded
     */
    fun updateCalendars(username: String? = null) {
        val calendarName = when {
            username != null -> calendarNameFromId(username)
            else -> null
        }

        Log.i(TAG, "Update started")
        updatedEventsInterval = loadedEventsInterval

        operationRunning.postValue(true)
        compositeDisposable.clear()

        val disposable = repository.getGoogleCalendarList()
            .toList()
            .flatMapCompletable {
                // Check if personal calendar exists and unhide hidden calendars in Google Calendar service
                checkGoogleCalendars(it)
                    .observeOn(Schedulers.computation())
                    .andThen(repository.getLocalCalendarListItems())
                    .filter { !it.syncEvents }
                    .map { it.with(syncEvents = true) }
                    .flatMapCompletable {
                        // Make all local calendars sync with Google Calendar service
                        repository.updateLocalCalendarList(it).ignoreElement()
                    }
            }
            .andThen(repository.refreshCalendars())
            .andThen(repository.getLocalCalendarListItems())
            .filter { username == null || it.displayName == calendarName }  // If username is null, update all
            .observeOn(Schedulers.io())
            .flatMapCompletable { calendarListItem ->
                // Update the currently picked calendar with data from Sirius API
                val siriusObs = getSiriusEventsList(calendarListItem)

                val calendarObs = getGoogleCalendarEventsList(calendarListItem)

                val updateObs = Observable.zip(siriusObs, calendarObs,
                    BiFunction { siriusEvents: MutableList<TimetableEvent>, calendarEvents: MutableList<TimetableEvent> ->
                        Pair(siriusEvents, calendarEvents)
                    })
                    .flatMapCompletable { pair ->
                        return@flatMapCompletable getActionsCompletable(calendarListItem.id, pair)
                    }

                return@flatMapCompletable updateObs
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "UpdateCalendars error: $exception")
                thrownException.postValue(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Update done")
                operationRunning.postValue(false)
                loadEvents()
                updateSavedTimetables()

                // Refresh Google Calendar without waiting
                repository.startCalendarRefresh()
            }

        compositeDisposable.add(disposable)
    }

    /**
     * Checks Google Calendar list for calendars that are hidden and for the missing personal calendar.
     */
    private fun checkGoogleCalendars(calendars: MutableList<CalendarListEntry>): Completable {
        val personalCalendarName = calendarNameFromId(ctuUsername)
        var personalCalendarFound = false
        val hiddenCalendars = mutableListOf<CalendarListEntry>()

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
                repository.updateGoogleCalendarList(entry).ignoreElement()
            }

        if (!personalCalendarFound) {
            Log.i(TAG, "Creating personal calendar: $personalCalendarName")
            completable = completable.andThen(repository.addGoogleCalendar(personalCalendarName))
        }

        return completable
    }

    /**
     * Gets Sirius API events of the selected calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getSiriusEventsList(calendarListItem: CalendarListItem): Observable<MutableList<TimetableEvent>>? {
        // Get id from calendar display name - username, room number...
        val id = idFromCalendarName(calendarListItem.displayName)

        return repository.searchSirius(id)
            .filter { searchItem -> searchItem.id == id }
            .flatMap { searchItem ->
                repository.getSiriusEventsOf(
                    searchItem.type,
                    searchItem.id,
                    loadedEventsInterval.start,
                    loadedEventsInterval.end
                )
            }
            .flatMap { Observable.fromIterable(it.events) }
            .map { event -> TimetableEvent.from(event) }
            .toList()
            .toObservable()
    }

    /**
     * Gets events from the selected calendar.
     * @return An observable holding a list of TimetableEvents.
     */
    private fun getGoogleCalendarEventsList(calendarListItem: CalendarListItem): Observable<MutableList<TimetableEvent>>? {
        return repository.getCalendarEvents(calendarListItem.id, loadedEventsInterval.start, loadedEventsInterval.end)
            .filter { event -> event.siriusId != null }   // TODO: Move to methods that use the list?
            .toList()
            .toObservable()
    }

    /**
     * Creates and uses completables to update a calendar.
     *
     * @param pair a pair<SiriusEvents, CalendarEvents> containing a list of events from Sirius and a list of events from Google Calendar
     * @param calendarId an id of the calendar to update
     */
    private fun getActionsCompletable(
        calendarId: Long,
        pair: Pair<MutableList<TimetableEvent>, MutableList<TimetableEvent>>
    ): Completable {
        // Sort events out into lists by what action they should be used for
        var new = pair.first.minus(pair.second)
        var deleted = pair.second.minus(pair.first)

        // These events were changed by a user and should not be changed by update
        val changedByUser = new.map { Pair(it.siriusId!!, true) }
            .intersect(deleted.map { Pair(it.siriusId!!, it.changed) })
            .map { it.first }

        // Remove events that were changed by user
        new = new.filter { !changedByUser.contains(it.siriusId) }
        deleted = deleted.filter { !changedByUser.contains(it.siriusId) }

        // Create action completables
        val createObs = Observable.fromIterable(new)
            .flatMapCompletable { currEvent ->
                Observable.fromIterable(currEvent.teacherIds)
                    .flatMapMaybe { repository.searchSirius(it).firstElement() }
                    .toList()
                    .flatMapCompletable { teacherSearchItems ->
                        // Fill teacher names
                        val teacherNames = ArrayList(teacherSearchItems.map { it.toString() })
                        currEvent.teachersNames.addAll(teacherNames)

                        repository.addCalendarEvent(calendarId, currEvent).ignoreElement()
                    }

            }

        val deleteObs = Observable.fromIterable(deleted)
            .map {
                it.deleted = true
                return@map it
            }
            .flatMapCompletable { currEvent ->
                // TODO: Update deleted status of events with SiriusID, delete the rest
                Log.i(TAG, "Deleting event: $currEvent")
                repository.deleteCalendarEvent(currEvent, deleteCompletely = true).ignoreElement()
            }


        // Create a completable which starts all actions
        return Completable.mergeArray(deleteObs, createObs)
    }

    /**
     * @return true if the currently loaded events in [MainViewModel.events] have already been updated.
     */
    fun areLoadedEventsUpdated(): Boolean {
        val updatedEventsInterval = this.updatedEventsInterval
        return updatedEventsInterval != null && updatedEventsInterval.isEqual(loadedEventsInterval)
    }

    /**
     * @return true if the currently selected timetable is available offline.
     */
    fun isSelectedCalendarAvailableOffline(): Boolean {
        val savedTimetables = this.savedTimetables.value
        val timetableOwnerUsername = this.timetableOwner.value?.first

        if (savedTimetables != null && timetableOwnerUsername != null) {
            return savedTimetables.any { it.id == timetableOwnerUsername }
        }

        return false
    }

    fun removeCalendar(calendarName: String) {
        val disposable = repository.getGoogleCalendar(calendarName)
            .flatMapCompletable {
                repository.removeGoogleCalendar(it)
            }
            .subscribeOn(Schedulers.io())
            .onErrorComplete { exception ->
                Log.e(TAG, "RemoveCalendar error: $exception")
                thrownException.postValue(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Calendar removed")
                updateSavedTimetables(true)
            }

        compositeDisposable.add(disposable)
    }

    fun addCalendar(calendarName: String) {
        val disposable = repository.addGoogleCalendar(calendarName)
            .subscribeOn(Schedulers.io())
            .onErrorComplete { exception ->
                Log.e(TAG, "AddCalendar error: $exception")
                thrownException.postValue(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Calendar added")
                updateSavedTimetables(true)
            }

        compositeDisposable.add(disposable)
    }

    /**
     * Updates [savedTimetables] using SearchSirius endpoint.
     *
     * If the internet connection is unavailable the default SearchItems with usernames only are saved.
     */
    private fun updateSavedTimetables(refreshCalendars: Boolean = false) {
        // Update calendars if needed
        val obs = when (refreshCalendars) {
            true -> repository.refreshCalendars().andThen(repository.getLocalCalendarListItems())
            false -> repository.getLocalCalendarListItems()
        }


        val disposable = obs
            .flatMapMaybe {
                val username = idFromCalendarName(it.displayName)

                if (repository.isInternetAvailable()) {
                    // We have internet connection so we can call search endpoint
                    return@flatMapMaybe repository.searchSirius(username).firstElement()
                }

                return@flatMapMaybe Maybe.just(SearchItem(username, type = ItemType.UNKNOWN))
            }
            .toList()
            .map {
                it.sortedWith(Comparator { searchItem1, searchItem2 ->
                    searchItem1.type.ordinal - searchItem2.type.ordinal
                })
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { result ->
                    savedTimetables.postValue(ArrayList(result))
                },
                { error ->
                    Log.e(TAG, "UpdateSavedTimetables: $error")
                    thrownException.postValue(error)
                }
            )

        compositeDisposable.add(disposable)
    }

    /**
     * Loads events into [MainViewModel.events].
     */
    fun loadEvents(middleDate: DateTime = currentlySelectedDate) {
        val pair = timetableOwner.value

        if (pair == null) {
            Log.e(TAG, "Timetable owner not specified.")
            return
        }

        loadedEventsInterval = withMiddleDate(middleDate)

        operationRunning.postValue(true)
        val disposable = repository.getLocalCalendarListItems()
            .filter { it.displayName == calendarNameFromId(pair.first) }
            .flatMap { repository.getCalendarEvents(it.id, loadedEventsInterval.start, loadedEventsInterval.end) }
            .switchIfEmpty(
                repository.getSiriusEventsOf(
                    pair.second,
                    pair.first,
                    loadedEventsInterval.start,
                    loadedEventsInterval.end
                )
                    .flatMap { Observable.fromIterable(it.events) }
                    .filter { !it.deleted }
                    .map { event -> TimetableEvent.from(event) }
            )
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { events ->
                    this.events.postValue(events)
                    operationRunning.postValue(false)
                },
                { error ->
                    Log.e(TAG, "LoadEvents error: $error")
                    thrownException.postValue(error)
                    operationRunning.postValue(false)
                }
            )

        compositeDisposable.add(disposable)
    }

    // MARK: Mostly methods used for testing

    /**
     * Gets a list of calendar display names and ids using the android calendar provider.
     */
    fun getLocalCalendarList() {
        val disposable = repository.getLocalCalendarListItems()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "$result")
                },
                { error ->
                    Log.e(TAG, "GetCalendarEvents: $error")
                    thrownException.postValue(error)
                }
            )

        compositeDisposable.add(disposable)
    }

    fun addCalendarEvent(timetableEvent: TimetableEvent) {
        val disposable = repository.getLocalCalendarListItems()
            .filter { it.displayName == calendarNameFromId(timetableOwner.value!!.first) }
            .flatMapSingle {
                when {
                    timetableEvent.googleId != null -> repository.updateCalendarEvent(timetableEvent)
                    else -> repository.addCalendarEvent(it.id, timetableEvent)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "addCalendarEvent")

                    if (selectedEvent.value != null) {
                        selectedEvent.postValue(eventToEditChanges)
                    }

                    eventToEditChanges = null
                    eventToEdit.postValue(null)
                    loadEvents()
                },
                { error ->
                    Log.e(TAG, "addCalendarEvent: $error")
                    thrownException.postValue(error)
                })

        compositeDisposable.add(disposable)
    }

    fun removeCalendarEvent(timetableEvent: TimetableEvent) {
        val disposable = repository.deleteCalendarEvent(timetableEvent)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "removeCalendarEvent")
                    selectedEvent.postValue(null)
                    loadEvents()
                },
                { error ->
                    Log.e(TAG, "removeCalendarEvent: $error")
                    thrownException.postValue(error)
                })

        compositeDisposable.add(disposable)
    }

    fun sharePersonalTimetable(email: String) {
        val disposable = repository.sharePersonalCalendar(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "CalendarShared, ACL: $result")
                },
                { error ->
                    Log.e(TAG, "sharePersonalTimetable: $error")
                    thrownException.postValue(error)
                })

        compositeDisposable.add(disposable)
    }

    fun unsharePersonalTimetable(email: String) {
        val disposable = repository.unsharePersonalCalendar(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete { exception ->
                Log.e(TAG, "Unshare: $exception")
                thrownException.postValue(exception)
                true
            }
            .subscribe {
                Log.i(TAG, "Calendar unshared successfully.")
            }

        compositeDisposable.add(disposable)
    }

    // MARK: Multiday

    fun editOrCreateEvent(event: TimetableEvent) {
        eventToEditChanges = event.deepCopy()
        eventToEdit.postValue(event.deepCopy())

    }

    companion object {
        fun withMiddleDate(date: DateTime): Interval {
            val dateStart = date.minusWeeks(MyApplication.NUM_OF_WEEKS_TO_UPDATE).withTimeAtStartOfDay()
            val dateEnd = date.plusWeeks(MyApplication.NUM_OF_WEEKS_TO_UPDATE).withTimeAtStartOfDay()

            return Interval(dateStart, dateEnd)
        }
    }
}