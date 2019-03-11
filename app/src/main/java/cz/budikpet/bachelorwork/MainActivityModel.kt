package cz.budikpet.bachelorwork

import android.content.Intent
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.*

class MainActivityModel(appAuthHolder: AppAuthHolder) {
    interface OnFinishedListener {
        fun onTokenReceived(accessToken: String?)
        fun onTokenError()
        fun onEventsResult(result: Model.EventsResult)
    }

    private val TAG = "MY_Model"
    private val appAuthHolder = appAuthHolder

    private var disposable: Disposable? = null
    private val siriusApiServe by lazy {
        SiriusApiService.create()
    }

    // MARK: Helper methods and flows

    fun onDestroy() {
        disposable?.dispose()
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

    /**
     * Get new access tokens using the refresh token.
     */
    fun startRefreshAccessToken() {
        Log.i(TAG, "Refreshing access token")
        performTokenRequest(
            appAuthHolder.authStateManager.authState!!.createTokenRefreshRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                appAuthHolder.authStateManager.updateAfterTokenResponse(tokenResponse, authException)
                Log.i(TAG, "handleAccessTokenResponse")
            })
    }

    /**
     * Makes using the performActionWithFreshTokens method a bit easier.
     */
    fun performActionWithFreshTokens(action: AuthState.AuthStateAction) {
        appAuthHolder.authStateManager.authState?.performActionWithFreshTokens(appAuthHolder.authService, action)
    }

    // MARK: Authorization code exchange flow for tokens

    /**
     * We received data from the authorization server.
     *
     * @param intent It has the response and exception information of the authorization flow.
     */
    fun startAuthCodeExchange(onFinishedListener: OnFinishedListener, intent: Intent) {
        // We need to complete the authState
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            appAuthHolder.authStateManager.updateAfterAuthorization(response, ex)
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            appAuthHolder.authStateManager.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(onFinishedListener, response)
        } else if (ex != null) {
            Log.e(TAG, "Authorization flow failed: " + ex.message)
        } else {
            Log.e(TAG, "No authorization state retained - reauthorization required")
        }
    }

    /**
     * We received authorization code which needs to be exchanged for tokens.
     */
    private fun exchangeAuthorizationCode(
        onFinishedListener: OnFinishedListener,
        authorizationResponse: AuthorizationResponse
    ) {
        Log.i(TAG, "Exchanging authorization code")

        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                this.handleCodeExchangeResponse(
                    onFinishedListener,
                    tokenResponse,
                    authException
                )
            })
    }

    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {

        // FIT OAuth2 provider requires ClientSecret in Basic Authentication Header
        val clientAuthentication = ClientSecretBasic("eMaDK7iPVDlC09mb70pRc4OMIja37nQY")

        appAuthHolder.authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    private fun handleCodeExchangeResponse(
        onFinishedListener: OnFinishedListener,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {

        appAuthHolder.authStateManager.updateAfterTokenResponse(tokenResponse, authException)
        Log.i(TAG, "IsAuth: " + appAuthHolder.authStateManager.authState!!.isAuthorized)
        if (!appAuthHolder.authStateManager.authState!!.isAuthorized) {
            val message = "Authorization Code exchange failed" + if (authException != null) authException.error else ""

            // WrongThread inference is incorrect for lambdas
            Log.e(TAG, message)
        } else {
            // The Authorization Code exchange was successful
            Log.i(TAG, "AccessToken: ${appAuthHolder.authStateManager.authState?.accessToken}")
            Log.i(TAG, "RefreshToken: ${appAuthHolder.authStateManager.authState?.refreshToken}")
            onFinishedListener.onTokenReceived(appAuthHolder.authStateManager.authState?.accessToken)
        }
    }

    // MARK: API calls

    fun getEvents(onFinishedListener: OnFinishedListener) {
        Log.i(TAG, "GetEvents")
        Log.i(TAG, "AccessToken: ${appAuthHolder.authStateManager.authState?.accessToken}")
        Log.i(TAG, "RefreshToken: ${appAuthHolder.authStateManager.authState?.refreshToken}")

        performActionWithFreshTokens(AuthState.AuthStateAction()
        { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
            // Check for errors and expired tokens
            if (accessToken == null) {
                Log.e(TAG, "Request failed: $ex")

                // Its possible the access token expired
                startRefreshAccessToken()

            } else {
                // Call endpoints
//                testGetEvents(accessToken)
//                testSearch(accessToken)
//                testPeopleEvents(accessToken)
//                testRoomEvents(accessToken)
                testCourseEvents(onFinishedListener, accessToken)

            }

        })
    }

    private fun testCourseEvents(onFinishedListener: OnFinishedListener, accessToken: String?) {
        disposable = siriusApiServe.getCourseEvents(
            courseCode = "BI-AG2", accessToken = accessToken!!,
            from = "2019-1-1", to = "2019-4-1", limit = 10
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
//            .map { t -> t.events }
            .subscribe(
                { result ->
                    Log.i(TAG, "CourseEvents: $result")
                    onFinishedListener.onEventsResult(result)

                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }

    private fun testRoomEvents(accessToken: String?) {
        disposable = siriusApiServe.getRoomEvents(
            roomKosId = "TH:A-1231", accessToken = accessToken!!,
            from = "2019-1-1", to = "2019-3-1", limit = 1000
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { t -> t.events }
            .subscribe(
                { result -> Log.i(TAG, "RoomEvents: $result") },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }

    private fun testPeopleEvents(accessToken: String?) {
        disposable = siriusApiServe.getPersonEvents(
            username = "balikm", accessToken = accessToken!!,
            from = "2019-1-1", to = "2019-3-1", limit = 1000
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { t -> t.events }
            .subscribe(
                { result -> Log.i(TAG, "PersonEvents: $result") },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }

    private fun testSearch(accessToken: String?) {
        disposable = siriusApiServe.search(accessToken = accessToken!!, limit = 200, query = "Th")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    val types: MutableSet<SearchItemType> = hashSetOf();
                    for (item in result.results) {
                        types.add(item.type)
                    }

                    for (type in types) {
                        Log.i(TAG, "SearchItemType: $type")
                    }
                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }

    private fun testGetEvents(accessToken: String?) {
        disposable = siriusApiServe.getEvents(
            accessToken = accessToken!!,
            from = "2019-1-1", to = "2019-3-1", limit = 1000, event_type = EventType.COURSE_EVENT
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { t -> t.events }
            .subscribe(
                { result ->
                    Log.i(TAG, "Events: $result")
                    val types: MutableSet<EventType?> = hashSetOf();
                    for (event in result) {
                        types.add(event.event_type)
                    }

                    for (type in types) {
                        Log.i(TAG, "EventType: $type")
                    }
                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )
    }
}