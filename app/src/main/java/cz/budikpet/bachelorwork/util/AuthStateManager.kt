package cz.budikpet.bachelorwork.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import net.openid.appauth.*


class AuthStateManager(context: Context) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val prefPath = "auth"
    private val STATE_KEY = "stateJson"
    private val authPrefs: SharedPreferences = context.getSharedPreferences(prefPath, MODE_PRIVATE);

    var authState: AuthState? = null
        get() {
            return if (field != null) field else readAuthState()
        }
        set(authState: AuthState?) {
            field = if (authState == null) AuthState() else authState

            writeAuthState(field!!)

        }

    /**
     * Either read the AuthState value from SharedPreferences, or create it.
     *
     * @return Current AuthState.
     */
    private fun readAuthState(): AuthState {
        val stateJson = authPrefs.getString(STATE_KEY, "{}")
        return if (stateJson != null) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState()
        }
    }

    /**
     * Write @param state into SharedPreferences.
     * @param state The AuthState to be written into SharedPreferences.
     */
    private fun writeAuthState(state: AuthState) {
        authPrefs.edit()
            .putString(STATE_KEY, state.jsonSerializeString())
            .apply()
    }

    private fun replace(state: AuthState): AuthState {
        this.authState = state
        return state
    }

    /**
     * Update the AuthState after the authorization code has been received.
     */
    fun updateAfterAuthorization(
        response: AuthorizationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = authState!!
        current.update(response, ex)
        return replace(current)
    }

    /**
     * Update the AuthState after the authorization code has been exchanged for tokens.
     */
    fun updateAfterTokenResponse(response: TokenResponse?, ex: AuthorizationException?): AuthState {
        val current = authState!!
        current.update(response, ex)
        return replace(current)
    }

    fun updateAfterRegistration(
        response: RegistrationResponse,
        ex: AuthorizationException?
    ): AuthState {
        val current = authState!!
        if (ex != null) {
            return current
        }

        current.update(response)
        return replace(current)
    }
}