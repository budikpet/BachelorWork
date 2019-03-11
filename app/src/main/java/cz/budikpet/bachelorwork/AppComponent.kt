package cz.budikpet.bachelorwork

import dagger.Component

@Component(modules = [ContextModule::class])
internal interface AppComponent {
    fun inject(mainActivityPresenter: MainActivityPresenter)
    fun inject(mainActivityModel: MainActivityModel)
}