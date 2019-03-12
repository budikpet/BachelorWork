package cz.budikpet.bachelorwork.di

import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.mvp.main.MainActivityModel
import cz.budikpet.bachelorwork.mvp.main.MainActivityPresenter
import dagger.Component

/**
 * Component connects modules and annotations.
 */
@Component(modules = [AppModule::class])
internal interface AppComponent {
    fun inject(mainActivityPresenter: MainActivityPresenter)
    fun inject(mainActivityModel: MainActivityModel)
    fun inject(ctuLoginActivity: CTULoginActivity)
}