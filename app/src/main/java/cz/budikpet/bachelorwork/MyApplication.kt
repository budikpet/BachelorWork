package cz.budikpet.bachelorwork

import android.app.Application
import android.util.Log

class MyApplication: Application() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        internal lateinit var appComponent: AppComponent
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Created")
        appComponent = DaggerAppComponent.builder()
            .contextModule(ContextModule(applicationContext))
            .build()
    }
}