package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.*
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import cz.budikpet.bachelorwork.util.schedulers.BaseSchedulerProvider
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.MockitoAnnotations


open class BaseMainViewModelTest {
    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @InjectMocks
    val repository = mock<Repository>()

    val viewModel by lazy {
        val schedulersProvider = object : BaseSchedulerProvider {
            override fun io() = Schedulers.trampoline()

            override fun computation() = Schedulers.trampoline()

            override fun ui() = Schedulers.trampoline()
        }

        MainViewModel(repository, schedulersProvider)
    }

    val username = "budikpet"

    @Before
    open fun initTest() {
        MockitoAnnotations.initMocks(this);
        viewModel.timetableOwner.value = Pair(username, ItemType.PERSON)
        viewModel.operationsRunning.value = 0
    }

    @After
    fun clear() {
//        assert(viewModel.compositeDisposable.size() > 0)
        viewModel.onDestroy()
        assert(viewModel.compositeDisposable.size() <= 0)
    }

}