package cz.budikpet.bachelorwork.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
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
import com.google.api.services.calendar.model.CalendarList
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.gson.Gson
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Completable
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
    internal lateinit var credential: GoogleAccountCredential

    private lateinit var calendarService: Calendar

    private val mondayDate = DateTime().withDayOfWeek(DateTimeConstants.MONDAY)
        .withTime(0, 0, 0, 0)

    init {
        MyApplication.appComponent.inject(this)

        val transport = NetHttpTransport.Builder().build()
        calendarService = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), setHttpTimeout(credential))
            .setApplicationName("BachelorWork")
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

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        appAuthManager.checkAuthorization(response, exception)
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

        // TODO: Remove
        val endDateString = DateTime().withDate(2019, 3, 31).toString("YYYY-MM-dd")
        return when (itemType) {
            ItemType.COURSE -> siriusApiService.getCourseEvents(accessToken = accessToken, id = id, from = dateString, to = endDateString)
            ItemType.PERSON -> siriusApiService.getPersonEvents(accessToken = accessToken, id = id, from = dateString, to = endDateString)
            ItemType.ROOM -> siriusApiService.getRoomEvents(accessToken = accessToken, id = id, from = dateString, to = endDateString)
        }
    }

    // MARK: Google Calendar API

    fun getGoogleCalendarServiceObservable(): Single<Calendar> {
        return Single.create<Calendar> { emitter ->
            if (credential.selectedAccountName != null) {
                emitter.onSuccess(calendarService)
            } else {
                emitter.onError(UserNotAuthenticatedException())
            }
        }
    }

    /**
     * Refreshes local copies of all calendars of the account the app is using.
     *
     * @return A completable which completes when the refresh finishes.
     */
    fun refreshCalendars(): Completable {
        val authority = CalendarContract.Calendars.CONTENT_URI.authority

        val extras = Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)

        // Start refresh
        ContentResolver.requestSync(credential.selectedAccount, authority, extras)

        return Completable.create { emitter ->
            var refreshStarted = false  // set to true when refresh starts
            var restarted = false
            var loopCnt = 0             // number of loops made without refresh being started
            val timeout: Long = 30       // how many seconds can the loop keep going

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

                Thread.sleep(1000)
            }
        }
    }

    /**
     * Gets a list of calendars used by this application using Google Calendar API.
     */
    fun getGoogleCalendarList(): Single<CalendarList> {
        val FIELDS = "id,summary"
        val FEED_FIELDS = "items($FIELDS)"

        // TODO: Filter only used calendars
        return getGoogleCalendarServiceObservable()
            .flatMap { calendar ->
                Single.fromCallable {
                    calendar.calendarList().list().setFields(FEED_FIELDS).execute()
                }
            }
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
        val selectionArgs: Array<String> = arrayOf("%_BachelorWork%")

        val obs = Observable.create<GoogleCalendarListItem> { emitter ->
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

        return refreshCalendars().andThen(obs)
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

        val dateEnd = DateTime().withDate(2019, 3, 31)  // TODO: Remove
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

                // TODO: Check metadata if they are what they should be
                val metadata = Gson().fromJson(desc, GoogleCalendarMetadata::class.java)

                val event = TimetableEvent(
                    metadata.id, googleId = id, starts_at = dateStart, ends_at = dateEnd,
                    event_type = metadata.eventType, capacity = metadata.capacity,
                    occupied = metadata.occupied, acronym = title, room = location, teachers = metadata.teachers,
                    students = metadata.students, deleted = metadata.deleted
                )
                emitter.onNext(event)

//                Log.e(TAG, "$dateStart > $mondayDate == ${dateStart > mondayDate}")
//                Log.e(TAG, "#####################")
            }
            emitter.onComplete()
        }

        return refreshCalendars()
            .andThen(obs)
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
        return getGoogleCalendarServiceObservable()
            .flatMap { calendarService ->
                Single.fromCallable {
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
            }.toCompletable()
            .andThen(refreshCalendars())
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
