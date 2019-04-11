package cz.budikpet.bachelorwork

import android.Manifest
import android.app.Application
import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.di.DaggerAppComponent

class MyApplication : Application() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        internal lateinit var appComponent: AppComponent
        val calendarsName = "BachelorWork"

        const val CODE_REQUEST_PERMISSIONS = 1
        val requiredPerms: Array<String> = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_SYNC_STATS,
            Manifest.permission.GET_ACCOUNTS
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Create an instance of @AppComponent
        appComponent = DaggerAppComponent.builder()
            .appModule(AppModule(applicationContext))
            .build()
    }
}