package cz.budikpet.bachelorwork.screens.freeTimeView


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.app.Fragment
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import com.tokenautocomplete.TokenCompleteTextView
import cz.budikpet.bachelorwork.MyApplication

import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.screens.TokenCompletionView
import cz.budikpet.bachelorwork.screens.eventEditView.AutoSuggestAdapter
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.screens.multidayView.MultidayViewFragment
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.inTransaction
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import javax.inject.Inject

// TODO: Zobrazení fragmentu

class FreeTimeFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val dateStringFormat = "< %s - %s >"

    private var selectedWeekStart: DateTime = DateTime().withDayOfWeek(DateTimeConstants.MONDAY).withTimeAtStartOfDay()
    set(value) {
        field = value
        viewModel.selectedWeekStart = value
    }
    private lateinit var selectedWeekEnd: DateTime
    private var selectedStartTime: LocalTime = LocalTime()
        set(value) {
            field = value
            viewModel.selectedStartTime = value
        }
    private var selectedEndTime: LocalTime = LocalTime()
        set(value) {
            field = value
            viewModel.selectedEndTime = value
        }

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewModel: MainViewModel

    private var itemRun: MenuItem? = null
    private var supportActionBar: ActionBar? = null
    private lateinit var imageError: ImageView
    private lateinit var buttonWeek: Button
    private lateinit var buttonTimeFrom: Button
    private lateinit var buttonTimeTo: Button
    private lateinit var timetablesCompletionView: TokenCompleteTextView<SearchItem>
    private lateinit var freeTimeLayout: ConstraintLayout

    private val multidayFreeTimeViewFragment: MultidayViewFragment by lazy {
        MultidayViewFragment.newInstance(MultidayViewFragment.MAX_COLUMNS, selectedWeekStart, usesCustomEvents = true)
    }

    private val weekPickerDialog: DatePickerDialog by lazy {
        val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, day ->
            val date = LocalDate(year, month + 1, day)
            setSelectedDate(DateTime().withDate(date))
        }
        DatePickerDialog(
            context!!,
            listener,
            selectedWeekStart.year,
            selectedWeekStart.monthOfYear - 1,
            selectedWeekStart.dayOfMonth
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        if(viewModel.selectedWeekStart != null) {
            selectedWeekStart = viewModel.selectedWeekStart!!
            selectedStartTime = viewModel.selectedStartTime!!
            selectedEndTime = viewModel.selectedEndTime!!
        } else {
            initSelectedTimes()
        }

        childFragmentManager.inTransaction {
            replace(R.id.fragmentShowFreeTime, multidayFreeTimeViewFragment)
            hide(multidayFreeTimeViewFragment)
        }

        subscribeObservers()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFreeTime()
        viewModel.searchItems.postValue(arrayListOf())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val layout = inflater.inflate(R.layout.fragment_free_time, container, false)
        setHasOptionsMenu(true)

        freeTimeLayout = layout.findViewById(R.id.freeTimeLayout)

        imageError = layout.findViewById(R.id.imageError)

        initButtons(layout)

        initCompletionView(layout)

        return layout
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.free_time_bar, menu)
        super.onCreateOptionsMenu(menu, inflater)

        itemRun = menu!!.findItem(R.id.itemRun)

        supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(R.string.sidebar_FindFreeTime)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> hideFreeTime()
            R.id.itemRun -> showFreeTime()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun subscribeObservers() {
        viewModel.searchItems.observe(this, Observer { searchItemsList ->
            val adapter = (timetablesCompletionView.adapter as AutoSuggestAdapter?) ?: return@Observer
            if (searchItemsList != null) {
                adapter.setData(searchItemsList)
            }
        })

        viewModel.freeTimeEvents.observe(this, Observer { events ->
            childFragmentManager.inTransaction {
                if(events != null && events.count() > 0) {
                    // Show timetable
                    show(multidayFreeTimeViewFragment)
                    multidayFreeTimeViewFragment.firstDate = selectedWeekStart
                    multidayFreeTimeViewFragment.showCustomEvents(events)
                } else {
                    hide(multidayFreeTimeViewFragment)
                }

                return@inTransaction this
            }
        })
    }

    private fun showFreeTime() {
        hideSoftKeyboard(freeTimeLayout)
        if(timetablesCompletionView.objects.count() <= 0) {
            timetablesCompletionView.error = getString(R.string.error_fieldBlank)
            return
        }

        if (areSelectedTimesCorrect()) {
            itemRun?.setVisible(false)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            viewModel.getFreeTimeEvents(selectedWeekStart, selectedWeekEnd, selectedStartTime, selectedEndTime, viewModel.freeTimeTimetables)
        }
    }

    private fun hideFreeTime() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        viewModel.freeTimeEvents.postValue(arrayListOf())
        itemRun?.isVisible = true
        hideSoftKeyboard(freeTimeLayout)
    }

    private fun initCompletionView(layout: View) {
        timetablesCompletionView = layout.findViewById<TokenCompletionView>(R.id.timetablesTokenAuto)
        timetablesCompletionView.allowCollapse(false)

        timetablesCompletionView.setAdapter(AutoSuggestAdapter(context!!, android.R.layout.simple_list_item_1) {
            // Filter items that are already selected
            !viewModel.freeTimeTimetables.contains(it)
        })
        timetablesCompletionView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = query.toString()
                val parts = newText.split(",".toRegex())
                if (parts.isNotEmpty()) {
                    viewModel.searchSirius(parts.last())
                }
            }

        })

        timetablesCompletionView.setTokenListener(object : TokenCompleteTextView.TokenListener<SearchItem> {
            override fun onTokenIgnored(token: SearchItem?) {}

            override fun onTokenAdded(token: SearchItem?) {
                val token = token ?: return
                if (!viewModel.freeTimeTimetables.contains(token)) {
                    viewModel.freeTimeTimetables.add(token)
                }
            }

            override fun onTokenRemoved(token: SearchItem?) {
                val token = token ?: return
                viewModel.freeTimeTimetables.remove(token)
            }
        })

        // Add timetables user has already picked (change of settings)
        viewModel.freeTimeTimetables.forEach { searchItem ->
            timetablesCompletionView.addObjectAsync(searchItem)
        }
    }

    private fun initSelectedTimes() {
        val lessonLength = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 45)
        val breakLength = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 15)
        val numOfLessons = sharedPreferences.getInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 5)
        val lessonStartMillis = sharedPreferences.getInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), 0)


        selectedStartTime = LocalTime().withMillisOfDay(lessonStartMillis)
        selectedEndTime = MyApplication.getLastLesson(selectedStartTime, numOfLessons, lessonLength, breakLength)
    }

    private fun initButtons(layout: View) {
        this.buttonWeek = layout.findViewById(R.id.buttonWeek)
        this.buttonTimeFrom = layout.findViewById(R.id.buttonTimeFrom)
        this.buttonTimeTo = layout.findViewById(R.id.buttonTimeTo)


        buttonWeek.setOnClickListener {
            weekPickerDialog.show()
        }

        buttonTimeFrom.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                setStartTime(LocalTime(hourOfDay, minute))
            }
            TimePickerDialog(
                context!!,
                listener,
                selectedStartTime.hourOfDay,
                selectedStartTime.minuteOfHour,
                true
            ).show()
        }

        buttonTimeTo.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                setEndTime(LocalTime(hourOfDay, minute))
            }
            TimePickerDialog(context!!, listener, selectedEndTime.hourOfDay, selectedEndTime.minuteOfHour, true).show()
        }

        setSelectedDate(selectedWeekStart)
        setStartTime(selectedStartTime)
        setEndTime(selectedEndTime)
    }

    private fun setStartTime(time: LocalTime) {
        selectedStartTime = time
        buttonTimeFrom.text = selectedStartTime.toString("HH:mm")
        areSelectedTimesCorrect()
    }

    private fun setEndTime(time: LocalTime) {
        selectedEndTime = time
        buttonTimeTo.text = selectedEndTime.toString("HH:mm")
        areSelectedTimesCorrect()
    }

    private fun setSelectedDate(date: DateTime) {
        val dateStart = date.withDayOfWeek(DateTimeConstants.MONDAY)
        val dateEnd = dateStart.plusDays(MultidayViewFragment.MAX_COLUMNS - 1)
        buttonWeek.text = dateStringFormat.format(dateStart.toString("dd.MM.YYYY"), dateEnd.toString("dd.MM.YYYY"))

        this.selectedWeekStart = dateStart.withTimeAtStartOfDay()
        this.selectedWeekEnd = dateStart.plusDays(MultidayViewFragment.MAX_COLUMNS).withTimeAtStartOfDay()
    }

    private fun areSelectedTimesCorrect(): Boolean {
        return if (selectedStartTime.isBefore(selectedEndTime)) {
            // Error occurred
            imageError.visibility = View.GONE
            true
        } else {
            imageError.visibility = View.VISIBLE
            false
        }
    }

    private fun hideSoftKeyboard(view: View) {
        val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
    }

    companion object {
        private const val ARG_WEEK_MILLIS = "week-millis"
        private const val ARG_START_MILLIS = "start-millis"
        private const val ARG_END_MILLIS = "end-millis"
    }
}
