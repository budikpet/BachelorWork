package cz.budikpet.bachelorwork.di

import android.arch.lifecycle.ViewModelProvider
import cz.budikpet.bachelorwork.di.util.DaggerViewModelFactory
import dagger.Binds
import dagger.Module

@Module
abstract class ViewModelFactoryModule {
    @Binds
    abstract fun bindViewModelFactory(viewModelFactory: DaggerViewModelFactory): ViewModelProvider.Factory
}