package cz.budikpet.bachelorwork.screens.main.di

import dagger.Component
import javax.inject.Singleton

/**
 * Component connects modules and annotations.
 */
@Singleton
@Component(modules = [TestAppModule::class])
internal interface TestAppComponent {

}