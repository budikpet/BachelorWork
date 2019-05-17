package cz.budikpet.bachelorwork.screens.main.di

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceManager
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.api.SiriusAuthApiService
import cz.budikpet.bachelorwork.di.AppModule
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import dagger.Module
import dagger.Provides
import io.reactivex.schedulers.Schedulers
import javax.inject.Singleton

/**
 * Provides @Context through dependency injection.
 */
@Module
internal class TestAppModule(private val context: Context) {

    @Provides
    fun provideContext(): Context {
        return context
    }

    @Provides
    @Singleton
    fun providesSiriusApiService(): SiriusApiService {
        return SiriusApiService.create()
    }

    @Provides
    @Singleton
    fun providesSiriusAuthApiService(): SiriusAuthApiService {
        return SiriusAuthApiService.create()
    }

    @Provides
    @Singleton
    fun providesSharedPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun providesGoogleAccountCredential(): GoogleAccountCredential {
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
    fun providesScheduler(): BaseSchedulerProvider {
        return object: BaseSchedulerProvider {
            override fun io() = Schedulers.trampoline()

            override fun computation() = Schedulers.trampoline()

            override fun ui() = Schedulers.trampoline()

        }
    }
}