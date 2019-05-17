package cz.budikpet.bachelorwork.data

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Acl
import com.google.api.services.calendar.model.AclRule
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.api.SiriusAuthApiService
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.util.*
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import retrofit2.HttpException
import java.util.*
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton


@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@Singleton
open class Repository @Inject constructor(private val context: Context, var sharedPreferences: SharedPreferences) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var siriusApiService: SiriusApiService

    @Inject
    internal lateinit var siriusAuthApiService: SiriusAuthApiService

    @Inject
    internal lateinit var credential: GoogleAccountCredential


    /** Username of the CTU account that was used to log in. */
    val ctuUsername by lazy { sharedPreferences.getString(SharedPreferencesKeys.CTU_USERNAME.toString(), "") }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val calendarService: Calendar
        get() {
            if (credential.selectedAccountName != null) {
                return field
            } else {
                throw GoogleAccountNotFoundException()
            }
        }

    init {
        MyApplication.appComponent.inject(this)

        val transport = NetHttpTransport.Builder().build()
        calendarService = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), setHttpTimeout(credential))
            .setApplicationName(MyApplication.CALENDARS_NAME)
            .build()
    }

    /**
     * Increases timeouts for the Google service requests.
     */
    private fun setHttpTimeout(requestInitializer: HttpRequestInitializer): HttpRequestInitializer {
        return HttpRequestInitializer { httpRequest ->
            requestInitializer.initialize(httpRequest)
            httpRequest.connectTimeout = 3 * 60000  // 3 minutes connect timeout
            httpRequest.readTimeout = 3 * 60000  // 3 minutes read timeout
        }
    }

    /**
     *
     * @throws NoInternetConnectionException when the device could not connect to the internet.
     */
    private fun hasInternetConnection(): Single<Boolean> {
        return Single.just(isInternetAvailable())
            .map {
                if (!it) {
                    throw NoInternetConnectionException()
                }

                return@map it
            }
            .retry(5)
    }

    open fun saveCtuUsername(username: String) {
        if (!sharedPreferences.contains(SharedPreferencesKeys.CTU_USERNAME.toString())) {
            // Store the Sirius username
            sharedPreferences.edit {
                putString(SharedPreferencesKeys.CTU_USERNAME.toString(), username)
            }
        }
    }

    /**
     * Checks whether the device has internet connection. WiFi and/or Cellular if enabled.
     * @return true if the device is connected to the internet.
     */
    open fun isInternetAvailable(): Boolean {
        val useMobileDate = sharedPreferences.getBoolean(SharedPreferencesKeys.USE_MOBILE_DATA.toString(), false)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(activeNetwork)

            if (capabilities != null) {
                var result = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

                if (useMobileDate) {
                    result = result || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                }

                return result
            }
        }

        return false
    }

    // MARK: Sirius API

    open fun getLoggedUserInfo(accessToken: String): Observable<AuthUserInfo> {
        return hasInternetConnection()
            .flatMapObservable { siriusAuthApiService.getUserInfo(accessToken) }
    }

    open fun checkSiriusAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?): Single<String> {
        return hasInternetConnection()
            .flatMap { appAuthManager.checkSiriusAuthorization(response, exception) }
    }

    open fun signOut() {
        appAuthManager.signOut()
    }

    /**
     * Uses search endpoint.
     */
    open fun searchSirius(query: String): Observable<SearchItem> {
        return hasInternetConnection()
            .flatMap { appAuthManager.getFreshAccessToken() }
            .toObservable()
            .observeOn(Schedulers.io())
            .flatMap { accessToken ->
                siriusApiService.search(accessToken, query = query)
                    .flatMapObservable { searchResult ->
                        Observable.fromIterable(searchResult.results)
                    }
            }
            .retry { count, error ->
                Log.w(TAG, "SearchSirius retries: $count. Error: $error")
                return@retry count < 2 && !isSpecialError(error)
            }
    }

    /**
     * Provides events endpoints of courses, people and rooms.
     *
     * @return An observable @EventsResult endpoint.
     */
    open fun getSiriusEventsOf(
        itemType: ItemType,
        id: String,
        dateStart: DateTime,
        dateEnd: DateTime
    ): Observable<Event> {
        return hasInternetConnection()
            .flatMap { appAuthManager.getFreshAccessToken() }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io()) // Observe the refreshAccessToken operation on a non-main thread.
            .flatMap { accessToken ->
                getSiriusEventsOf(itemType, id, accessToken, dateStart, dateEnd)
            }
            .retry { count, error ->
                Log.w(TAG, "GetSiriusEventsOf retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
            .flatMapObservable { Observable.fromIterable(it.events) }
            .onErrorResumeNext { error: Throwable ->
                if(error is HttpException && error.code() == 403) {
                    // We are probably trying to Sirius update a timetable of another student
                    getSavedTimetables()
                        .flatMapObservable {
                            val savedTimetables = it.map { it.id }
                            if(savedTimetables.contains(id)) {
                                // This students timetable is shared via Google Calendar, leave it be
                                Observable.empty()
                            } else {
                                // This students timetable isn't shared, error
                                Observable.error<Event>(error)
                            }
                        }
                } else {
                    return@onErrorResumeNext Observable.error(error)
                }
            }
    }

    /**
     * Picks which events endpoint to call.
     *
     * @param itemType the type of endpoint we need to call.
     * @param id either persons name (budikpet), course ID (BI-AND) or room ID (T9-350)
     *
     * @return Observable SiriusApi endpoint data.
     */
    private fun getSiriusEventsOf(
        itemType: ItemType,
        id: String,
        accessToken: String,
        dateStart: DateTime,
        dateEnd: DateTime
    ): Single<EventsResult> {
        val dateString = dateStart.toString("YYYY-MM-dd")

        val endDateString = dateEnd.toString("YYYY-MM-dd")
        return when (itemType) {
            ItemType.COURSE -> siriusApiService.getCourseEvents(
                accessToken = accessToken,
                id = id,
                from = dateString,
                to = endDateString
            )
            ItemType.PERSON -> siriusApiService.getPersonEvents(
                accessToken = accessToken,
                id = id,
                from = dateString,
                to = endDateString
            )
            ItemType.ROOM -> siriusApiService.getRoomEvents(
                accessToken = accessToken,
                id = id,
                from = dateString,
                to = endDateString
            )
            ItemType.UNKNOWN -> {
                Log.e(TAG, "getSiriusEventsOf: ItemType.Unknown")
                Single.never<EventsResult>()
            }
        }
    }

    // MARK: Google Calendar

    /**
     * Starts the refresh of all calendars of the used google account asynchronously.
     * Does not wait for completion.
     *
     * The application does not wait
     */
    open fun startCalendarRefresh() {
        val extras = Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)

        ContentResolver.requestSync(
            credential.selectedAccount,
            CalendarContract.Calendars.CONTENT_URI.authority,
            extras
        )
    }

    /**
     * Starts the refresh of all calendars of the used google account and waits until it completes.
     *
     * @throws TimeoutException thrown using the emitter when the refresh reaches specified timeout
     * @return A completable which completes when the refresh finishes.
     */
    open fun refreshCalendars(): Completable {
        return appAuthManager.getFreshAccessToken()
            .flatMapCompletable {
                Completable.create { emitter ->
                    // Start refresh
                    startCalendarRefresh()

                    waitRefreshEnd(emitter)
                }
            }
    }

    /**
     * Checks status of a refresh in a while loop. The loop either ends with timeout or when refresh ends.
     */
    private fun waitRefreshEnd(emitter: CompletableEmitter) {
        var refreshStarted = false  // set to true when refresh starts
        var restarted = false
        var loopCnt = 0             // number of loops made without refresh being started
        val timeout: Long = 30       // how many loops can we make (30s when refresh started, 3s when it didn't)

        Log.i(TAG, "Refresh started")

        // Wait until refresh ends (when no items are being synchronized)
        while (true) {
            val sync = ContentResolver.getCurrentSyncs()
            if (sync.size > 0) {
                for (info in sync) {
                    if (info.account == credential.selectedAccount && info.authority == CalendarContract.Calendars.CONTENT_URI.authority) {
                        refreshStarted = true
                        Log.i(TAG, "Calendar refresh running")
                    }
                }
            } else {
                if (refreshStarted) {
                    Log.i(TAG, "Finished calendar refresh")
                    emitter.onComplete()
                    break
                } else {
                    loopCnt++
                }
            }

            if (!refreshStarted && restarted && loopCnt >= timeout) {
                // Restart didn't help, throw timeout exception
                emitter.onError(TimeoutException("Could not refresh the calendar."))
                break
            } else if (!restarted && loopCnt >= timeout / 2) {
                // Sometimes refresh doesn't start and needs to be restarted
                Log.i(TAG, "Restarting calendar refresh")
                loopCnt = 0
                restarted = true
                startCalendarRefresh()
            } else if (!isInternetAvailable()) {
                emitter.onError(NoInternetConnectionException())
                break
            }

            // Lower sleep needed when we're waiting for refresh to start
            Thread.sleep(if (refreshStarted) 1000 else 10)
        }
    }

    /**
     * Gets a list of calendars used by the application using Google Calendar API.
     */
    open fun getGoogleCalendarList(): Observable<CalendarListEntry> {
        val FIELDS = "id,summary,hidden"
        val FEED_FIELDS = "items($FIELDS)"

        return hasInternetConnection()
            .flatMap {
                Single.fromCallable {
                    calendarService.calendarList().list()
                        .setFields(FEED_FIELDS).setShowHidden(true).setMaxResults(240)
                        .execute()
                }
            }
            .retry { count, error ->
                Log.w(TAG, "GetGoogleCalendarList retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
            .flatMapObservable { Observable.fromIterable(it.items) }
            .filter { it.summary.contains(MyApplication.CALENDARS_NAME) }
    }

    /**
     * Gets only a calendar matching the calendarName using Google Calendar API.
     */
    open fun getGoogleCalendar(calendarName: String): Single<CalendarListEntry> {
        return getGoogleCalendarList()
            .filter { it.summary == calendarName }
            .singleOrError()
    }

    /**
     * Updates the CalendarList in the Google Calendar service using Google Calendar API.
     */
    open fun updateGoogleCalendarList(entry: CalendarListEntry): Single<CalendarListEntry> {
        return hasInternetConnection()
            .flatMap {
                Single.fromCallable { calendarService.calendarList().update(entry.id, entry).execute() }
            }
            .retry { count, error ->
                Log.w(TAG, "UpdateGoogleCalendarList retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
    }

    /**
     * Removes a calendar from the Google Calendar service using Google Calendar API.
     */
    open fun removeGoogleCalendar(entry: CalendarListEntry): Completable {
        val calendarServiceList = calendarService.calendarList().delete(entry.id)
        return hasInternetConnection()
            .map {
                calendarServiceList.execute()
                return@map it
            }
            .retry { count, error ->
                Log.w(TAG, "RemoveGoogleCalendar retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
            .ignoreElement()
    }

    /**
     * Gets a list of calendars used by this application using Android calendar provider.
     */
    open fun getLocalCalendarListItems(): Observable<CalendarListItem> {
        val eventProjection: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.SYNC_EVENTS
        )

        val projectionIdIndex = 0
        val projectionDisplayNameIndex = 1
        val projectionSyncEventsIndex = 2

        val accountName = sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), "")

        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val selection =
            "(${CalendarContract.Events.CALENDAR_DISPLAY_NAME} LIKE ?) AND (${CalendarContract.Events.ACCOUNT_NAME} = ?)"
        val selectionArgs: Array<String> = arrayOf("%_${MyApplication.CALENDARS_NAME}%", accountName)

        return Observable.create { emitter ->
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)
            Log.i(TAG, "Found ${cursor.count} used calendars.")

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(projectionDisplayNameIndex)
                val id = cursor.getLong(projectionIdIndex)
                val syncEvents = cursor.getInt(projectionSyncEventsIndex) == 1

                emitter.onNext(CalendarListItem(id, displayName, syncEvents))
            }
            cursor.close()
            emitter.onComplete()
        }
    }

    open fun getLocalCalendarListItem(siriusUsername: String): Single<CalendarListItem> {
        return getLocalCalendarListItems()
            .filter { it.displayName == MyApplication.calendarNameFromId(siriusUsername) }
            .firstOrError()
    }

    /**
     * Updates an event in a calendar using Android calendar provider.
     */
    open fun updateLocalCalendarList(calendarListItem: CalendarListItem): Single<Int> {

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.SYNC_EVENTS, calendarListItem.syncEvents)
        }
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarListItem.id)
        return Single.fromCallable {
            val rows: Int = context.contentResolver.update(updateUri, values, null, null)
            return@fromCallable rows
        }
    }

    /**
     * Gets calendar events from a calendar using Android calendar provider.
     *
     * @param calId id of the calendar we get events from. Received from a list of calendars using Android Calendar provider.
     */
    open fun getCalendarEvents(
        calId: Long,
        dateStart: DateTime,
        dateEnd: DateTime
    ): Observable<TimetableEvent> {
        val eventProjection: Array<String> = arrayOf(
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events._ID
        )

        val projectionDisplayNameIndex = 0
        val projectionTitleIndex = 1
        val projectionDTStartIndex = 2
        val projectionDTEndIndex = 3
        val projectionDescIndex = 4
        val projectionLocationIndex = 5
        val projectionIdIndex = 6

        val uri: Uri = CalendarContract.Events.CONTENT_URI
        val selection = "((${CalendarContract.Events.DTSTART} > ?) AND (${CalendarContract.Events.CALENDAR_ID} = ?)" +
                "AND (${CalendarContract.Events.DTEND} < ?)" +
                "AND (${CalendarContract.Calendars.DELETED} = ?))"

        val selectionArgs: Array<String> = arrayOf("${dateStart.millis}", "$calId", "${dateEnd.millis}", "0")

        val obs = Observable.create<TimetableEvent> { emitter ->
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)
            Log.i(TAG, "Received ${cursor.count} events from calendar $calId.")

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(projectionDisplayNameIndex)
                val title = cursor.getString(projectionTitleIndex)
                val desc = cursor.getString(projectionDescIndex)
                val location = cursor.getString(projectionLocationIndex)
                val dateStart = DateTime(cursor.getLong(projectionDTStartIndex))
                val dateEnd = DateTime(cursor.getLong(projectionDTEndIndex))
                val googleId = cursor.getLong(projectionIdIndex)

                // Get and check metadata
                var metadata = GoogleCalendarMetadata()
                try {
                    if (desc.count() > 0) {
                        metadata = Gson().fromJson(desc, GoogleCalendarMetadata::class.java)
                    } else {
                        metadata.id = -1
                    }
                } catch (e: JsonSyntaxException) {
                    // When error occurs, default metadata is used which makes the faulty event deleted
                    Log.e(TAG, "Metadata error: $e")
                    metadata.id = -1
                }

                val event = TimetableEvent(
                    metadata.id, starts_at = dateStart, ends_at = dateEnd,
                    event_type = metadata.eventType, capacity = metadata.capacity,
                    occupied = metadata.occupied, acronym = title, room = location, teacherIds = metadata.teacherIds,
                    deleted = metadata.deleted, fullName = metadata.fullName
                )
                event.googleId = googleId
                event.teachersNames.addAll(metadata.teacherNames)
                event.note = metadata.note
                event.changed = metadata.changed
                emitter.onNext(event)
            }
            cursor.close()
            emitter.onComplete()
        }

        return obs
    }

    /**
     * Updates an event in a calendar using Android calendar provider.
     */
    open fun updateCalendarEvent(event: TimetableEvent): Single<Int> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teacherIds, event.teachersNames, event.capacity,
            event.occupied, event.event_type, changed = true, deleted = event.deleted, note = event.note,
            fullName = event.fullName
        )
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.acronym)
            put(CalendarContract.Events.EVENT_LOCATION, event.room)
            put(CalendarContract.Events.DTSTART, event.starts_at.millis)
            put(CalendarContract.Events.DTEND, event.ends_at.millis)
            put(CalendarContract.Events.DESCRIPTION, Gson().toJson(calendarMetadata))
        }
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.googleId!!)
        return Single.fromCallable {
            val rows: Int = context.contentResolver.update(updateUri, values, null, null)
            return@fromCallable rows
        }
    }

    /**
     * Removes an event from the calendar using Android calendar provider.
     */
    open fun deleteCalendarEvent(event: TimetableEvent, deleteCompletely: Boolean = false): Single<Int> {
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.googleId!!)

        return if (deleteCompletely || event.siriusId == null) {
            Single.fromCallable {
                val rows: Int = context.contentResolver.delete(updateUri, null, null)
                return@fromCallable rows
            }
        } else {
            event.deleted = true
            updateCalendarEvent(event)
        }

    }

    /**
     * Add a new event into a calendar using Android Calendar provider.
     *
     * @param calId id of the calendar we add event to. Received from a list of calendars using Android Calendar provider.
     * @param event event to be added into the calendar.
     */
    open fun addCalendarEvent(calId: Long, event: TimetableEvent): Single<Long> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teacherIds, event.teachersNames, event.capacity,
            event.occupied, event.event_type, note = event.note, fullName = event.fullName
        )
        val timezone = TimeZone.getDefault().toString()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_LOCATION, event.room)
            put(CalendarContract.Events.TITLE, event.acronym)
            put(CalendarContract.Events.DTSTART, event.starts_at.millis)
            put(CalendarContract.Events.DTEND, event.ends_at.millis)
            put(CalendarContract.Events.DESCRIPTION, Gson().toJson(calendarMetadata))
            put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
        }
        return Single.fromCallable {
            val uri: Uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri.lastPathSegment.toLong()
        }
    }

    /**
     * Create and add a new secondary Google calendar using Google Calendar API.
     *
     * @param calendarName calendarName of the new calendar.
     */
    open fun addGoogleCalendar(calendarName: String): Completable {
        return hasInternetConnection()
            .flatMap {
                Single.fromCallable {
                    // Create the calendar
                    val calendarModel = com.google.api.services.calendar.model.Calendar()
                    calendarModel.summary = calendarName

                    calendarService.calendars()
                        .insert(calendarModel)
                        .setFields("id,summary")
                        .execute()
                }
            }
            .retry { count, error ->
                Log.w(TAG, "AddSecondaryGoogleCalendar retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
            .observeOn(Schedulers.io())
            .flatMap { createdCalendar ->
                Log.i(TAG, "Calendar created, changing its settings.")
                val entry: CalendarListEntry = createdCalendar.createMyEntry()
                Single.fromCallable {
                    calendarService.calendarList()
                        .update(createdCalendar.id, entry)
                        .setColorRgbFormat(true)
                        .execute()
                }
            }
            .ignoreElement()
    }

    /**
     * Shares calendar of the user with other person using Google Calendar API.
     */
    open fun sharePersonalCalendar(email: String): Single<AclRule> {
        val calendarName = MyApplication.calendarNameFromId(ctuUsername)

        // Create access rule with associated scope
        val rule = AclRule()
        val scope = AclRule.Scope()
        scope.setType("user").value = email
        rule.setScope(scope).role = "reader"

        return hasInternetConnection()
            .flatMap { getGoogleCalendar(calendarName) }
            .flatMap { calendar ->
                Single.fromCallable {
                    return@fromCallable calendarService.acl().insert(calendar.id, rule).setSendNotifications(false)
                        .execute()
                }
            }
            .retry { count, error ->
                Log.w(TAG, "SharePersonalCalendar retries: $count. Error: $error")
                return@retry count < 21 && !isSpecialError(error)
            }
    }

    /**
     * Stops sharing calendar of the user with the selected person using Google Calendar API.
     */
    open fun unsharePersonalCalendar(email: String): Completable {
        val calendarName = MyApplication.calendarNameFromId(ctuUsername)

        val ruleId = "user:$email"

        return hasInternetConnection()
            .flatMap { getGoogleCalendar(calendarName) }
            .flatMapCompletable { calendar ->
                Completable.fromCallable {
                    calendarService.acl().delete(calendar.id, ruleId).execute()
                }
            }
    }

    /**
     * Gets email addresses of people the user shares his timetable with using Google Calendar API.
     */
    open fun getEmails(calendarName: String): Observable<String> {
        return hasInternetConnection()
            .flatMap { getGoogleCalendar(calendarName) }
            .flatMapObservable { calendar ->
                Observable.fromCallable {
                    calendarService.acl().list(calendar.id).execute()
                }
            }
            .flatMap { Observable.fromIterable(it.items) }
            .filter { !it.id.contains("@group.calendar", ignoreCase = true) }
            .map { it.scope.value }
            .filter {
                val googleAccountName = sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), null)
                if(googleAccountName != null) {
                    return@filter it != googleAccountName
                }

                return@filter true
            }
    }

    open fun getSavedTimetables(): Single<List<SearchItem>> {
        return getLocalCalendarListItems()
            .flatMapMaybe {
                val username = MyApplication.idFromCalendarName(it.displayName)

                if (isInternetAvailable()) {
                    // We have internet connection so we can call search endpoint
                    return@flatMapMaybe searchSirius(username).firstElement()
                }

                return@flatMapMaybe Maybe.just(SearchItem(username, type = ItemType.UNKNOWN))
            }
            .toList()
            .map {
                it.sortedWith(Comparator { searchItem1, searchItem2 ->
                    searchItem1.type.ordinal - searchItem2.type.ordinal
                })
            }
    }

    /**
     * @return true for an error that should not be retried.
     */
    private fun isSpecialError(error: Throwable): Boolean {
        if (error !is NoInternetConnectionException) {
            return true
        } else if (error !is HttpException) {
            val error = error as HttpException
            if (error.code() == 403) {
                return true
            }
        }

        return false
    }
}
