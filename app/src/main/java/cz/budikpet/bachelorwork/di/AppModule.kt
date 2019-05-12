package cz.budikpet.bachelorwork.di

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.api.SiriusAuthApiService
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import dagger.Module
import dagger.Provides
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Singleton

/**
 * Provides @Context through dependency injection.
 */
@Module
open internal class AppModule(private val context: Context) {

    @Provides
    open fun provideContext(): Context {
        return context
    }

    @Provides
    @Singleton
    open fun providesSiriusApiService(): SiriusApiService {
        return SiriusApiService.create()
    }

    @Provides
    @Singleton
    open fun providesSiriusAuthApiService(): SiriusAuthApiService {
        return SiriusAuthApiService.create()
    }

    @Provides
    @Singleton
    open fun providesSharedPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    open fun providesGoogleAccountCredential(): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(context, setOf(CalendarScopes.CALENDAR))
        val accountName =
            providesSharedPreferences().getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), null)

        if (accountName != null) {
            credential.selectedAccountName = accountName
        }

        return credential
    }

    @Provides
    @Singleton
    open fun providesScheduler(): BaseSchedulerProvider {
        return object: BaseSchedulerProvider {
            override fun io() = Schedulers.io()

            override fun computation() = Schedulers.computation()

            override fun ui() = AndroidSchedulers.mainThread()

        }
    }
}