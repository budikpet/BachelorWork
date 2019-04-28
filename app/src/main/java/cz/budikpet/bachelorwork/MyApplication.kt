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
        internal lateinit var appComponent: AppComponent
        val calendarsName = "BachelorWork"
    }

    override fun onCreate() {
        super.onCreate()

        // Create an instance of @AppComponent
        appComponent = DaggerAppComponent.builder()
            .appModule(AppModule(applicationContext))
            .build()

        // Create a handler for some undeliverable exceptions
        RxJavaPlugins.setErrorHandler { ex ->
            // These exceptions occur sometimes when chains are interrupted and aren't crash worthy
            if (ex is IOException) {
                Log.i(TAG, "RxJava thrown an IOException: $ex")
            } else if (ex is SocketException) {
                Log.i(TAG, "RxJava thrown a SocketException: $ex")
            } else if (ex is InterruptedException) {
                Log.i(TAG, "RxJava thrown an InterruptedException: $ex")
            }

            // These exceptions are crash worthy
            if (ex is NullPointerException || ex is IllegalStateException || ex is IllegalArgumentException) {
                Log.i(TAG, "RxJava thrown a crash worthy exception: $ex")
                throw ex
            }
        }
    }
}