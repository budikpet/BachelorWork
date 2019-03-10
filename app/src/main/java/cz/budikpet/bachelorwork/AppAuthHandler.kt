package cz.budikpet.bachelorwork

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.*

class AppAuthHandler(context: Context) {
    private val TAG = "MY_AppAuthHandler"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    private val scope = "cvut:sirius:personal:read"

    private var context: Context = context
    private val authStateManager: AuthStateManager = AuthStateManager(context)
    private val authService: AuthorizationService
    private var authRequest: AuthorizationRequest

    private var disposable: Disposable? = null
    private val siriusApiServe by lazy {
        SiriusApiService.create()
    }

    init {

        if (authStateManager.authState!!.authorizationServiceConfiguration == null) {
            Log.i(TAG, "auth config needs to be established")
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse("https://auth.fit.cvut.cz/oauth/authorize"), // authorization endpoint
                Uri.parse("https://auth.fit.cvut.cz/oauth/token") // token endpoint
            )
            authStateManager.authState = AuthState(serviceConfig)
        }

        val authRequestBuilder = AuthorizationRequest.Builder(
            authStateManager.authState!!.authorizationServiceConfiguration!!, // the authorization service configuration
            clientId, // the client ID, typically pre-registered and static
            ResponseTypeValues.CODE, // the response_type value: we want a code
            redirectUri // the redirect URI to which the auth response is sent
        ).setScope(scope)

        authRequest = authRequestBuilder.build()
        authService = AuthorizationService(context)
    }

    fun close() {
        authService.dispose()
        disposable?.dispose()
    }

    // MARK: User authorization

    /**
     * Starts the authorization flow.
     *
     * A user is redirected to CTU login page to provide username and password.
     */
    fun startAuthorization() {
        var errorIntent = Intent(context, CTULoginActivity::class.java)
        errorIntent.putExtra("TEST", "error")

        authService.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0),
            PendingIntent.getActivity(context, 0, errorIntent, 0)
        )
    }

    // MARK: Authorization code exchange flow for tokens

    /**
     * We received data from the authorization server.
     *
     * @param intent It has the response and exception information of the authorization flow.
     */
    fun startAuthCodeExchange(intent: Intent) {
        // We need to complete the authState
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            authStateManager.updateAfterAuthorization(response, ex)
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            authStateManager.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            Log.e(TAG, "Authorization flow failed: " + ex.message)
        } else {
            Log.e(TAG, "No authorization state retained - reauthorization required")
        }
    }

    /**
     * We received authorization code which needs to be exchanged for tokens.
     */
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        Log.i(TAG, "Exchanging authorization code")

        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                this.handleCodeExchangeResponse(
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

        authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {

        authStateManager.updateAfterTokenResponse(tokenResponse, authException)
        Log.i(TAG, "IsAuth: " + authStateManager.authState!!.isAuthorized)
        if (!authStateManager.authState!!.isAuthorized) {
            val message = "Authorization Code exchange failed" + if (authException != null) authException.error else ""

            // WrongThread inference is incorrect for lambdas
            Log.e(TAG, message)
        } else {
            // The Authorization Code exchange was successful
            Log.i(TAG, "AccessToken: ${authStateManager.authState?.accessToken}")
            Log.i(TAG, "RefreshToken: ${authStateManager.authState?.refreshToken}")
        }
    }

    // MARK: Helper methods and flows

    fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = authStateManager.authState
        val clearedState = AuthState(currentState?.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        authStateManager.authState = clearedState
    }

    /**
     * Get new access tokens using the refresh token.
     */
    fun startRefreshAccessToken() {
        Log.i(TAG, "Refreshing access token")
        performTokenRequest(
            authStateManager.authState!!.createTokenRefreshRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                authStateManager.updateAfterTokenResponse(tokenResponse, authException)
                Log.i(TAG, "handleAccessTokenResponse")
            })
    }

    fun isAuthorized(): Boolean {
        return authStateManager.authState!!.isAuthorized
    }

    // MARK: API endpoint call methods

    fun getEvents() {
        Log.i(TAG, "GetEvents")
        Log.i(TAG, "AccessToken: ${authStateManager.authState?.accessToken}")
        Log.i(TAG, "RefreshToken: ${authStateManager.authState?.refreshToken}")
        authStateManager.authState?.performActionWithFreshTokens(authService, this::fetchCalendarData)
    }

    private fun fetchCalendarData(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        // Check for errors and expired tokens
        if (ex != null) {
            Log.e(TAG, "Request failed: $ex")

            // Its possible the access token expired
            startRefreshAccessToken()
            return
        }

        // Call endpoints
//        testGetEvents(accessToken)
//        testSearch(accessToken)
//        testPeopleEvents(accessToken)
//        testRoomEvents(accessToken)
        testCourseEvents(accessToken)

    }

    private fun testCourseEvents(accessToken: String?) {
        disposable = siriusApiServe.getCourseEvents(
            courseCode = "BI-AG2", accessToken = accessToken!!,
            from = "2019-1-1", to = "2019-4-1", limit = 1000
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { t -> t.events }
            .subscribe(
                { result -> Log.i(TAG, "CourseEvents: $result") },
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