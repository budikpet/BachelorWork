package cz.budikpet.bachelorwork

import android.content.Context
import android.R.id.edit
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import net.openid.appauth.AuthState
import android.support.annotation.NonNull



class AuthStateManager(context: Context) {
    private final val prefPath = "auth"
    private final val STATE_KEY = "stateJson"
    private val authPrefs: SharedPreferences = context.getSharedPreferences(prefPath, MODE_PRIVATE);

    var authState: AuthState? = null
        get() {
            if(authState != null) {
                return authState
            } else {
                return readAuthState()
            }
        }
        set(authState: AuthState?) {
            if(authState == null) {
                writeAuthState(AuthState())
            } else {
                writeAuthState(authState!!)
            }
            field = authState
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
}