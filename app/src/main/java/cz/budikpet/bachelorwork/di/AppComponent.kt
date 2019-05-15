package cz.budikpet.bachelorwork.di

import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.screens.calendarListView.CalendarsListFragment
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.screens.emailListView.EmailListFragment
import cz.budikpet.bachelorwork.screens.eventEditView.EventEditFragment
import cz.budikpet.bachelorwork.screens.eventView.EventViewFragment
import cz.budikpet.bachelorwork.screens.freeTimeView.FreeTimeFragment
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.multidayView.MultidayFragmentHolder
import cz.budikpet.bachelorwork.screens.multidayView.MultidayViewFragment
import cz.budikpet.bachelorwork.screens.settings.SettingsFragment
import dagger.Component
import javax.inject.Singleton

/**
 * Component connects modules and annotations.
 */
@Singleton
@Component(modules = [AppModule::class, ViewModelFactoryModule::class, MainViewModelModule::class])
internal interface AppComponent {
    fun inject(mainViewModel: MainViewModel)
    fun inject(repository: Repository)
    fun inject(ctuLoginActivity: CTULoginActivity)
    fun inject(mainActivity: MainActivity)
    fun inject(multidayViewFragment: MultidayViewFragment)
    fun inject(freeTimeFragment: FreeTimeFragment)
    fun inject(settingsFragment: SettingsFragment)
    fun inject(multidayFragmentHolder: MultidayFragmentHolder)
    fun inject(eventViewFragment: EventViewFragment)
    fun inject(eventEditFragment: EventEditFragment)
    fun inject(emailListFragment: EmailListFragment)
    fun inject(calendarsListFragment: CalendarsListFragment)
}