package cz.budikpet.bachelorwork.screens.main.mainViewModel

import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.screens.main.util.mock
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import io.reactivex.Observable
import org.junit.Before
import org.junit.Test


internal class MainViewModelTest_SearchSirius : BaseMainViewModelTest() {

    val testObserver = mock<Observer<List<SearchItem>>>()

    val calendarName = MyApplication.calendarNameFromId(username)
    val searchItems = arrayListOf(
        SearchItem(type = ItemType.PERSON, id = "budikpet"),
        SearchItem(type = ItemType.ROOM, id = "budik"),
        SearchItem(type = ItemType.COURSE, id = "budisl")
    )

    @Before
    override fun initTest() {
        super.initTest()
        reset(testObserver)
    }

    @Test
    fun searchSirius_simple() {
        // Data
        val query = "budi"

        // Stubs
        whenever(repository.searchSirius(query))
            .thenReturn(Observable.fromIterable(searchItems))

        viewModel.searchItems.observeForever(testObserver)
        viewModel.searchSirius(query)

        // Asserts
        assert(viewModel.searchItems.value != null)
        assert(viewModel.searchItems.value!! == searchItems)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun searchSirius_withItemType() {
        // Data
        val query = "budi"

        val type = ItemType.PERSON
        val result = searchItems.filter { it.type == type }

        // Stubs
        whenever(repository.searchSirius(query))
            .thenReturn(Observable.fromIterable(searchItems))

        viewModel.searchItems.observeForever(testObserver)
        viewModel.searchSirius(query, type)

        // Asserts
        assert(viewModel.searchItems.value != null)
        assert(viewModel.searchItems.value!! == result)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }

    @Test
    fun searchSirius_error() {
        // Data
        val query = "budi"

        // Stubs
        whenever(repository.searchSirius(query))
            .thenReturn(Observable.error(NoInternetConnectionException()))

        viewModel.searchItems.observeForever(testObserver)
        viewModel.searchSirius(query)

        // Asserts
        assert(viewModel.searchItems.value == null)
        assert(viewModel.thrownException.value == null)

        assert(viewModel.operationsRunning.value != null)
        assert(viewModel.operationsRunning.value!! == 0)
    }
}