package cz.budikpet.bachelorwork.di

import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.main.MultidayViewFragment
import dagger.Component
import javax.inject.Singleton

/**
 * Component connects modules and annotations.
 */
@Singleton
@Component(modules = [AppModule::class])
internal interface AppComponent {
    fun inject(mainViewModel: MainViewModel)
    fun inject(repository: Repository)
    fun inject(ctuLoginActivity: CTULoginActivity)
    fun inject(mainActivity: MainActivity)
    fun inject(multidayViewFragment: MultidayViewFragment)
}