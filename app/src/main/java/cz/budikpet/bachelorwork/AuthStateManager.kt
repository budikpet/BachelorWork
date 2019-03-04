package cz.budikpet.bachelorwork

import android.content.Context
import android.R.id.edit
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.support.annotation.AnyThread
import android.support.annotation.NonNull
import android.text.TextUtils.replace
import android.util.Log
import net.openid.appauth.*


class AuthStateManager(context: Context) {
    private val TAG = "MY_MainActivity"

    private final val prefPath = "auth"
    private final val STATE_KEY = "stateJson"
    private val authPrefs: SharedPreferences = context.getSharedPreferences(prefPath, MODE_PRIVATE);

    var authState: AuthState? = null
        get() {
            if(field != null) {
                return field
            } else {
                return readAuthState()
            }
        }
        set(authState: AuthState?) {
            field = if(authState == null) AuthState() else authState

            writeAuthState(field!!)

        }

    private fun readAuthState(): AuthState {
        val stateJson = authPrefs.getString(STATE_KEY, "{}")
        val state: AuthState
        return if (stateJson != null) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState()
        }
    }

    private fun writeAuthState(state: AuthState) {
        authPrefs.edit()
            .putString(STATE_KEY, state.jsonSerializeString())
            .apply()
    }

    fun replace(state: AuthState): AuthState {
        this.authState = state
        return state
    }

    fun updateAfterTokenResponse(response: TokenResponse?, ex: AuthorizationException?): AuthState {
        val current = authState!!
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
    fun updateAfterAuthorization(
        response: AuthorizationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = authState!!
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
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