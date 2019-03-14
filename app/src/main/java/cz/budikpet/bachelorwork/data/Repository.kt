package cz.budikpet.bachelorwork.data

import android.arch.lifecycle.LiveData
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.SiriusApiClient
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class Repository() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var siriusApiClient: SiriusApiClient

    init {
        MyApplication.appComponent.inject(this)
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        appAuthManager.checkAuthorization(response, exception)
    }

    fun signOut() {
        appAuthManager.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return siriusApiClient.getSiriusApiEvents()
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        appAuthManager.performActionWithFreshTokens(
            AuthState.AuthStateAction()
            { accessToken: String?, _: String?, ex: AuthorizationException? ->
                // Check for errors and expired accessToken
                if (ex != null) {
                    Log.e(TAG, "Request failed: $ex.")

                    // 2007 == not fully authorized, accessToken expired
                    if (ex.code == 2007) {
                        // Refresh accessToken then search for events
                        val observable = appAuthManager.startRefreshAccessToken()
                        siriusApiClient.searchSiriusApiEvents(observable, itemType, id)
                    }

                } else {
                    siriusApiClient.searchSiriusApiEvents(accessToken!!, itemType, id)
                }

            })
    }
}