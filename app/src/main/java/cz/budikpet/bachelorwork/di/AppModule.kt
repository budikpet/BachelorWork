package cz.budikpet.bachelorwork.di

import android.content.Context
import android.content.SharedPreferences
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Provides @Context through dependency injection.
 */
@Module
internal class AppModule(private val context: Context) {

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
    fun providesSharedPreferences(): SharedPreferences {
        // TODO: Should name be equal to username?
        return context.getSharedPreferences("Pref", Context.MODE_PRIVATE)
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
}