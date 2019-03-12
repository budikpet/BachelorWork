package cz.budikpet.bachelorwork.mvp.main

import android.content.Intent
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.dataModel.Model
import cz.budikpet.bachelorwork.util.AppAuthHolder
import io.reactivex.disposables.Disposable
import net.openid.appauth.*
import javax.inject.Inject

class MainActivityModel() {
    interface Callbacks {
        fun onTokenReceived(accessToken: String?)
        fun onTokenError()
        fun onEventsResult(result: Model.EventsResult)
    }

    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthHolder: AppAuthHolder

    init {
        MyApplication.appComponent.inject(this)
    }

    // MARK: Helper methods and flows

    fun onDestroy() {
    }
}