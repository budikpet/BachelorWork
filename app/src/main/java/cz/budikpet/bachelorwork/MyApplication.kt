package cz.budikpet.bachelorwork

import android.app.Application
import android.util.Log
import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.di.DaggerAppComponent
import io.reactivex.plugins.RxJavaPlugins
import org.joda.time.LocalTime
import java.io.IOException
import java.net.SocketException


class MyApplication : Application() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        const val CALENDARS_NAME = "CTUTimetable"
        const val NUM_OF_WEEKS_TO_UPDATE = 4

        internal lateinit var appComponent: AppComponent

        /**
         * @param calendarName is a CTU (Sirius API) username.
         * @return name of the calendar from Google Calendar.
         */
        fun calendarNameFromId(id: String): String {
            return "${id}_${CALENDARS_NAME}"
        }

        /**
         * @param calendarName is aname of the calendar from Google Calendar.
         * @return CTU (Sirius API) username
         */
        fun idFromCalendarName(calendarName: String): String {
            return calendarName.substringBefore("_")
        }

        fun getLastLesson(startTime: LocalTime, numOfLessons: Int, lessonLength: Int, breakLength: Int):LocalTime {
            return startTime.plusMinutes(numOfLessons * (lessonLength + breakLength) - breakLength)
        }
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