package cz.budikpet.bachelorwork

import android.app.Application
import android.util.Log
import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.di.DaggerAppComponent
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException


class MyApplication : Application() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        const val CALENDARS_NAME = "CTUTimetable"
        const val NUM_OF_WEEKS_TO_UPDATE = 4

        internal lateinit var appComponent: AppComponent
    }

    override fun onCreate() {
        super.onCreate()

        // Create an instance of @AppComponent
        appComponent = DaggerAppComponent.builder()
            .appModule(AppModule(applicationContext))
            .build()

        // Create a handler for some undeliverable exceptions
        RxJavaPlugins.setErrorHandler { e ->
            if (e is IOException || e is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
//                Thread.currentThread().uncaughtExceptionHandler
//                    .handleException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
//                Thread.currentThread().uncaughtExceptionHandler.
//                    .handleException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            Log.w(TAG, "Undeliverable exception received, not sure what to do", e)
        }
    }
}