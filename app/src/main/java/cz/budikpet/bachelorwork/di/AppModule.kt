package cz.budikpet.bachelorwork.di

import android.content.Context
import cz.budikpet.bachelorwork.api.SiriusApiService
import dagger.Module
import dagger.Provides

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
        // TODO: Move the creation logic here entirely?
        return SiriusApiService.create()
    }
}