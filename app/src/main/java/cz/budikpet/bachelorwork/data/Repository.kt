package cz.budikpet.bachelorwork.data

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.security.keystore.UserNotAuthenticatedException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.gson.Gson
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
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

    fun getCalendarEventsObservable(name: String): Observable<Event> {
        val EVENT_PROJECTION: Array<String> = arrayOf(
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION
        )

        val PROJECTION_DISPNAME_INDEX = 0
        val PROJECTION_TITLE_INDEX = 1
        val PROJECTION_DTSTART_INDEX = 2
        val PROJECTION_DTEND_INDEX = 3
        val PROJECTION_DESC_INDEX = 4
        val PROJECTION_LOCATION_INDEX = 5

        val uri: Uri = CalendarContract.Events.CONTENT_URI
        val selection =
            "((${CalendarContract.Events.DTSTART} > ?) AND (${CalendarContract.Events.CALENDAR_DISPLAY_NAME} = ?))"
        val selectionArgs: Array<String> = arrayOf("${Date().time - 48 * 60 * 60 * 1000}", name)

        return Observable.create<Event> { emitter ->
            val cursor = context.contentResolver.query(uri, EVENT_PROJECTION, selection, selectionArgs, null)

            // Use the cursor to step through the returned records
            while (cursor.moveToNext()) {
                // Get the field values
                val displayName = cursor.getString(PROJECTION_DISPNAME_INDEX)
                val title = cursor.getString(PROJECTION_TITLE_INDEX)
                val desc = cursor.getString(PROJECTION_DESC_INDEX)
                val location = cursor.getString(PROJECTION_LOCATION_INDEX)
                val dateStart = Date(cursor.getLong(PROJECTION_DTSTART_INDEX))
                val dateEnd = Date(cursor.getLong(PROJECTION_DTEND_INDEX))

                val metadata = Gson().fromJson(desc, CalendarMetadata::class.java)

                val event = Event(
                    metadata.id, starts_at = dateStart, ends_at = dateEnd,
                    event_type = EventType.COURSE_EVENT, capacity = 90, occupied = 0,
                    links = Links(location, title, metadata.teachers, metadata.students)
                )
                emitter.onNext(event)

//                Log.i(TAG, " \ndisplayName: $displayName\ntitle: $title\nstart: $dtstart")
//                Log.i(TAG, " \n$desc")
//                Log.e(TAG, "#####################")
            }
            emitter.onComplete()
        }
    }
}