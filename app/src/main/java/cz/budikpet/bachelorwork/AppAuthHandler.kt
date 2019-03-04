package cz.budikpet.bachelorwork

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import net.openid.appauth.*

class AppAuthHandler(context: Context) {
    private val TAG = "MY_AppAuthHandler"

    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    private val scope = "cvut:sirius:personal:read"

    private lateinit var context: Context
    private val authStateManager: AuthStateManager by lazy { AuthStateManager(context) }
    private lateinit var authRequest: AuthorizationRequest

    init {
        this.context = context

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

        authRequest = authRequestBuilder.build()
    }

    fun isAuthorized(): Boolean {
        return authStateManager.authState!!.isAuthorized
    }

    fun authorize() {
        val authService = AuthorizationService(context)

        var errorIntent = Intent(context, MainActivity::class.java)
        errorIntent.putExtra("TEST", "error")

        authService.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(context, 0, Intent(context, AppAuthTest::class.java), 0),
            PendingIntent.getActivity(context, 0, errorIntent, 0)
        )
    }
}