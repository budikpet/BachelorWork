package cz.budikpet.bachelorwork.di

import android.content.Context
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.util.AppAuthManager
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
}