package cz.budikpet.bachelorwork.screens.freeTimeView


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.view.*
import android.widget.Button
import cz.budikpet.bachelorwork.MyApplication

import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.multidayView.MultidayViewFragment
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import javax.inject.Inject

class FreeTimeFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val dateStringFormat = "< %s - %s >"

    private var selectedWeekStart: DateTime = DateTime().withDayOfWeek(DateTimeConstants.MONDAY)
    private lateinit var selectedStartTime: LocalTime
    private lateinit var selectedEndTime: LocalTime

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewModel: MainViewModel

    private var supportActionBar: ActionBar? = null
    private lateinit var buttonWeek: Button
    private lateinit var buttonTimeFrom: Button
    private lateinit var buttonTimeTo: Button

    private val multidayFreeTimeViewFragment: MultidayViewFragment by lazy {
        val selectedDate = this.selectedWeekStart ?: DateTime()

        MultidayViewFragment.newInstance(MultidayViewFragment.MAX_COLUMNS, selectedDate, usesCustomEvents = true)
    }

    private val weekPickerDialog: DatePickerDialog by lazy {
        val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, day ->
            val date = LocalDate(year, month + 1, day)
            setSelectedDate(date)
        }
        val date = LocalDate()
        DatePickerDialog(context!!, listener, date.year, date.monthOfYear - 1, date.dayOfMonth)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        initSelectedTimes()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val layout = inflater.inflate(R.layout.fragment_free_time, container, false)
        setHasOptionsMenu(true)

        initButtons(layout)

        return layout
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.free_time_bar, menu)
        super.onCreateOptionsMenu(menu, inflater)

        supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(R.string.sidebar_FindFreeTime)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            android.R.id.home -> hideFreeTime()
            R.id.itemRun -> showFreeTime()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun initSelectedTimes() {
        val lessonLength = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 45)
        val breakLength = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 15)
        val numOfLessons = sharedPreferences.getInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 5)
        val lessonStartMillis = sharedPreferences.getInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), 0)


        selectedStartTime = LocalTime().withMillisOfDay(lessonStartMillis)
        selectedEndTime = selectedStartTime.plusMinutes(numOfLessons * (lessonLength + breakLength) - breakLength)
    }

    private fun initButtons(layout: View) {
        this.buttonWeek = layout.findViewById<Button>(R.id.buttonWeek)
        this.buttonTimeFrom = layout.findViewById<Button>(R.id.buttonTimeFrom)
        this.buttonTimeTo = layout.findViewById<Button>(R.id.buttonTimeTo)


        buttonWeek.setOnClickListener {
            weekPickerDialog.show()
        }

        buttonTimeFrom.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                setStartTime(LocalTime(hourOfDay, minute))
            }
            TimePickerDialog(context!!, listener, selectedStartTime.hourOfDay, selectedStartTime.minuteOfHour, true).show()
        }

        buttonTimeTo.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                setEndTime(LocalTime(hourOfDay, minute))
            }
            TimePickerDialog(context!!, listener, selectedEndTime.hourOfDay, selectedEndTime.minuteOfHour, true).show()
        }

        setSelectedDate(LocalDate())
        setStartTime(selectedStartTime)
        setEndTime(selectedEndTime)
    }

    private fun setStartTime(time: LocalTime) {
        selectedStartTime = time
        buttonTimeFrom.text = selectedStartTime.toString("HH:mm")
    }

    private fun setEndTime(time: LocalTime) {
        selectedEndTime = time
        buttonTimeTo.text = selectedEndTime.toString("HH:mm")
    }

    private fun setSelectedDate(date: LocalDate) {
        val dateStart = date.withDayOfWeek(DateTimeConstants.MONDAY)
        val dateEnd = dateStart.plusDays(MultidayViewFragment.MAX_COLUMNS - 1)
        buttonWeek.text = dateStringFormat.format(dateStart.toString("dd.MM.YYYY"), dateEnd.toString("dd.MM.YYYY"))

        this.selectedWeekStart = DateTime().withDate(dateStart).withTimeAtStartOfDay()
    }

    private fun showFreeTime() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun hideFreeTime() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
}
