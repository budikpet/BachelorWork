package cz.budikpet.bachelorwork.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.EventsResult
import cz.budikpet.bachelorwork.data.models.GoogleCalendarListItem
import cz.budikpet.bachelorwork.data.models.GoogleCalendarMetadata
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.joda.time.DateTime
import java.util.*
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

    init {
        MyApplication.appComponent.inject(this)

        // Calendar calendarService
        val transport = NetHttpTransport.Builder().build()

//        val transport = AndroidHttp.newCompatibleTransport();

        calendarService = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), setHttpTimeout(credential))
            .setApplicationName("BachelorWork")
            .build()

    }

    // MARK: Sirius API

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        appAuthManager.checkAuthorization(response, exception)
    }

    fun signOut() {
        appAuthManager.signOut()
    }

    /**
     * Provide course, person and room events endpoints.
     *
     * @return An observable @SiriusApi.EventsResult endpoint.
     */
    fun searchSiriusApiEvents(itemType: ItemType, id: String): Observable<EventsResult> {
        return appAuthManager.getFreshAccessToken()
            .toObservable()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io()) // Observe the refreshAccessToken operation on a non-main thread.
            .flatMap { accessToken ->
                getEventsObservable(accessToken, itemType, id)
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
    private fun getEventsObservable(
        accessToken: String,
        itemType: ItemType,
        id: String
    ): Observable<EventsResult> {
        return when (itemType) {
            ItemType.COURSE -> siriusApiService.getCourseEvents(accessToken = accessToken, id = id, from = "2019-3-2")
            ItemType.PERSON -> siriusApiService.getPersonEvents(accessToken = accessToken, id = id, from = "2019-3-2")
            ItemType.ROOM -> siriusApiService.getRoomEvents(accessToken = accessToken, id = id, from = "2019-3-2")
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
     * Increases timeouts for the Google service requests.
     */
    private fun setHttpTimeout(requestInitializer: HttpRequestInitializer): HttpRequestInitializer {
        return HttpRequestInitializer { httpRequest ->
            requestInitializer.initialize(httpRequest)
            httpRequest.connectTimeout = 3 * 60000  // 3 minutes connect timeout
            httpRequest.readTimeout = 3 * 60000  // 3 minutes read timeout
        }
    }

    fun getGoogleCalendarList(): Single<CalendarList> {
        val FIELDS = "id,summary"
        val FEED_FIELDS = "items($FIELDS)"

        return getGoogleCalendarServiceObservable()
            .flatMap { calendar ->
                Single.fromCallable {
                    calendar.calendarList().list().setFields(FEED_FIELDS).execute()
                }
            }
    }

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

        return Observable.create<GoogleCalendarListItem> { emitter ->
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(projectionDisplayNameIndex)
                val id = cursor.getString(projectionIdIndex)

                emitter.onNext(GoogleCalendarListItem(id, displayName))
            }
            emitter.onComplete()
        }
    }

    fun getGoogleCalendarEvents(name: String): Observable<TimetableEvent> {
        val eventProjection: Array<String> = arrayOf(
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION
        )

        val projectionDisplayNameIndex = 0
        val projectionTitleIndex = 1
        val projectionDTStartIndex = 2
        val projectionDTEndIndex = 3
        val projectionDescIndex = 4
        val projectionLocationIndex = 5

        val uri: Uri = CalendarContract.Events.CONTENT_URI
        val selection =
            "((${CalendarContract.Events.DTSTART} > ?) AND (${CalendarContract.Events.CALENDAR_DISPLAY_NAME} = ?))"
        val selectionArgs: Array<String> = arrayOf("${Date().time - 48 * 60 * 60 * 1000}", name)

        return Observable.create<TimetableEvent> { emitter ->
            val cursor = context.contentResolver.query(uri, eventProjection, selection, selectionArgs, null)

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(projectionDisplayNameIndex)
                val title = cursor.getString(projectionTitleIndex)
                val desc = cursor.getString(projectionDescIndex)
                val location = cursor.getString(projectionLocationIndex)
                val dateStart = DateTime(cursor.getLong(projectionDTStartIndex))
                val dateEnd = DateTime(cursor.getLong(projectionDTEndIndex))

                val metadata = Gson().fromJson(desc, GoogleCalendarMetadata::class.java)

                val event = TimetableEvent(
                    metadata.id, starts_at = dateStart, ends_at = dateEnd,
                    event_type = EventType.COURSE_EVENT, capacity = 90, occupied = 0,
                    acronym = title, room = location, teachers = metadata.teachers,
                    students = metadata.students
                )
                emitter.onNext(event)

//                Log.i(TAG, " \n$desc")
//                Log.e(TAG, "#####################")
            }
            emitter.onComplete()
        }
    }

    fun addGoogleCalendarEvent(calId: Int, event: TimetableEvent) {
        val calendarMetadata = GoogleCalendarMetadata(event.id, event.teachers, event.students)
        val timezone = TimeZone.getDefault().toString()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.acronym)
            put(CalendarContract.Events.DTSTART, event.starts_at.millis)
            put(CalendarContract.Events.DTEND, event.ends_at.millis)
            put(CalendarContract.Events.DESCRIPTION, Gson().toJson(calendarMetadata))
            put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
        }
        val uri: Uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

        val eventID: Long = uri.lastPathSegment.toLong()

        Log.i(TAG, eventID.toString())
    }

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
                        // Make the calendar hidden
                        val entry: CalendarListEntry = createdCalendar.createMyEntry()
                        Single.fromCallable {
                            calendarService.calendarList()
                                .update(createdCalendar.id, entry)
                                .setColorRgbFormat(true)
                                .execute()
                        }
                    }
            }.toCompletable()
    }
}

/**
 * Make the entry hidden with a specific color.
 */
fun com.google.api.services.calendar.model.Calendar.createMyEntry(): CalendarListEntry {
    val entry = CalendarListEntry()
    entry.id = id
    entry.hidden = true
    entry.foregroundColor = "#000000"
    entry.backgroundColor = "#d3d3d3"

    return entry
}
