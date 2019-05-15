package cz.budikpet.bachelorwork.di

import android.arch.lifecycle.ViewModel
import cz.budikpet.bachelorwork.di.util.ViewModelKey
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap


@Module
abstract class MainViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(MainViewModel::class)
    abstract fun bindMyViewModel(myViewModel: MainViewModel): ViewModel
}