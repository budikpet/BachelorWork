package cz.budikpet.bachelorwork.util

import android.content.Context
import android.net.Uri
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import io.reactivex.Single
import net.openid.appauth.*
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Inject singleton
/**
 * Holds information needed to authorize with OAuth 2.0 server and manage tokens.
 */
@Singleton
class AppAuthManager @Inject constructor(context: Context) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    //    private val scope = "cvut:sirius:limited-by-idm:read" // TODO: Use this scope when available
    private val scope = "cvut:sirius:personal:read"

    private val authStateManager: AuthStateManager = AuthStateManager(context)
    private val authService: AuthorizationService
    val authRequest: AuthorizationRequest

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

    fun getAccessToken(): String? {
        return authStateManager.authState?.accessToken
    }

    fun close() {
        authService.dispose()
    }

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

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        if (isAuthorized()) {
            Log.i(TAG, "Already authorized.")
            startRefreshAccessToken()
        } else {
            Log.i(TAG, "Not authorized")
            startAuthCodeExchange(response, exception)
        }
    }

    /**
     * Wraps the performActionWithFreshTokens as an observable.
     *
     * If accessToken is fresh, it is used directly.
     *
     * If accessToken isn't fresh, the startRefreshAccessToken observable is used.
     *
     * @return An observable with fresh accessToken.
     */
    internal fun getFreshAccessToken(): Single<String> {

        return Single.create<String> { emitter ->
            performActionWithFreshTokens(AuthState.AuthStateAction { accessToken, _, ex ->
                if (ex != null) {
                    Log.e(TAG, "Request failed: $ex.")
                    // Need to refresh the accessToken
                    emitter.onError(UserNotAuthenticatedException())
                } else {
                    // AccessToken is fresh
                    emitter.onSuccess(accessToken!!)
                }
            })
        }.onErrorResumeNext {
            // Refresh accessToken then search for events
            return@onErrorResumeNext startRefreshAccessToken()
        }
    }

    /**
     * Get new access tokens using the refresh token.
     */
    private fun startRefreshAccessToken(): Single<String> {
        Log.i(TAG, "PRE_RefreshToken: ${getAccessToken()}")
        return Single.create { emitter ->
            performTokenRequest(
                authStateManager.authState!!.createTokenRefreshRequest(),
                AuthorizationService.TokenResponseCallback { tokenResponse, authException ->
                    authStateManager.updateAfterTokenResponse(tokenResponse, authException)
                    Log.i(TAG, "POST_RefreshToken: ${getAccessToken()}")
                    emitter.onSuccess(getAccessToken()!!)
                })
        }
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

    /**
     * Makes using the performActionWithFreshTokens method a bit easier.
     */
    private fun performActionWithFreshTokens(action: AuthState.AuthStateAction) {
        authStateManager.authState?.performActionWithFreshTokens(authService, action)
    }
}