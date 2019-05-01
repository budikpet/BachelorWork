package cz.budikpet.bachelorwork.screens.main

import android.app.DatePickerDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import android.widget.Toast
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.util.GoogleAccountNotFoundException
import kotlinx.android.synthetic.main.fragment_holder_multiday.view.*
import org.joda.time.DateTime
import org.joda.time.Interval
import retrofit2.HttpException

class MultidayFragmentHolder : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val PREFILLED_WEEKS = 151

    private lateinit var viewPager: ViewPager
    private lateinit var progressBar: ProgressBar
    private var itemGoToToday: MenuItem? = null

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

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        itemGoToToday = menu?.findItem(R.id.itemGoToToday)

        val adapter = viewPager.adapter as ViewPagerAdapter

        if (adapter != null)
            updateAppBar(adapter.dateFromPosition(pagerPosition))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_holder_multiday, container, false) as ConstraintLayout
        viewPager = layout.viewPager
        progressBar = layout.progressBar
        setupViewPager()
        return layout
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.itemGoToToday) {
            // Move the viewPager to today
            resetViewPager()
        } else if (item?.itemId == R.id.itemGoToDate) {
            datePickerDialog.show()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun subscribeObservers() {

        viewModel.operationRunning.observe(this, Observer { updating ->
            Log.i(TAG, "Calendars updating: $updating")

            if (updating != null) {
                if (!updating) {
                    // Update done
                    progressBar.visibility = View.GONE
                } else {
                    // Update started
                    progressBar.visibility = View.VISIBLE
                }
            }
        })

        viewModel.thrownException.observe(this, Observer {
            if (it != null) {
                handleException(it)
            }
        })
    }

    private fun resetViewPager(date: DateTime = DateTime()) {
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

            Log.d(
                TAG,
                "Is ${currDate.toString("dd.MM")} in loaded events: ${viewModel.loadedEventsInterval.start.toString("dd.MM")} - ${viewModel.loadedEventsInterval.end.toString(
                    "dd.MM"
                )}"
            )
            Log.d(
                TAG,
                "UpdatedEvents: ${viewModel.updatedEventsInterval?.start?.toString("dd.MM")} - ${viewModel.updatedEventsInterval?.end?.toString(
                    "dd.MM"
                )}"
            )

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

        // Update title
        val lastDate = currDate.plusDays(viewModel.daysPerMultidayViewFragment)
        val title = when {
            currDate.monthOfYear == lastDate.monthOfYear -> currDate.monthOfYear().getAsText(null).capitalize()
            else -> "${currDate.monthOfYear().getAsText(null).capitalize()} - " +
                    lastDate.monthOfYear().getAsText(null).capitalize()
        }

        activity?.title = title
    }

    private fun handleException(exception: Throwable) {
        // TODO: Implement
        var text = "Unknown exception occurred."

        if (exception is GoogleAccountNotFoundException) {
            // Prompt the user to select a new google account
            Log.e(TAG, "Used google account not found.")
        } else if (exception is HttpException) {
            Log.e(TAG, "Retrofit 2 HTTP ${exception.code()} exception: ${exception.response()}")
            if (exception.code() == 500) {
                text = "CTU internal server error occured. Please try again."
            }
        } else {
            Log.e(TAG, "Unknown exception occurred: $exception")
        }

        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}