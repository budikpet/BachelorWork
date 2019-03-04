package cz.budikpet.bachelorwork

import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.AuthorizationRequest
import android.content.Intent
import android.app.PendingIntent
import android.util.Log
import net.openid.appauth.AuthorizationService

class MainActivity : AppCompatActivity() {
    private val TAG = "MY_MainActivity"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    private val scope = "cvut:sirius:personal:read"

    private val authStateManager: AuthStateManager by lazy { AuthStateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authStateManager.authState!!.isAuthorized()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, AppAuthTest::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        if(authStateManager.authState!!.authorizationServiceConfiguration == null) {
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

        val authRequest = authRequestBuilder.build()

        appAuthPendingIntent.setOnClickListener {
            val authService = AuthorizationService(this)

            authService.performAuthorizationRequest(
                authRequest,
                PendingIntent.getActivity(this, 0, Intent(this, AppAuthTest::class.java), 0),
                PendingIntent.getActivity(this, 0, Intent(this, AppAuthTest::class.java), 0)
            )
        }

    }
}
