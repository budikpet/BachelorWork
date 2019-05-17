package cz.budikpet.bachelorwork.screens.main

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.MyApplication.Companion.calendarNameFromId
import cz.budikpet.bachelorwork.MyApplication.Companion.idFromCalendarName
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.CalendarListItem
import cz.budikpet.bachelorwork.data.models.PassableStringResource
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.multidayView.MultidayViewFragment
import cz.budikpet.bachelorwork.util.GoogleAccountNotFoundException
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.*
import retrofit2.HttpException
import java.net.SocketTimeoutException
import javax.inject.Inject


open class MainViewModel @Inject constructor(var repository: Repository, var schedulerProvider: BaseSchedulerProvider) :
    ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    var compositeDisposable = CompositeDisposable()

    /** Username of the CTU account that was used to log in. */
    val ctuUsername: String by lazy { repository.ctuUsername }

    // MARK: Data

    /** Username, ItemType of the currently selected timetable. */
    val timetableOwner = MutableLiveData<Pair<String, ItemType>>()

    /** Events of the currently selected timetable. */
    val events = MutableLiveData<List<TimetableEvent>>()

    /** All timetables that were saved to the Google Calendar. */
    val savedTimetables = MutableLiveData<ArrayList<SearchItem>>()

    /** Timetable events that represent free time. */
    val freeTimeEvents = MutableLiveData<ArrayList<TimetableEvent>>()

    /** Email addresses of people the user shares his timetable with. */
    val emails = MutableLiveData<ArrayList<String>>()

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
    val operationsRunning = MutableLiveData<Int>()

    val ctuSignedOut = MutableLiveData<Boolean>()

    /** Any exception that was thrown and must be somehow shown to the user. */
    val thrownException = MutableLiveData<Throwable>()

    /** A message we want to show to the user. */
    val showMessage = MutableLiveData<PassableStringResource>()

    /** Represents items received from Sirius API search endpoint. */
    val searchItems = MutableLiveData<List<SearchItem>>()
    var lastSearchQuery = ""

    /** Timetables that were selected for free time calculations. */
    val freeTimeTimetables: ArrayList<SearchItem> = arrayListOf()
    var selectedWeekStart: DateTime? = null
    var selectedStartTime: LocalTime? = null
    var selectedEndTime: LocalTime? = null

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
    open fun isInternetAvailable(): Boolean {
        return repository.isInternetAvailable()
    }

    open fun goToLastMultidayView() {
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

    open fun canEditTimetable(): Boolean {
        return timetableOwner.value!!.first == ctuUsername && selectedSidebarItem.value != R.id.sidebarFreeTime
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
            .observeOn(schedulerProvider.io())
            .flatMapObservable { accessToken ->
                repository.getLoggedUserInfo(accessToken)
            }
            .flatMapCompletable { userInfo ->
                repository.saveCtuUsername(userInfo.username)

                Completable.complete()
            }
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .onErrorComplete { exception ->
                Log.e(TAG, "Authorization: $exception")
                handleException(exception)
                true
            }
            .subscribe {
                //                Log.i(TAG, "Thread: ${Thread.currentThread().name}")
                Log.i(TAG, "Fully authorized & tokens restored.")
            }

        compositeDisposable.add(disposable)
    }

    open fun ctuLogOut() {
        repository.signOut()
        ctuSignedOut.postValue(true)
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
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
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

    /**
     * The application has all permissions, is signed into Google and CTU accounts.
     */
    open fun ready(forceUpdate: Boolean = false) {
        val currOwner = timetableOwner.value
        operationsRunning.value = 0

        if (currOwner == null || forceUpdate) {
            selectedSidebarItem.value = R.id.sidebarWeekView
            timetableOwner.value = Pair(ctuUsername, ItemType.PERSON)
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

        operationsRunning.value = operationsRunning.value!! + 1
        compositeDisposable.clear()

        val disposable = repository.getGoogleCalendarList()
            .toList()
            .flatMapCompletable {
                // Check if personal calendar exists and unhide hidden calendars in Google Calendar service
                checkGoogleCalendars(it)
                    .observeOn(schedulerProvider.computation())
                    .andThen(repository.getLocalCalendarListItems())
                    .filter { !it.syncEvents }
                    .map { it.with(syncEvents = true) }
                    .flatMapCompletable {
                        // Ensure that all used calendars sync Google Calendar service
                        repository.updateLocalCalendarList(it).ignoreElement()
                    }
            }
            .andThen(repository.refreshCalendars())
            .andThen(repository.getLocalCalendarListItems())
            .filter { username == null || it.displayName == calendarName }  // If username is null, update all
            .observeOn(schedulerProvider.io())
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
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .doOnDispose {
                Log.i(TAG, "Dispose")
                operationsRunning.value = operationsRunning.value!! - 1
            }
            .onErrorComplete { exception ->
                Log.e(TAG, "UpdateCalendars error: $exception")
                handleException(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Update done")
                operationsRunning.value = operationsRunning.value!! - 1
                if (username != null) {
                    showMessage.postValue(PassableStringResource(R.string.message_TimetableUpdated, listOf(username)))
                } else {
                    showMessage.postValue(PassableStringResource(R.string.message_TimetablesUpdated))
                }

                loadEvents()
                updateSavedTimetables()
                updateSharedEmails()

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
    open fun areLoadedEventsUpdated(): Boolean {
        val updatedEventsInterval = this.updatedEventsInterval
        return updatedEventsInterval != null && updatedEventsInterval.isEqual(loadedEventsInterval)
    }

    /**
     * @return true if the currently selected timetable is available offline.
     */
    fun isCalendarAvailableOffline(timetableOwnerUsername: String): Boolean {
        val savedTimetables = this.savedTimetables.value

        if (savedTimetables != null) {
            return savedTimetables.any { it.id == timetableOwnerUsername }
        }

        return false
    }

    fun removeCalendar(calendarName: String) {
        val disposable = repository.getGoogleCalendar(calendarName)
            .flatMapCompletable {
                repository.removeGoogleCalendar(it)
            }
            .subscribeOn(schedulerProvider.io())
            .onErrorComplete { exception ->
                Log.e(TAG, "RemoveCalendar error: $exception")
                handleException(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Calendar removed")
                showMessage.postValue(PassableStringResource(R.string.message_CalendarRemoved))
                updateSavedTimetables(true)
            }

        compositeDisposable.add(disposable)
    }

    fun addCalendar(calendarName: String) {
        val disposable = repository.addGoogleCalendar(calendarName)
            .subscribeOn(schedulerProvider.io())
            .onErrorComplete { exception ->
                Log.e(TAG, "AddCalendar error: $exception")
                handleException(exception)
                return@onErrorComplete true
            }
            .subscribe {
                Log.i(TAG, "Calendar added")
                showMessage.postValue(PassableStringResource(R.string.message_CalendarAdded))
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
            true -> repository.refreshCalendars().andThen(repository.getSavedTimetables())
            false -> repository.getSavedTimetables()
        }


        val disposable = obs
            .subscribeOn(schedulerProvider.io())
            .subscribe(
                { result ->
                    savedTimetables.postValue(ArrayList(result))
                },
                { exception ->
                    Log.e(TAG, "UpdateSavedTimetables: $exception")
                    handleException(exception)
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

        operationsRunning.value = operationsRunning.value!! + 1
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
                    .map { event -> TimetableEvent.from(event) }
            )
            .toList()
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .doOnDispose {
                Log.i(TAG, "Dispose")
                operationsRunning.value = operationsRunning.value!! - 1
            }
            .subscribe(
                { events ->
                    this.events.postValue(events)
                    operationsRunning.value = operationsRunning.value!! - 1
                },
                { exception ->
                    Log.e(TAG, "LoadEvents error: $exception")
                    !checkNotFound(exception)
                    handleException(exception)
                    operationsRunning.value = operationsRunning.value!! - 1
                }
            )

        compositeDisposable.add(disposable)
    }

    private fun checkNotFound(error: Throwable): Boolean {
        if (error is HttpException && error.code() == 404) {
            // Calendar not found, go back
            timetableOwner.postValue(Pair(ctuUsername, ItemType.PERSON))
            return true
        }

        return false
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
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .subscribe(
                { result ->
                    Log.i(TAG, "addCalendarEvent")

                    if (selectedEvent.value != null) {
                        selectedEvent.postValue(timetableEvent)
                    }

                    eventToEditChanges = null
                    eventToEdit.postValue(null)
                    loadEvents()
                },
                { exception ->
                    Log.e(TAG, "addCalendarEvent: $exception")
                    handleException(exception)
                })

        compositeDisposable.add(disposable)
    }

    fun removeCalendarEvent(timetableEvent: TimetableEvent) {
        val disposable = repository.deleteCalendarEvent(timetableEvent)
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .subscribe(
                { result ->
                    Log.i(TAG, "removeCalendarEvent")
                    selectedEvent.postValue(null)
                    loadEvents()
                },
                { exception ->
                    Log.e(TAG, "removeCalendarEvent: $exception")
                    handleException(exception)
                })

        compositeDisposable.add(disposable)
    }

    fun sharePersonalTimetable(email: String) {
        operationsRunning.value = operationsRunning.value!! + 1
        val disposable = repository.sharePersonalCalendar(email)
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .doOnDispose {
                Log.i(TAG, "Dispose")
                operationsRunning.value = operationsRunning.value!! - 1
            }
            .subscribe(
                { result ->
                    Log.i(TAG, "CalendarShared, ACL: $result")
                    showMessage.postValue(PassableStringResource(R.string.message_CalendarShared))
                    operationsRunning.value = operationsRunning.value!! - 1
                    updateSharedEmails()
                },
                { exception ->
                    Log.e(TAG, "sharePersonalTimetable: $exception")
                    handleException(exception)
                    operationsRunning.value = operationsRunning.value!! - 1
                    updateSharedEmails()
                })

        compositeDisposable.add(disposable)
    }

    fun unsharePersonalTimetable(email: String) {
        operationsRunning.value = operationsRunning.value!! + 1
        val disposable = repository.unsharePersonalCalendar(email)
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .doOnDispose {
                Log.i(TAG, "Dispose")
                operationsRunning.value = operationsRunning.value!! - 1
            }
            .onErrorComplete { exception ->
                Log.e(TAG, "Unshare: $exception")
                handleException(exception)
                true
            }
            .subscribe {
                Log.i(TAG, "Calendar unshared successfully.")
                showMessage.postValue(PassableStringResource(R.string.message_CalendarUnshared))
                operationsRunning.value = operationsRunning.value!! - 1
                updateSharedEmails()
            }

        compositeDisposable.add(disposable)
    }

    open fun updateSharedEmails() {
        val disposable = repository.getEmails(calendarNameFromId(ctuUsername))
            .toList()
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .subscribe(
                { result ->
                    Log.i(TAG, "updateSharedEmails: $result")
                    showMessage.postValue(PassableStringResource(R.string.message_emailListUpdated))
                    emails.postValue(ArrayList(result))
                },
                { exception ->
                    Log.e(TAG, "updateSharedEmails: $exception")
                    handleException(exception)
                })

        compositeDisposable.add(disposable)
    }

    fun editOrCreateEvent(event: TimetableEvent) {
        eventToEditChanges = event.deepCopy()
        eventToEdit.postValue(event.deepCopy())

    }

    fun getFreeTimeEvents(
        weekStart: DateTime,
        weekEnd: DateTime,
        startTime: LocalTime,
        endTime: LocalTime,
        timetables: ArrayList<SearchItem>
    ) {
        val timeRange = Range.closed(startTime, endTime)

        val numOfDays = Days.daysBetween(weekStart.toLocalDate(), weekEnd.toLocalDate()).days

        val rangeSet = TreeRangeSet.create<DateTime>()
        for (i in 0 until numOfDays) {
            val range = Range.closed(weekStart.plusDays(i).withTime(startTime), weekStart.plusDays(i).withTime(endTime))
            rangeSet.add(range)
        }

        compositeDisposable.clear()
        operationsRunning.value = operationsRunning.value!! + 1
        val disposable = Observable.fromIterable(timetables)
            .flatMap {
                val savedTimetables = savedTimetables.value
                if (savedTimetables != null) {
                    if (savedTimetables.contains(it)) {
                        // Timetable is available in a calendar
                        return@flatMap repository.getLocalCalendarListItem(it.id)
                            .flatMapObservable { calendarItem ->
                                repository.getCalendarEvents(calendarItem.id, weekStart, weekEnd)
                                    .filter { !it.deleted }
                            }
                    }
                }

                return@flatMap repository.getSiriusEventsOf(it.type, it.id, weekStart, weekEnd)
                    .map { TimetableEvent.from(it) }
                    .onErrorReturn { TimetableEvent(starts_at = weekStart.minusDays(2)) }
            }
            .map { Pair(it.starts_at, it.ends_at) }
            .observeOn(schedulerProvider.computation())
            .filter {
                // Filter out event that are not in specified time range
                val eventTimeRange = Range.closed(it.first.toLocalTime(), it.second.toLocalTime())
                return@filter timeRange.isConnected(eventTimeRange)
            }
            .doOnNext {
                val eventTimeRange = Range.closed(it.first, it.second)
                rangeSet.remove(eventTimeRange)
            }
            .doOnComplete {
                Log.i(TAG, "$rangeSet")

                val events = arrayListOf<TimetableEvent>()
                for (range in rangeSet.asRanges()) {
                    val startsAt = range.lowerEndpoint()
                    val endsAt = range.upperEndpoint()
                    val hours = Hours.hoursBetween(startsAt, endsAt).hours
                    val minutes = Minutes.minutesBetween(startsAt, endsAt).minutes

                    var acronym = ""
                    if (minutes % 60 != 0) {
                        acronym = "${minutes % 60} m"
                    }
                    if (hours > 0) {
                        acronym = "$hours h $acronym"
                    }

                    val event = TimetableEvent(starts_at = startsAt, ends_at = endsAt, acronym = acronym)
                    events.add(event)
                }

                freeTimeEvents.postValue(events)
            }
            .subscribeOn(schedulerProvider.io())
            .observeOn(schedulerProvider.ui())
            .doOnDispose {
                Log.i(TAG, "Dispose")
                operationsRunning.value = operationsRunning.value!! - 1
            }
            .subscribe(
                {
                    operationsRunning.value = operationsRunning.value!! - 1
                },
                { exception ->
                    Log.e(TAG, "getFreeTimeEvents: $exception")
                    handleException(exception)
                    operationsRunning.value = operationsRunning.value!! - 1
                })

        compositeDisposable.add(disposable)
    }


    // MARK: Exceptions

    private fun handleException(exception: Throwable) {
        var text = PassableStringResource(R.string.exceptionUnknown, listOf(exception.toString()))

        if (exception is GoogleAccountNotFoundException) {
            // Prompt the user to select a new google account
            Log.e(TAG, "Used google account not found.")
            text = PassableStringResource(R.string.exceptionGoogleAccountNotFound)
        } else if (exception is HttpException) {
            Log.e(TAG, "Retrofit 2 HTTP ${exception.code()} exception: ${exception.response()}")
            if (exception.code() == 500) {
                text = PassableStringResource(R.string.exceptionCTUInternal)
            } else if (exception.code() == 404) {
                text = PassableStringResource(R.string.exceptionTimetableNotFound)
            } else if (exception.code() == 403) {
                text = PassableStringResource(R.string.exceptionUnauthorized, listOf(timetableOwner.value!!.first))
                timetableOwner.postValue(Pair(ctuUsername, ItemType.PERSON))
            }
        } else if (exception is NoInternetConnectionException) {
            Log.e(TAG, "Could not connect to the internet.")
            text = PassableStringResource(R.string.exceptionInternetUnavailable)
        } else if (exception is SocketTimeoutException) {
            text = PassableStringResource(R.string.exceptionSocket)
        } else {
            Log.e(TAG, "Unknown exception occurred: $exception")
        }

        thrownException.postValue(exception)
        showMessage.postValue(text)
    }

    companion object {
        fun withMiddleDate(date: DateTime): Interval {
            val dateStart = date.minusWeeks(MyApplication.NUM_OF_WEEKS_TO_UPDATE).withTimeAtStartOfDay()
            val dateEnd = date.plusWeeks(MyApplication.NUM_OF_WEEKS_TO_UPDATE).withTimeAtStartOfDay()

            return Interval(dateStart, dateEnd)
        }
    }
}