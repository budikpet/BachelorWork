package cz.budikpet.bachelorwork

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat.startActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_app_auth_test.*
import net.openid.appauth.*
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okio.Okio
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppAuthTest : AppCompatActivity() {
    private val TAG = "MY_AppAuthTest"
    private lateinit var mAuthService: AuthorizationService
    private lateinit var mExecutor: ExecutorService

    private val authStateManager: AuthStateManager by lazy { AuthStateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_auth_test)

        mExecutor = Executors.newSingleThreadExecutor()

        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )

        backBtn.setOnClickListener {
//            val mainIntent = Intent(this, MainActivity::class.java)
//            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
//            startActivity(mainIntent)
//            finish()
            signOut()
        }

        getEventsBtn.setOnClickListener {
            authStateManager.authState?.performActionWithFreshTokens(mAuthService, this::fetchCalendarData)
        }
    }

    override fun onStart() {
        super.onStart()

        if (authStateManager.authState!!.isAuthorized()) {
            Log.i(TAG, "Already authorized.")
            return
        } else {
            Log.i(TAG, "Not authorized")
        }

        // We need to complete the authState
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            authStateManager.updateAfterAuthorization(response, ex);
        }

        if (response != null && response.authorizationCode != null) {
            // authorization code exchange is required
            authStateManager.updateAfterAuthorization(response, ex);
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            Log.e(TAG, "Authorization flow failed: " + ex.message)
        } else {
            Log.e(TAG, "No authorization state retained - reauthorization required")
        }
    }

    // MARK: Authorization code flow

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
        callback: AuthorizationService.TokenResponseCallback) {

        // FIT OAuth2 provider requires ClientSecret in Basic Authentication Header
        val clientAuthentication = ClientSecretBasic("eMaDK7iPVDlC09mb70pRc4OMIja37nQY")

        mAuthService.performTokenRequest(
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

    private fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = authStateManager.authState
        val clearedState = AuthState(currentState?.getAuthorizationServiceConfiguration()!!)
        if (currentState.getLastRegistrationResponse() != null) {
            clearedState.update(currentState.getLastRegistrationResponse())
        }
        authStateManager.authState = clearedState

        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    // MARK: API call methods

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
