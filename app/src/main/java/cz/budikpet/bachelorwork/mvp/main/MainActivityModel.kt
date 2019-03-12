package cz.budikpet.bachelorwork.mvp.main

import android.content.Intent
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.dataModel.Model
import cz.budikpet.bachelorwork.util.AppAuthHolder
import io.reactivex.disposables.Disposable
import net.openid.appauth.*
import javax.inject.Inject

class MainActivityModel() {
    interface Callbacks {
        fun onTokenReceived(accessToken: String?)
        fun onTokenError()
        fun onEventsResult(result: Model.EventsResult)
    }

    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthHolder: AppAuthHolder

    @Inject
    internal lateinit var siriusApiServe: SiriusApiService
    private var disposable: Disposable? = null

    init {
        MyApplication.appComponent.inject(this)
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
    fun startAuthCodeExchange(callbacks: Callbacks, intent: Intent) {
        // We need to complete the authState
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            appAuthHolder.authStateManager.updateAfterAuthorization(response, ex)
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            appAuthHolder.authStateManager.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(callbacks, response)
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
        callbacks: Callbacks,
        authorizationResponse: AuthorizationResponse
    ) {
        Log.i(TAG, "Exchanging authorization code")

        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest(),
            AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                this.handleCodeExchangeResponse(
                    callbacks,
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
        callbacks: Callbacks,
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
            callbacks.onTokenReceived(appAuthHolder.authStateManager.authState?.accessToken)
        }
    }
}