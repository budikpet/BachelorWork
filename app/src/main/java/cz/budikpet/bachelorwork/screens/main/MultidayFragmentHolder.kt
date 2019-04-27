package cz.budikpet.bachelorwork.screens.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import cz.budikpet.bachelorwork.R
import kotlinx.android.synthetic.main.fragment_holder_multiday.*
import kotlinx.android.synthetic.main.fragment_holder_multiday.view.*
import org.joda.time.DateTime

class MultidayFragmentHolder : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val PREFILLED_WEEKS = 151

    private lateinit var viewPager: ViewPager
    private lateinit var progressBar: ProgressBar
    private var isGoToTodayVisible = false

    private val todayDate = DateTime().withTimeAtStartOfDay()
    private var currentDate = todayDate

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun subscribeObservers() {

        // Observer created only once
        viewModel.state.observe(this, Observer { state ->
            if (state != null) {
                Log.i(TAG, "State changed")
                // TODO: Changes to other parts of the UI like ToolBar

                if(state.events.isEmpty()) {
                    viewModel.loadEventsFromCalendar(state.username)
                }
            }
        })

        viewModel.calendarsUpdating.observe(this, Observer { updating ->
            val username = viewModel.state.value?.username

            if (updating != null && username != null) {
                if (!updating) {
                    // Update done
                    progressBar.visibility = View.GONE
                    viewModel.loadEventsFromCalendar(username)
                } else {
                    // Update started
                    progressBar.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupFragment() {
        val multidayAdapter = ViewPagerAdapter(activity!!.supportFragmentManager, 7, PREFILLED_WEEKS)

        viewPager!!.apply {
            adapter = multidayAdapter
            currentItem = PREFILLED_WEEKS / 2

            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    Log.i(TAG, "Selected page: $position")

//                    currentWeekTS = weekTSs[position]
//                    val shouldGoToTodayBeVisible = shouldGoToTodayBeVisible()
//                    if (isGoToTodayVisible != shouldGoToTodayBeVisible) {
//                        (activity as? MainActivity)?.toggleGoToTodayVisibility(shouldGoToTodayBeVisible)
//                        isGoToTodayVisible = shouldGoToTodayBeVisible
//                    }
//
//                    setupWeeklyActionbarTitle(weekTSs[position])
                }
            })
        }
//        updateActionBarTitle()
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