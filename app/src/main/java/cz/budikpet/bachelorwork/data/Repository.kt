package cz.budikpet.bachelorwork.data

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class Repository() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    // TODO: Change to MediatorLiveData
    private val events = MutableLiveData<List<Model.Event>>()

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var siriusApiServe: SiriusApiService
    private var disposable: Disposable? = null

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
     * Return currently selected events
     */
    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return events
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        appAuthManager.performActionWithFreshTokens(
            AuthState.AuthStateAction()
            { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                // Check for errors and expired tokens
                if (accessToken == null) {
                    Log.e(TAG, "Request failed: $ex")

                    // Its possible the access token expired
                    appAuthManager.startRefreshAccessToken()

                } else {
                    // Prepare the endpoint call
                    var endpoint = when (itemType) {
                        ItemType.COURSE -> siriusApiServe.getCourseEvents(accessToken = accessToken, id = id)
                        ItemType.PERSON -> siriusApiServe.getPersonEvents(accessToken = accessToken, id = id, from = "2019-3-1")
                        ItemType.ROOM -> siriusApiServe.getRoomEvents(accessToken = accessToken, id = id)
                    }
                    this.getSiriusApiEvents(endpoint)
                }

            })
    }

    private fun getSiriusApiEvents(endpoint: Observable<Model.EventsResult>) {
        disposable = endpoint
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
//            .map { t -> t.events }
            .subscribe(
                { result ->
                    Log.i(TAG, "Events: $result")
                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }
}