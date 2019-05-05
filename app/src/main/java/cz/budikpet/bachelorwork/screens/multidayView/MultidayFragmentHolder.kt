package cz.budikpet.bachelorwork.screens.multidayView

import android.app.DatePickerDialog
import android.app.SearchManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import kotlinx.android.synthetic.main.fragment_holder_multiday.*
import kotlinx.android.synthetic.main.fragment_holder_multiday.view.*
import org.joda.time.DateTime
import org.joda.time.Interval

class MultidayFragmentHolder : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val PREFILLED_WEEKS = 151

    private lateinit var viewPager: ViewPager
    private lateinit var supportActionBar: ActionBar

    private var itemGoToToday: MenuItem? = null
    private lateinit var searchMenuItem: MenuItem

    private var pagerPosition = PREFILLED_WEEKS / 2

    private lateinit var viewModel: MainViewModel

    private val datePickerDialog: DatePickerDialog by lazy {
        val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, dayOfMonth ->
            val date = DateTime(year, month + 1, dayOfMonth, 0, 0)
            resetViewPager(date)
        }

        DatePickerDialog(
            context,
            listener,
            viewModel.currentlySelectedDate.year,
            viewModel.currentlySelectedDate.monthOfYear - 1,
            viewModel.currentlySelectedDate.dayOfMonth
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        // Receive MainViewModel reference in a fragment
        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        supportActionBar = (activity as AppCompatActivity).supportActionBar!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewPager = inflater.inflate(R.layout.fragment_holder_multiday, container, false) as ViewPager
        setupViewPager()
        return viewPager
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.multiday_view_bar, menu)
        super.onCreateOptionsMenu(menu, inflater)

        searchMenuItem = menu?.findItem(R.id.itemSearch)!!
        val searchView = searchMenuItem.actionView as SearchView
        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        searchView.isSubmitButtonEnabled = true

        // Watch for user input
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String?): Boolean {
                if (query != null) {
                    viewModel.lastSearchQuery = query

                    if (query.count() >= 1) {
                        viewModel.searchSirius(query)
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.i(TAG, "Submitted: <$query>")
                viewModel.lastSearchQuery = ""
                return true
            }

        })

        // Watch when the user closes the searchView
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                // User entered the searchView
                if(!viewModel.checkInternetConnection()) {
                    viewModel.thrownException.postValue(NoInternetConnectionException())
                }

                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                // User left the searchView
                viewModel.searchItems.postValue(listOf())
                viewModel.lastSearchQuery = ""
                return true
            }

        })

        // Restore the search view after configuration changes
        val query = viewModel.lastSearchQuery
        if (query.count() > 0) {
            // There are searchItems now
            searchMenuItem.expandActionView()
            searchView.setQuery(query, false)
        }

        itemGoToToday = menu.findItem(R.id.itemGoToToday)

        val adapter = viewPager.adapter as ViewPagerAdapter?
        if (adapter != null)
            updateAppBar(adapter.dateFromPosition(pagerPosition))
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when {
            item?.itemId == R.id.itemGoToToday -> resetViewPager()
            item?.itemId == R.id.itemGoToDate -> datePickerDialog.show()
            item?.itemId == android.R.id.home -> {
                // Go back to the users' timetable
                viewModel.timetableOwner.postValue(Pair(viewModel.ctuUsername, ItemType.PERSON))
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun subscribeObservers() {

        viewModel.timetableOwner.observe(this, Observer {
            updateAppBar(viewModel.currentlySelectedDate)
        })

        viewModel.searchItems.observe(this, Observer { searchItemsList ->
            if(searchItemsList != null && searchItemsList.isEmpty() && viewModel.lastSearchQuery == "") {
                // Deactivate searchView
                searchMenuItem.collapseActionView()
            }
        })
    }

    fun resetViewPager(date: DateTime = DateTime()) {
        pagerPosition = PREFILLED_WEEKS / 2

        viewModel.currentlySelectedDate = date

        val adapter = ViewPagerAdapter(
            activity!!.supportFragmentManager,
            viewModel.daysPerMultidayViewFragment,
            PREFILLED_WEEKS,
            viewModel.currentlySelectedDate
        )
        viewPager.adapter = adapter

        viewPager.setCurrentItem(pagerPosition, false)

        updateViewPager(pagerPosition)
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(
            activity!!.supportFragmentManager,
            viewModel.daysPerMultidayViewFragment,
            PREFILLED_WEEKS,
            viewModel.currentlySelectedDate
        )

        viewPager.apply {
            this.adapter = adapter
            currentItem = pagerPosition

            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(currPosition: Int) {
                    updateViewPager(currPosition)
                }
            })
        }
    }

    private fun updateViewPager(currPosition: Int) {
        val adapter = viewPager.adapter as ViewPagerAdapter?
        pagerPosition = currPosition

        if (adapter != null) {
            val currDate = adapter.dateFromPosition(currPosition)
            viewModel.currentlySelectedDate = currDate
            updateAppBar(currDate)

            if (!viewModel.loadedEventsInterval.contains(currDate)) {
                // User moved outside of loaded events
                viewModel.loadEvents(currDate)
            }
        }
    }

    private fun updateAppBar(currDate: DateTime) {
        // Update GoToToday menu item
        val interval = Interval(currDate, currDate.plusDays(viewModel.daysPerMultidayViewFragment))
        itemGoToToday?.isVisible = !interval.contains(DateTime().withTimeAtStartOfDay())

        // Update name
        val lastDate = currDate.plusDays(viewModel.daysPerMultidayViewFragment - 1)
        supportActionBar.subtitle = "${currDate.toString("dd.MM")} – ${lastDate.toString("dd.MM")}"

        // Update back button
        val currUsername = viewModel.timetableOwner.value?.first
        if (currUsername != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(currUsername != viewModel.ctuUsername)
        }
    }
}