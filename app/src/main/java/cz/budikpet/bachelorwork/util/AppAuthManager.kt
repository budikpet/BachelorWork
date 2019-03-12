package cz.budikpet.bachelorwork.util

import android.content.Context
import android.net.Uri
import android.util.Log
import net.openid.appauth.*
import javax.inject.Inject

/**
 * Holds information needed to authorize with OAuth 2.0 server and manage tokens.
 */
class AppAuthManager @Inject constructor(context: Context) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    //    private val scope = "cvut:sirius:limited-by-idm:read" // TODO: Use this scope when available
    private val scope = "cvut:sirius:personal:read"

    val authStateManager: AuthStateManager = AuthStateManager(context)
    val authService: AuthorizationService
    var authRequest: AuthorizationRequest

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

    fun isAuthorized(): Boolean {
        return authStateManager.authState!!.isAuthorized
    }

    fun close() {
        authService.dispose()
    }

    fun getAccessToken(): String? {
        return authStateManager.authState?.accessToken
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

    // MARK: Authorization code exchange flow for tokens

    /**
     * We received data from the authorization server.
     *
     */
    fun startAuthCodeExchange(response: AuthorizationResponse?, exception: AuthorizationException?) {
        // We need to complete the authState

        if (response != null || exception != null) {
            authStateManager.updateAfterAuthorization(response, exception)
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            authStateManager.updateAfterAuthorization(response, exception)
            exchangeAuthorizationCode(response)
        } else if (exception != null) {
            Log.e(TAG, "Authorization flow failed: " + exception.message)
        } else {
            Log.e(TAG, "No authorization state retained - reauthorization required")
        }
    }

    /**
     * We received authorization code which needs to be exchanged for tokens.
     */
    private fun exchangeAuthorizationCode(
        authorizationResponse: AuthorizationResponse
    ) {
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
            Log.i(TAG, "AccessToken: ${getAccessToken()}")
            Log.i(TAG, "RefreshToken: ${authStateManager.authState?.refreshToken}")
        }
    }
}