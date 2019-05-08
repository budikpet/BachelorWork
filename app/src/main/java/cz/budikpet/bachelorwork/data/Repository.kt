package cz.budikpet.bachelorwork.data

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
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
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class Repository @Inject constructor(private val context: Context) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var siriusApiService: SiriusApiService

    @Inject
    internal lateinit var siriusAuthApiService: SiriusAuthApiService

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

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
        return Single.just(checkInternetConnection())
            .map {
                if (!it) {
                    throw NoInternetConnectionException()
                }

                return@map it
            }
            .retry(5)
    }

    /**
     * Checks whether the device has internet connection. WiFi and/or Cellular if enabled.
     * @return true if the device is connected to the internet.
     */
    fun checkInternetConnection(): Boolean {
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

    fun getLoggedUserInfo(accessToken: String): Observable<AuthUserInfo> {
        return hasInternetConnection()
            .flatMapObservable { siriusAuthApiService.getUserInfo(accessToken) }
    }

    fun checkSiriusAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?): Single<String> {
        return hasInternetConnection()
            .flatMap { appAuthManager.checkSiriusAuthorization(response, exception) }
    }

    fun signOut() {
        appAuthManager.signOut()
    }

    /**
     * Uses search endpoint.
     */
    fun searchSirius(query: String): Observable<SearchItem> {
        return hasInternetConnection()
            .flatMap { appAuthManager.getFreshAccessToken() }
            .toObservable()
            .observeOn(Schedulers.io())
            .flatMap { accessToken ->
                siriusApiService.search(accessToken, query = query)
                    .flatMap { searchResult ->
                        Observable.fromIterable(searchResult.results)
                    }
            }
            .retry { count, error ->
                Log.w(TAG, "SearchSirius retries: $count. Error: $error")
                return@retry count < 21 && error !is NoInternetConnectionException
            }
    }

    /**
     * Provides events endpoints of courses, people and rooms.
     *
     * @return An observable @EventsResult endpoint.
     */
    fun getSiriusEventsOf(
        itemType: ItemType, id: String,
        dateStart: DateTime,
        dateEnd: DateTime
    ): Observable<EventsResult> {
        return hasInternetConnection()
            .flatMap { appAuthManager.getFreshAccessToken() }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io()) // Observe the refreshAccessToken operation on a non-main thread.
            .flatMapObservable { accessToken ->
                getSiriusEventsOf(itemType, id, accessToken, dateStart, dateEnd)
            }
            .retry { count, error ->
                Log.w(TAG, "GetSiriusEventsOf retries: $count. Error: $error")
                return@retry count < 21 && error !is NoInternetConnectionException
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
    ): Observable<EventsResult> {
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
                Observable.empty()
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
    fun startCalendarRefresh() {
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
    fun refreshCalendars(): Completable {
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
            } else if (!checkInternetConnection()) {
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
    fun getGoogleCalendarList(): Observable<CalendarListEntry> {
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
                return@retry count < 21 && error !is NoInternetConnectionException
            }
            .flatMapObservable { Observable.fromIterable(it.items) }
            .filter { it.summary.contains(MyApplication.CALENDARS_NAME) }
    }

    fun getGoogleCalendar(calendarName: String): Single<CalendarListEntry> {
        return getGoogleCalendarList()
            .filter { it.summary == calendarName }
            .singleOrError()
    }

    /**
     * Updates the CalendarList in the Google Calendar service using Google Calendar API.
     */
    fun updateGoogleCalendarList(entry: CalendarListEntry): Single<CalendarListEntry> {
        return hasInternetConnection()
            .flatMap {
                Single.fromCallable { calendarService.calendarList().update(entry.id, entry).execute() }
            }
            .retry { count, error ->
                Log.w(TAG, "UpdateGoogleCalendarList retries: $count. Error: $error")
                return@retry count < 21 && error !is NoInternetConnectionException
            }
    }

    /**
     * Removes a calendar from the Google Calendar service using Google Calendar API.
     */
    fun removeGoogleCalendar(entry: CalendarListEntry): Completable {
        val calendarServiceList = calendarService.calendarList().delete(entry.id)
        return hasInternetConnection()
            .map {
                calendarServiceList.execute()
                return@map it
            }
            .retry { count, error ->
                Log.w(TAG, "RemoveGoogleCalendar retries: $count. Error: $error")
                return@retry count < 21 && error !is NoInternetConnectionException
            }
            .ignoreElement()
    }

    /**
     * Gets a list of calendars used by this application using Android calendar provider.
     */
    fun getLocalCalendarListItems(): Observable<CalendarListItem> {
        val eventProjection: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.SYNC_EVENTS
        )

        val projectionIdIndex = 0
        val projectionDisplayNameIndex = 1
        val projectionSyncEventsIndex = 2

        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val selection =
            "${CalendarContract.Events.CALENDAR_DISPLAY_NAME} LIKE ?"
        val selectionArgs: Array<String> = arrayOf("%_${MyApplication.CALENDARS_NAME}%")

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

    /**
     * Updates an event in a calendar using Android calendar provider.
     */
    fun updateLocalCalendarList(calendarListItem: CalendarListItem): Single<Int> {

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
    fun getCalendarEvents(
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
                val id = cursor.getLong(projectionIdIndex)

                // Get and check metadata
                var metadata = GoogleCalendarMetadata()
                try {
                    if(desc.count() > 0) {
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
                    deleted = metadata.deleted
                )
                event.googleId = id
                event.teachersNames.addAll(metadata.teacherNames)
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
    fun updateGoogleCalendarEvent(eventId: Long, event: TimetableEvent): Single<Int> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teacherIds, event.teachersNames, event.capacity,
            event.occupied, event.event_type, deleted = event.deleted
        )
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.acronym)
            put(CalendarContract.Events.EVENT_LOCATION, event.room)
            put(CalendarContract.Events.DTSTART, event.starts_at.millis)
            put(CalendarContract.Events.DTEND, event.ends_at.millis)
            put(CalendarContract.Events.DESCRIPTION, Gson().toJson(calendarMetadata))
        }
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Single.fromCallable {
            val rows: Int = context.contentResolver.update(updateUri, values, null, null)
            return@fromCallable rows
        }
    }

    /**
     * Removes an event from the calendar using Android calendar provider.
     */
    fun deleteGoogleCalendarEvent(eventId: Long): Single<Int> {
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Single.fromCallable {
            val rows: Int = context.contentResolver.delete(updateUri, null, null)
            return@fromCallable rows
        }
    }

    /**
     * Add a new event into a calendar using Android Calendar provider.
     *
     * @param calId id of the calendar we add event to. Received from a list of calendars using Android Calendar provider.
     * @param event event to be added into the calendar.
     */
    fun addGoogleCalendarEvent(calId: Long, event: TimetableEvent): Single<Long> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teacherIds, event.teachersNames, event.capacity,
            event.occupied, event.event_type
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
            // TODO: Make safe
            val uri: Uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri.lastPathSegment.toLong()
        }
    }

    /**
     * Create and add a new secondary Google calendar using Google Calendar API.
     *
     * @param calendarName calendarName of the new calendar.
     */
    fun addGoogleCalendar(calendarName: String): Completable {
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
                return@retry count < 21 && error !is NoInternetConnectionException
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
    fun sharePersonalCalendar(email: String): Single<AclRule> {
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
                return@retry count < 21 && error !is NoInternetConnectionException
            }
    }

    /**
     * Stops sharing calendar of the user with the selected person using Google Calendar API.
     */
    fun unsharePersonalCalendar(email: String): Completable {
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
}
