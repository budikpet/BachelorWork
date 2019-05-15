package cz.budikpet.bachelorwork.di.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import javax.inject.Inject

class MyViewModelFactory @Inject constructor(
    private val repository: Repository,
    private val baseSchedulerProvider: BaseSchedulerProvider
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return if (modelClass.isAssignableFrom(MainViewModel::class.java!!)) {
            MainViewModel(this.repository, baseSchedulerProvider) as T
        } else {
            throw IllegalArgumentException("ViewModel Not Found")
        }
    }

}