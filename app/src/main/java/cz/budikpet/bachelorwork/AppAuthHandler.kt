package cz.budikpet.bachelorwork

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import net.openid.appauth.*
import okio.Okio
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppAuthHandler(context: Context) {
    private val TAG = "MY_AppAuthHandler"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    private val scope = "cvut:sirius:personal:read"

    private var context: Context = context
    private val authStateManager: AuthStateManager by lazy { AuthStateManager(context) }
    private val authService = AuthorizationService(context)
    private var authRequest: AuthorizationRequest

    private var mExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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
    }

    fun close() {
        if (authService != null) {
            authService.dispose()
        }
    }
    
    // MARK: User authorization

    fun isAuthorized(): Boolean {
        return authStateManager.authState!!.isAuthorized
    }

    fun authorize() {
        var errorIntent = Intent(context, MainActivity::class.java)
        errorIntent.putExtra("TEST", "error")

        authService.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(context, 0, Intent(context, AppAuthTest::class.java), 0),
            PendingIntent.getActivity(context, 0, errorIntent, 0)
        )
    }

    // MARK: Authorization code exchange flow for tokens

    fun startAuthCodeExchange(intent: Intent) {
        // We need to complete the authState
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            authStateManager.updateAfterAuthorization(response, ex);
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            authStateManager.updateAfterAuthorization(response, ex);
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            Log.e(TAG, "Authorization flow failed: " + ex.message)
        } else {
            Log.e(TAG, "No authorization state retained - reauthorization required")
        }
    }

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
        Log.i(TAG, tokenResponse!!.accessToken)
        if (!authStateManager.authState!!.isAuthorized()) {
            val message = "Authorization Code exchange failed" + if (authException != null) authException.error else ""

            // WrongThread inference is incorrect for lambdas
            Log.e(TAG, message)
        } else {
            Log.i("ACCESS_TOKEN", tokenResponse!!.accessToken)
        }
    }

    fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = authStateManager.authState
        val clearedState = AuthState(currentState?.getAuthorizationServiceConfiguration()!!)
        if (currentState.getLastRegistrationResponse() != null) {
            clearedState.update(currentState.getLastRegistrationResponse())
        }
        authStateManager.authState = clearedState
    }

    // MARK: API call methods

    fun getEvents() {
        authStateManager.authState?.performActionWithFreshTokens(authService, this::fetchCalendarData)
    }

    private fun fetchCalendarData(accessToken: String?, idToken: String?, ex: AuthorizationException?) {
        mExecutor.submit {
            try {
                val conn = URL("https://sirius.fit.cvut.cz/api/v1/people/budikpet/events?from=2018-10-29&to=2018-10-30")
                    .openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $accessToken")

                conn.instanceFollowRedirects = false
                val response = Okio.buffer(Okio.source(conn.inputStream))
                    .readString(Charset.forName("UTF-8"))
                Log.i(TAG, response)
            } catch (ioEx: IOException) {
                Log.e(TAG, "Network error when querying userinfo endpoint", ioEx)
            } catch (jsonEx: JSONException) {
                Log.e(TAG, "Failed to parse userinfo response")
            }
        }
    }
}