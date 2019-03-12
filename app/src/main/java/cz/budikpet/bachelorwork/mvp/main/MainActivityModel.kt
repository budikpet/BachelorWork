package cz.budikpet.bachelorwork.mvp.main

import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.dataModel.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import javax.inject.Inject

class MainActivityModel() {
    interface Callbacks {
        fun onTokenReceived(accessToken: String?)
        fun onTokenError()
        fun onEventsResult(result: Model.EventsResult)
    }

    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    init {
        MyApplication.appComponent.inject(this)
    }

    // MARK: Helper methods and flows

    fun onDestroy() {
    }
}