package cz.budikpet.bachelorwork.screens.multidayView

import android.app.DatePickerDialog
import android.app.SearchManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.ActionBar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.util.Log
import android.view.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
import kotlinx.android.synthetic.main.dialog_share_timetable.view.*
import org.joda.time.DateTime
import org.joda.time.Interval

class MultidayFragmentHolder : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val PREFILLED_WEEKS = 151

    private lateinit var viewPager: ViewPager
    private lateinit var supportActionBar: ActionBar

    private var offlineAvailableMenuItem: MenuItem? = null
    private var itemGoToToday: MenuItem? = null
    private var searchMenuItem: MenuItem? = null
    private var shareMenuItem: MenuItem? = null

    private var pagerPosition = PREFILLED_WEEKS / 2

    private lateinit var viewModel: MainViewModel

    private val addCalendarSnackbar: Snackbar by lazy {
        val mainActivityLayout = activity?.findViewById<ConstraintLayout>(R.id.main_activity)
        val snackbar = Snackbar
            .make(
                mainActivityLayout!!, getString(R.string.snackbar_CalendarUnavailable),
                Snackbar.LENGTH_LONG
            )
            .setAction(getString(R.string.snackbar_Undo)) {
                // Change the menuItem back
                updateOfflineAvailableItem(viewModel.isSelectedCalendarAvailableOffline())
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        // Undo button was not pressed, make timetable available/unavailable offline
                        val calendarName = MyApplication.calendarNameFromId(viewModel.timetableOwner.value!!.first)
                        if (viewModel.isSelectedCalendarAvailableOffline()) {
                            // Available offline, remove
                            viewModel.removeCalendar(calendarName)
                        } else {
                            // Unavailable offline, add
                            viewModel.addCalendar(calendarName)
                        }
                    }
                }

                override fun onShown(snackbar: Snackbar) {}
            })

        return@lazy snackbar
    }

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

    private val shareTimetableDialogBuilder: AlertDialog.Builder by lazy {
        val shareDialogView = layoutInflater.inflate(R.layout.dialog_share_timetable, null)
        val emailEditText = shareDialogView.emailEditText

        AlertDialog.Builder(context!!)
            .setView(shareDialogView)
            .setPositiveButton(getString(R.string.alertDialog_share)) { dialog, id ->
                viewModel.sharePersonalTimetable(emailEditText.text.toString())
            }
            .setNegativeButton(getString(R.string.alertDialog_quit)) { _, _ -> }
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

        offlineAvailableMenuItem = menu?.findItem(R.id.itemOfflineAvailable)
        shareMenuItem = menu?.findItem(R.id.itemSharePersonalTimetable)

        searchMenuItem = menu?.findItem(R.id.itemSearch)!!
        val searchView = searchMenuItem?.actionView as SearchView
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
        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                // User entered the searchView
                if (!viewModel.checkInternetConnection()) {
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
            searchMenuItem?.expandActionView()
            searchView.setQuery(query, false)
        }

        itemGoToToday = menu.findItem(R.id.itemGoToToday)

        val adapter = viewPager.adapter as ViewPagerAdapter?
        if (adapter != null)
            updateAppBar(adapter.dateFromPosition(pagerPosition))
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.itemGoToToday -> resetViewPager()
            R.id.itemGoToDate -> datePickerDialog.show()
            R.id.itemOfflineAvailable -> offlineAvailableMenuItemClicked()
            R.id.itemSharePersonalTimetable -> shareTimetableDialogBuilder.show()
            android.R.id.home -> {
                // Go back to the users' timetable
                viewModel.timetableOwner.postValue(Pair(viewModel.ctuUsername, ItemType.PERSON))
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun offlineAvailableMenuItemClicked() {
        addCalendarSnackbar.apply {
            when {
                viewModel.isSelectedCalendarAvailableOffline() -> {
                    // Available offline, make unavailable
                    setText(R.string.snackbar_CalendarUnavailable)
                    updateOfflineAvailableItem(false)
                }
                else -> {
                    setText(R.string.snackbar_CalendarAvailable)
                    updateOfflineAvailableItem(true)
                }
            }

            show()
        }
    }

    private fun subscribeObservers() {

        viewModel.timetableOwner.observe(this, Observer {
            updateAppBar(viewModel.currentlySelectedDate)
        })

        viewModel.searchItems.observe(this, Observer { searchItemsList ->
            if (searchItemsList != null && searchItemsList.isEmpty() && viewModel.lastSearchQuery == "") {
                // Deactivate searchView
                searchMenuItem?.collapseActionView()
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
            shareMenuItem?.isVisible = currUsername == viewModel.ctuUsername
            updateOfflineAvailableItem(viewModel.isSelectedCalendarAvailableOffline())
        }
    }

    private fun updateOfflineAvailableItem(available: Boolean) {
        val currUsername = viewModel.timetableOwner.value?.first

        offlineAvailableMenuItem?.apply {
            isVisible = currUsername != viewModel.ctuUsername

            if (available) {
                // Available offline
                title = getString(R.string.menuItem_CalendarOfflineAvailable)
                icon = ContextCompat.getDrawable(context!!, R.drawable.ic_offline_available_black_24dp)
            } else {
                title = getString(R.string.menuItem_CalendarOfflineUnavailable)
                icon = ContextCompat.getDrawable(context!!, R.drawable.ic_offline_unavailable_black_24dp)
            }
        }


    }
}