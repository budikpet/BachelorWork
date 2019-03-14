package cz.budikpet.bachelorwork.di

import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.mvp.main.MainActivityViewModel
import cz.budikpet.bachelorwork.util.SiriusApiClient
import dagger.Component
import javax.inject.Singleton

/**
 * Component connects modules and annotations.
 */
@Singleton
@Component(modules = [AppModule::class])
internal interface AppComponent {
    fun inject(mainActivityViewModel: MainActivityViewModel)
    fun inject(repository: Repository)
    fun inject(ctuLoginActivity: CTULoginActivity)
    fun inject(siriusApiClient: SiriusApiClient) {

    }
}