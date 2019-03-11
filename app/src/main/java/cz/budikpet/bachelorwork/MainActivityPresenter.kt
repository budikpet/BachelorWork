package cz.budikpet.bachelorwork

import android.content.Intent
import android.util.Log
import javax.inject.Inject
import javax.inject.Named

class MainActivityPresenter(
    private var mainActivityView: MainActivityView?,
    private val mainActivityModel: MainActivityModel
) : MainActivityModel.Callbacks {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthHolder: AppAuthHolder

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        appAuthHolder.close()
        mainActivityModel.onDestroy()
        mainActivityView = null
    }

    // MARK: Functions

    fun checkAuthorization(intent: Intent) {
        if (appAuthHolder.isAuthorized()) {
            Log.i(TAG, "Already authorized.")
            // TODO: Check access token to refresh?
        } else {
            Log.i(TAG, "Not authorized")
            mainActivityModel.startAuthCodeExchange(this, intent)
        }
    }

    fun getEvents() {
        mainActivityModel.callSiriusApiEndpoint(this)
    }

    fun signOut() {
        mainActivityModel.signOut()
    }

    // MARK: interface

    override fun onTokenReceived(accessToken: String?) {
        mainActivityView?.showString("AccessToken: ${accessToken}")
    }

    override fun onTokenError() {

    }

    override fun onEventsResult(result: Model.EventsResult) {
        var builder: StringBuilder = StringBuilder()
        for (event in result.events) {
            builder.append("$event\n")
        }

        mainActivityView?.showString(builder.toString())
    }
}