package cz.budikpet.bachelorwork

import android.content.Context
import android.net.Uri

class AppAuthHandler(context: Context) {
    private val clientId = "1932312b-4981-4224-97b1-b45ad041a4b7"
    private val redirectUri = Uri.parse("net.openid.appauthdemo:/oauth2redirect")
    private val scope = "cvut:sirius:personal:read"

    private val authStateManager: AuthStateManager by lazy { AuthStateManager(context) }

    fun isAuthorized(): Boolean {
        return authStateManager.authState!!.isAuthorized
    }
}