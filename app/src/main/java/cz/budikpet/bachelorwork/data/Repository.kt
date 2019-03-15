package cz.budikpet.bachelorwork.data

import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class Repository() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var siriusApiService: SiriusApiService

    init {
        MyApplication.appComponent.inject(this)
    }

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
}