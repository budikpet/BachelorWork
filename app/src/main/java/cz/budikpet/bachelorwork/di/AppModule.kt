package cz.budikpet.bachelorwork.di

import android.content.Context
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.SiriusApiClient
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Provides @Context through dependency injection.
 */
@Module
internal class AppModule(val context: Context) {

    @Provides
    fun provideContext(): Context {
        return context
    }

    @Provides
    fun providesSiriusApiService(): SiriusApiService {
        return SiriusApiService.create()
    }

    @Provides
    @Singleton
    fun providesAppAuthManager(): AppAuthManager {
        return AppAuthManager(context)
    }

    @Provides
    @Singleton
    fun providesSiriusApiClient(): SiriusApiClient {
        return SiriusApiClient()
    }
}