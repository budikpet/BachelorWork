package cz.budikpet.bachelorwork.screens.main

import android.app.DatePickerDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.util.GoogleAccountNotFoundException
import kotlinx.android.synthetic.main.fragment_holder_multiday.view.*
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import retrofit2.HttpException

class MultidayFragmentHolder : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val PREFILLED_WEEKS = 151

    private lateinit var viewPager: ViewPager
    private lateinit var progressBar: ProgressBar

    private var daysPerFragment = 7 // TODO: Remove
    private var pagerPosition = PREFILLED_WEEKS / 2
    private var firstDate = DateTime().withTimeAtStartOfDay()

    private lateinit var viewModel: MainViewModel

    private val datePickerDialog: DatePickerDialog by lazy {
        val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, dayOfMonth ->
            val date = DateTime(year, month + 1, dayOfMonth, 0, 0)
            resetViewPager(date)
        }

        DatePickerDialog(context, listener, firstDate.year,firstDate.monthOfYear - 1,firstDate.dayOfMonth)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_holder_multiday, container, false) as ConstraintLayout
        viewPager = layout.viewPager
        progressBar = layout.progressBar
        setupFragment()
        return layout
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.itemGoToToday) {
            // Move the viewPager to today
            resetViewPager()
        } else if(item?.itemId == R.id.itemGoToDate) {
            // TODO: DatePicker
            datePickerDialog.show()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun resetViewPager(date: DateTime = DateTime()) {
        pagerPosition = PREFILLED_WEEKS / 2

        firstDate = date
        if(daysPerFragment == MultidayViewFragment.MAX_COLUMNS)
            firstDate = firstDate.withTimeAtStartOfDay().withDayOfWeek(DateTimeConstants.MONDAY)

        viewPager.adapter = ViewPagerAdapter(activity!!.supportFragmentManager, daysPerFragment, PREFILLED_WEEKS, firstDate)
        viewPager.setCurrentItem(pagerPosition, false)
    }

    private fun subscribeObservers() {

        // Observer created only once
        viewModel.timetableOwner.observe(this, Observer { pair ->
            if (pair != null) {
                val username = pair.first
                val itemType = pair.second

                // New pair was loaded
                viewModel.loadEvents(username, itemType)
            }
        })

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

    private fun setupFragment() {
        if (daysPerFragment == MultidayViewFragment.MAX_COLUMNS)
            firstDate = firstDate.withDayOfWeek(DateTimeConstants.MONDAY)

        viewPager.apply {
            adapter = ViewPagerAdapter(activity!!.supportFragmentManager, daysPerFragment, PREFILLED_WEEKS, firstDate)
            currentItem = pagerPosition

            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(currPosition: Int) {
                    changeTitle(currPosition)
                }
            })
        }

        changeTitle(pagerPosition)
    }

    /**
     * Changes title according to the current position in the view pager.
     */
    private fun changeTitle(currPosition: Int) {
        val currDate = when {
            currPosition > pagerPosition -> firstDate.plusDays((currPosition - pagerPosition) * daysPerFragment)
            else -> firstDate.minusDays((pagerPosition - currPosition) * daysPerFragment)
        }


        val lastDate = currDate.plusDays(daysPerFragment)
        val title = when {
            currDate.monthOfYear == lastDate.monthOfYear -> currDate.monthOfYear().getAsText(null).capitalize()
            else -> "${currDate.monthOfYear().getAsText(null).capitalize()} - " +
                    lastDate.monthOfYear().getAsText(null).capitalize()
        }

        viewModel.title.postValue(title)
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
            Log.e(TAG, "Unknown exception occurred in update: $exception")
        }

        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

//    private fun setupWeeklyActionbarTitle(timestamp: Long) {
//        val startDateTime = Formatter.getDateTimeFromTS(timestamp)
//        val endDateTime = Formatter.getDateTimeFromTS(timestamp + WEEK_SECONDS)
//        val startMonthName = Formatter.getMonthName(context!!, startDateTime.monthOfYear)
//        if (startDateTime.monthOfYear == endDateTime.monthOfYear) {
//            var newTitle = startMonthName
//            if (startDateTime.year != DateTime().year) {
//                newTitle += " - ${startDateTime.year}"
//            }
//            (activity as MainActivity).updateActionBarTitle(newTitle)
//        } else {
//            val endMonthName = Formatter.getMonthName(context!!, endDateTime.monthOfYear)
//            (activity as MainActivity).updateActionBarTitle("$startMonthName - $endMonthName")
//        }
//        (activity as MainActivity).updateActionBarSubtitle("${getString(R.string.week)} ${startDateTime.plusDays(3).weekOfWeekyear}")
//    }

//    override fun goToToday() {
//        currentWeekTS = thisWeekTS
//        setupFragment()
//    }
//
//    override fun showGoToDateDialog() {
//        activity!!.setTheme(context!!.getDialogTheme())
//        val view = layoutInflater.inflate(R.layout.date_picker, null)
//        val datePicker = view.findViewById<DatePicker>(R.id.date_picker)
//
//        val dateTime = Formatter.getDateTimeFromTS(currentWeekTS)
//        datePicker.init(dateTime.year, dateTime.monthOfYear - 1, dateTime.dayOfMonth, null)
//
//        AlertDialog.Builder(context!!)
//            .setNegativeButton(R.string.cancel, null)
//            .setPositiveButton(R.string.ok) { dialog, which -> dateSelected(dateTime, datePicker) }
//            .create().apply {
//                activity?.setupDialogStuff(view, this)
//            }
//    }

//    private fun dateSelected(dateTime: DateTime, datePicker: DatePicker) {
//        val isSundayFirst = context!!.config.isSundayFirst
//        val month = datePicker.month + 1
//        val year = datePicker.year
//        val day = datePicker.dayOfMonth
//        var newDateTime = dateTime.withDate(year, month, day)
//
//        if (isSundayFirst) {
//            newDateTime = newDateTime.plusDays(1)
//        }
//
//        var selectedWeek = newDateTime.withDayOfWeek(1).withTimeAtStartOfDay().minusDays(if (isSundayFirst) 1 else 0)
//        if (newDateTime.minusDays(7).seconds() > selectedWeek.seconds()) {
//            selectedWeek = selectedWeek.plusDays(7)
//        }
//
//        currentWeekTS = selectedWeek.seconds()
//        setupFragment()
//    }

//    override fun shouldGoToTodayBeVisible() = currentWeekTS != thisWeekTS
//
//    override fun updateActionBarTitle() {
//        setupWeeklyActionbarTitle(currentWeekTS)
//    }
}