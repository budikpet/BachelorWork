package cz.budikpet.bachelorwork.screens.main.di

import cz.budikpet.bachelorwork.di.AppComponent
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.main.MainViewModelTest
import dagger.Component
import javax.inject.Singleton

/**
 * Component connects modules and annotations.
 */
@Singleton
@Component(modules = [TestAppModule::class])
internal interface TestAppComponent {

}