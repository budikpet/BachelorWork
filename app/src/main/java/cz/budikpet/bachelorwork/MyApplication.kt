package cz.budikpet.bachelorwork

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.support.multidex.MultiDexApplication
import android.util.Log
import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.di.DaggerAppComponent
import io.reactivex.plugins.RxJavaPlugins
import org.joda.time.LocalTime
import java.io.IOException
import java.net.SocketException
import android.support.multidex.MultiDex
import android.support.v7.app.AppCompatDelegate


class MyApplication : MultiDexApplication() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        const val CALENDARS_NAME = "CTUTimetable"
        const val NUM_OF_WEEKS_TO_UPDATE = 4
        val sdkVersion = Build.VERSION.SDK_INT
        val version23 = Build.VERSION_CODES.M

        internal lateinit var appComponent: AppComponent

        fun calendarNameFromId(id: String): String {
            return "${id}_${CALENDARS_NAME}"
        }

        fun idFromCalendarName(calendarName: String): String {
            return calendarName.substringBefore("_")
        }

        fun getLastLesson(startTime: LocalTime, numOfLessons: Int, lessonLength: Int, breakLength: Int): LocalTime {
            return startTime.plusMinutes(numOfLessons * (lessonLength + breakLength) - breakLength)
        }

        /**
         * Performs API version check.
         * @return color integer
         */
        fun getColor(resources: Resources, colorId: Int): Int {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    resources.getColor(colorId, null)
                }
                else -> {
                    resources.getColor(colorId)
                }
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        if(sdkVersion < version23) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        }

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