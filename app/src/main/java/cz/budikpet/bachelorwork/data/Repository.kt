package cz.budikpet.bachelorwork.data

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.security.keystore.UserNotAuthenticatedException
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
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
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

    private val calendarService: Calendar
        get() {
            if (credential.selectedAccountName != null) {
                return field
            } else {
                throw UserNotAuthenticatedException()
            }
        }

    private val mondayDate = DateTime().withDayOfWeek(DateTimeConstants.MONDAY)
        .withTime(0, 0, 0, 0)

    init {
        MyApplication.appComponent.inject(this)

        val transport = NetHttpTransport.Builder().build()
        calendarService = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), setHttpTimeout(credential))
            .setApplicationName(MyApplication.calendarsName)
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

    // MARK: Sirius API

    fun getLoggedUserInfo(accessToken: String): Observable<AuthUserInfo> {
        return siriusAuthApiService.getUserInfo(accessToken)
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?): Single<String> {
        return appAuthManager.checkAuthorization(response, exception)
    }

    fun signOut() {
        appAuthManager.signOut()
    }

    /**
     * Uses search endpoint.
     */
    fun searchSirius(query: String): Observable<SearchItem> {
        return appAuthManager.getFreshAccessToken()
            .toObservable()
            .observeOn(Schedulers.io())
            .flatMap { accessToken ->
                siriusApiService.search(accessToken, 100, query = query)
                    .flatMap { searchResult ->
                        Observable.fromIterable(searchResult.results)
                    }
//                    .collect({ArrayList<SearchResult>()}, {arrayList, item: SearchResult -> arrayList.add(item) } )
            }
    }

    /**
     * Provides events endpoints of courses, people and rooms.
     *
     * @return An observable @EventsResult endpoint.
     */
    fun getSiriusEventsOf(itemType: ItemType, id: String): Observable<EventsResult> {
        return appAuthManager.getFreshAccessToken()
            .toObservable()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io()) // Observe the refreshAccessToken operation on a non-main thread.
            .flatMap { accessToken ->
                getSiriusEventsOf(accessToken, itemType, id)
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
        accessToken: String,
        itemType: ItemType,
        id: String
    ): Observable<EventsResult> {
        val dateString = mondayDate.toString("YYYY-MM-dd")

        val endDateString = mondayDate.plusDays(7).toString("YYYY-MM-dd")   // TODO: Remove
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
        }
    }

    // MARK: Google Calendar API

    /**
     * Refreshes local copies of all calendars of the account the app is using.
     *
     * @throws TimeoutException thrown using the emitter when the refresh reaches specified timeout
     * @return A completable which completes when the refresh finishes.
     */
    fun refreshCalendars(): Completable {
        return Completable.create { emitter ->
            // Start refresh
            val authority = CalendarContract.Calendars.CONTENT_URI.authority
            val extras = Bundle()
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)

            ContentResolver.requestSync(credential.selectedAccount, authority, extras)

            waitRefreshEnd(emitter, authority, extras)
        }
    }

    /**
     * Checks status of a refresh in a while loop. The loop either ends with timeout or when refresh ends.
     */
    private fun waitRefreshEnd(
        emitter: CompletableEmitter,
        authority: String?,
        extras: Bundle
    ) {
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
                    if (info.account == credential.selectedAccount && info.authority == authority) {
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
                ContentResolver.requestSync(credential.selectedAccount, authority, extras)
            }

            // Lower sleep needed when we're waiting for refresh to start
            Thread.sleep(if (refreshStarted) 1000 else 100)
        }
    }

    /**
     * Gets a list of calendars used by the application using Google Calendar API.
     */
    fun getGoogleCalendarList(): Single<MutableList<CalendarListEntry>> {
        val FIELDS = "id,summary,hidden"
        val FEED_FIELDS = "items($FIELDS)"

        return Single.fromCallable {
            calendarService.calendarList().list()
                .setFields(FEED_FIELDS).setShowHidden(true).setMaxResults(240)
                .execute()
        }
            .flatMapObservable { Observable.fromIterable(it.items) }
            .filter { it.summary.contains(MyApplication.calendarsName) }
            .toList()
    }

    fun updateGoogleCalendarList(entry: CalendarListEntry): Single<CalendarListEntry> {
        return Single.fromCallable { calendarService.calendarList().update(entry.id, entry).execute() }
    }

    /**
     * Gets a list of calendars used by this application using Android calendar provider.
     */
    fun getLocalCalendarList(): Observable<GoogleCalendarListItem> {
        val eventProjection: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )

        val projectionIdIndex = 0
        val projectionDisplayNameIndex = 1

        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val selection =
            "${CalendarContract.Events.CALENDAR_DISPLAY_NAME} LIKE ?"
        val selectionArgs: Array<String> = arrayOf("%_${MyApplication.calendarsName}%")

        return Observable.create { emitter ->
            Log.i(TAG, "Checking local calendars")
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(projectionDisplayNameIndex)
                val id = cursor.getInt(projectionIdIndex)

                emitter.onNext(GoogleCalendarListItem(id, displayName))
            }
            emitter.onComplete()
        }
    }

    /**
     * Gets calendar events from a calendar using Android calendar provider.
     *
     * @param calId id of the calendar we add event to. Received from a list of calendars using Android Calendar provider.
     */
    fun getGoogleCalendarEvents(calId: Int): Observable<TimetableEvent> {
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
                "AND (${CalendarContract.Events.DTEND} < ?))"

        val dateEnd = mondayDate.plusDays(7)  // TODO: Remove
        val selectionArgs: Array<String> = arrayOf("${mondayDate.millis}", "$calId", "${dateEnd.millis}")

        val obs = Observable.create<TimetableEvent> { emitter ->
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)
            Log.i(TAG, "getGoogleCalendarEvents: received ${cursor.count}")

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
                    metadata = Gson().fromJson(desc, GoogleCalendarMetadata::class.java)
                } catch (e: JsonSyntaxException) {
                    // When error occurs, default metadata is used which makes the faulty event deleted
                    Log.e(TAG, "Metadata error: $e")
                }

                val event = TimetableEvent(
                    metadata.id, googleId = id, starts_at = dateStart, ends_at = dateEnd,
                    event_type = metadata.eventType, capacity = metadata.capacity,
                    occupied = metadata.occupied, acronym = title, room = location, teachers = metadata.teachers,
                    students = metadata.students, deleted = metadata.deleted
                )
                emitter.onNext(event)
            }
            emitter.onComplete()
        }

        return obs
    }

    fun updateGoogleCalendarEvent(eventId: Long, event: TimetableEvent): Single<Int> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teachers, event.students, event.capacity,
            event.occupied, event.event_type, deleted = event.deleted
        )
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.acronym)
            put(CalendarContract.Events.DTSTART, event.starts_at.millis)
            put(CalendarContract.Events.DTEND, event.ends_at.millis)
            put(CalendarContract.Events.DESCRIPTION, Gson().toJson(calendarMetadata))
        }
        val updateUri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Single.fromCallable {
            val rows: Int = context.contentResolver.update(updateUri, values, null, null)
            Log.i(TAG, "Rows updated: $rows")
            return@fromCallable rows
        }
    }

    /**
     * Add a new event into a calendar using Android Calendar provider.
     *
     * @param calId id of the calendar we add event to. Received from a list of calendars using Android Calendar provider.
     * @param event event to be added into the calendar.
     */
    fun addGoogleCalendarEvent(calId: Int, event: TimetableEvent): Single<Long> {
        val calendarMetadata = GoogleCalendarMetadata(
            event.siriusId, event.teachers, event.students, event.capacity,
            event.occupied, event.event_type
        )
        val timezone = TimeZone.getDefault().toString()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
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
     * Create and add a new secondary Google calendar using GoogleCalendar API.
     *
     * @param name name of the new calendar.
     */
    fun addSecondaryGoogleCalendar(name: String): Completable {
        return Single.fromCallable {
            // Create the calendar
            val calendarModel = com.google.api.services.calendar.model.Calendar()
            calendarModel.summary = name

            calendarService.calendars()
                .insert(calendarModel)
                .setFields("id,summary")
                .execute()
        }
            .subscribeOn(Schedulers.io())
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
            .toCompletable()
    }

    fun sharePersonalCalendar(email: String): Single<AclRule> {
        val username = sharedPreferences.getString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), null)
        val calendarName = "${username}_${MyApplication.calendarsName}"

        // Create access rule with associated scope
        val rule = AclRule()
        val scope = AclRule.Scope()
        scope.setType("user").value = email
        rule.setScope(scope).role = "reader"

        return getGoogleCalendar(calendarName)
            .flatMap { calendar ->
                Single.fromCallable {
                    return@fromCallable calendarService.acl().insert(calendar.id, rule).setSendNotifications(false)
                        .execute()
                }
            }
    }

    fun unsharePersonalCalendar(email: String): Completable {
        val username = sharedPreferences.getString(SharedPreferencesKeys.SIRIUS_USERNAME.toString(), null)
        val calendarName = "${username}_${MyApplication.calendarsName}"

        val ruleId = "user:$email"

        return getGoogleCalendar(calendarName)
            .flatMapCompletable { calendar ->
                Completable.fromCallable {
                    calendarService.acl().delete(calendar.id, ruleId).execute();
                }
            }
    }

    private fun getGoogleCalendar(calendarName: String): Single<CalendarListEntry> {
        return getGoogleCalendarList()
            .flatMapObservable { calendarList ->
                Observable.fromIterable(calendarList)
            }
            .filter { it.summary == calendarName }
            .singleOrError()
    }
}

/**
 * Make the entry hidden with a specific color.
 */
fun com.google.api.services.calendar.model.Calendar.createMyEntry(): CalendarListEntry {
    val entry = CalendarListEntry()
    entry.id = id
    entry.hidden = false
    entry.foregroundColor = "#000000"
    entry.backgroundColor = "#d3d3d3"

    return entry
}
