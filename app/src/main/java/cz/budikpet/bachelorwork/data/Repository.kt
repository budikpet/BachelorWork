package cz.budikpet.bachelorwork.data

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class Repository @Inject constructor(context: Context) {
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
     * @return An observable @Model.EventsResult endpoint.
     */
    fun searchSiriusApiEvents(itemType: ItemType, id: String): Observable<Model.EventsResult> {
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
    ): Observable<Model.EventsResult> {
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
}