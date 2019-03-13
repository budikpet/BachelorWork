package cz.budikpet.bachelorwork.di

import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.mvp.main.Repository
import cz.budikpet.bachelorwork.mvp.main.MainActivityViewModel
import dagger.Component

/**
 * Component connects modules and annotations.
 */
@Component(modules = [AppModule::class])
internal interface AppComponent {
    fun inject(mainActivityViewModel: MainActivityViewModel)
    fun inject(repository: Repository)
    fun inject(ctuLoginActivity: CTULoginActivity)
}