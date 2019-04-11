package cz.budikpet.bachelorwork

import android.app.Application
import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.di.DaggerAppComponent

class MyApplication : Application() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        internal lateinit var appComponent: AppComponent
        val calendarsName = "BachelorWork"
    }

    override fun onCreate() {
        super.onCreate()

        // Create an instance of @AppComponent
        appComponent = DaggerAppComponent.builder()
            .appModule(AppModule(applicationContext))
            .build()
    }
}