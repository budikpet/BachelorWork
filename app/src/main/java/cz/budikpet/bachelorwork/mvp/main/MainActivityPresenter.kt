package cz.budikpet.bachelorwork.mvp.main

import android.content.Intent
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.dataModel.ItemType
import cz.budikpet.bachelorwork.dataModel.Model
import cz.budikpet.bachelorwork.util.AppAuthHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import javax.inject.Inject

class MainActivityPresenter(
    private var mainActivityView: MainActivityView?,
    private val mainActivityModel: MainActivityModel
) : MainActivityModel.Callbacks {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthHolder: AppAuthHolder

    @Inject
    internal lateinit var siriusApiServe: SiriusApiService
    private var disposable: Disposable? = null

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        appAuthHolder.close()
        mainActivityModel.onDestroy()
        mainActivityView = null
    }

    // MARK: Functions

    fun checkAuthorization(intent: Intent) {
        if (appAuthHolder.isAuthorized()) {
            Log.i(TAG, "Already authorized.")
            // TODO: Check access token to refresh?
        } else {
            Log.i(TAG, "Not authorized")
            appAuthHolder.startAuthCodeExchange(intent)
        }
    }

    fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = appAuthHolder.authStateManager.authState
        val clearedState = AuthState(currentState?.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        appAuthHolder.authStateManager.authState = clearedState
    }

    fun getEvents(itemType: ItemType, id: String) {
        performActionWithFreshTokens(
            AuthState.AuthStateAction()
            { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                // Check for errors and expired tokens
                if (accessToken == null) {
                    Log.e(TAG, "Request failed: $ex")

                    // Its possible the access token expired
                    appAuthHolder.startRefreshAccessToken()

                } else {
                    // Prepare the endpoint call
                    var observable = when (itemType) {
                        ItemType.COURSE -> siriusApiServe.getCourseEvents(accessToken = accessToken, id = id)
                        ItemType.PERSON -> siriusApiServe.getPersonEvents(accessToken = accessToken, id = id)
                        ItemType.ROOM -> siriusApiServe.getRoomEvents(accessToken = accessToken, id = id)
                    }
                    this.getEvents(observable)
                }

            })
    }

    private fun getEvents(observable: Observable<Model.EventsResult>) {
        disposable = observable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
//            .map { t -> t.events }
            .subscribe(
                { result ->
                    Log.i(TAG, "Events: $result")
                    onEventsResult(result)
                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }

    /**
     * Makes using the performActionWithFreshTokens method a bit easier.
     */
    private fun performActionWithFreshTokens(action: AuthState.AuthStateAction) {
        appAuthHolder.authStateManager.authState?.performActionWithFreshTokens(appAuthHolder.authService, action)
    }

    // MARK: @MainActivityModel.Callbacks interface implementation

    override fun onTokenReceived(accessToken: String?) {
        mainActivityView?.showString("AccessToken: ${accessToken}")
    }

    override fun onTokenError() {

    }

    override fun onEventsResult(result: Model.EventsResult) {
        var builder = StringBuilder()
        for (event in result.events) {
            builder.append("${event.links.course} ${event.event_type}: ${event.starts_at}\n")
        }

        mainActivityView?.showString(builder.toString())
    }
}