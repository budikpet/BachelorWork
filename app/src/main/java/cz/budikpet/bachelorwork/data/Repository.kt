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

    /**
     * @return Variable that is to be observed. Contains currently selected events.
     */
    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return siriusApiClient.getSiriusApiEvents()
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        appAuthManager.performActionWithFreshTokens(
            AuthState.AuthStateAction()
            { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
                // Check for errors and expired tokens
                if (accessToken == null) {
                    Log.e(TAG, "Request failed: $ex")

                    // Its possible the access token expired
                    appAuthManager.startRefreshAccessToken()

                } else {
                    siriusApiClient.searchSiriusApiEvents(accessToken, itemType, id)
                }

            })
    }
}