package cz.budikpet.bachelorwork.screens.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.toDp
import kotlinx.android.synthetic.main.fragment_multidayview_list.view.*
import org.joda.time.*
import javax.inject.Inject

/**
 * Fragment used to show multiple days like Google Calendar Week View.
 */
class MultidayViewFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var onEmptySpaceClickListener: View.OnClickListener
    private lateinit var onEventClickListener: View.OnClickListener

    /**
     * The only selected empty space is this one. Makes sure that at most one empty space add button is visible.
     */
    private var selectedEmptySpace: View? = null

    private var eventsColumnsCount = MAX_COLUMNS
    private val eventPadding by lazy { 2f.toDp(context!!) }
    private lateinit var firstDate: DateTime
    private val dpPerMinRatio = 1
    private val numOfLessons by lazy { sharedPreferences.getInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 0) }
    private val breakLength by lazy { sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 0) }
    private val lessonLength by lazy { sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 0) }
    private val lessonsStartTime by lazy {
        LocalTime().withMillisOfDay(
            sharedPreferences.getInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), 0)
        )
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var eventsColumns: List<ConstraintLayout>

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()

        arguments?.let {
            eventsColumnsCount = it.getInt(ARG_COLUMN_COUNT)
            firstDate = DateTime().withMillis(it.getLong(ARG_START_DATE)).withTimeAtStartOfDay()

            if (eventsColumnsCount == MAX_COLUMNS) {
                firstDate = firstDate.withDayOfWeek(DateTimeConstants.MONDAY)
            }
        }

        createListeners()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_multidayview_list, container, false)
        val rowsList = layout.rowsList
        val timesList = layout.timesList

        // Store references to dynamic event columns
        eventsColumns = listOf(
            layout.eventColumn0, layout.eventColumn1, layout.eventColumn2, layout.eventColumn3,
            layout.eventColumn4, layout.eventColumn5, layout.eventColumn6
        )

        // Create rows
        val currRowTime = firstDate.withTime(lessonsStartTime)
        for (i in 0 until numOfLessons) {
            val time = currRowTime.plusMinutes(i * (lessonLength + breakLength))
            createNewRow(inflater, rowsList, timesList, time)
        }

        // Hide views according to the number of columns
        val dayDisplayLayout = layout.dayDisplay
        for (i in 0 until MAX_COLUMNS) {
            val dayTextView = getDayTextView(i, dayDisplayLayout)

            if (i >= eventsColumnsCount) {
                dayTextView.visibility = View.GONE
                eventsColumns.elementAt(i).visibility = View.GONE
            } else {
                val currDate = firstDate.plusDays(i)
                val dayText = currDate.dayOfWeek().getAsShortText(null).capitalize()
                dayTextView.apply {
                    text = "$dayText\n${currDate.toString("dd")}"

                    if (currDate.toLocalDate().isEqual(LocalDate())) {
                        setTypeface(null, Typeface.BOLD)
                    }
                }
            }
        }

        return layout
    }

    private fun subscribeObservers() {

        // One observer per created fragment
        viewModel.events.observe(this, Observer { events ->
            if (events != null) {
                // Add events to the view
                updateEventsView(events)
            }
        })
    }

    private fun createListeners() {
        onEmptySpaceClickListener = View.OnClickListener { emptySpace ->
            val selectedStartTime = emptySpace.tag as DateTime

            if (selectedEmptySpace != null) {
                if (selectedEmptySpace!!.tag != selectedStartTime || selectedEmptySpace!!.id != emptySpace.id) {
                    // A different empty space was selected previously so it needs to be hidden
                    selectedEmptySpace!!.alpha = 0f
                }
            }

            selectedEmptySpace = emptySpace

            if (emptySpace.alpha == 1f) {
                emptySpace.alpha = 0f
                viewModel.onAddEventClicked(selectedStartTime, selectedStartTime.plusMinutes(lessonLength))
            } else {
                // Make the picture symbolizing event adding visible
                emptySpace.alpha = 1f
            }

        }

        onEventClickListener = View.OnClickListener { eventView ->
            viewModel.onEventClicked(eventView.tag as TimetableEvent)
        }
    }

    private fun getDayTextView(num: Int, dayDisplayLayout: LinearLayout): TextView {
        return dayDisplayLayout.findViewById<TextView>(
            resources.getIdentifier("dayTextView$num", "id", context!!.packageName)
        )
    }

    /**
     * Adds new time row into the View.
     */
    private fun createNewRow(
        inflater: LayoutInflater,
        rowsList: LinearLayout,
        timesList: LinearLayout,
        time: DateTime
    ) {
        val rowView = inflater.inflate(R.layout.time_row, null, false)
        val timeTextView = inflater.inflate(R.layout.time_text_view, null, false) as TextView
        timeTextView.text = time.toString("HH:mm")

        val layoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.bottomMargin = (breakLength * dpPerMinRatio).toDp(context!!)
        layoutParams.height = (lessonLength * dpPerMinRatio).toDp(context!!)
        rowView.layoutParams = layoutParams
        timeTextView.layoutParams = layoutParams

        for (i in 0 until MAX_COLUMNS) {
            val emptySpace = rowView
                .findViewById<View>(resources.getIdentifier("space$i", "id", context!!.packageName))

            emptySpace.tag = time.plusDays(i)

            if (i < eventsColumnsCount) {
                emptySpace.setOnClickListener(onEmptySpaceClickListener)
            } else {
                // This column is not displayed at all
                emptySpace.visibility = View.GONE
            }

        }

        rowsList.addView(rowView)
        timesList.addView(timeTextView)
    }

    // MARK: Dynamically added events

    /**
     * Add events from the collection into the view.
     */
    private fun updateEventsView(events: List<TimetableEvent>) {
        var currIndex = 0
        val preparedCollection = events
            .filter {
                firstDate.isBefore(it.starts_at.millis) && firstDate.plusDays(eventsColumnsCount).isAfter(it.starts_at.millis)
                        && !it.deleted
            }
            .filter { it ->
                return@filter when {
                    it.siriusId != null -> it.siriusId != -1
                    else -> true
                }
            }
            .map { return@map IndexedTimetableEvent(-1, it) }

        if(preparedCollection.isEmpty()) {
            if(!viewModel.areLoadedEventsUpdated()) {
                // Loaded events haven't been updated yet
                viewModel.updateCalendars(viewModel.timetableOwner.value!!.first)
            }

            return
        }

        // Clear columns
        for (column in eventsColumns) {
            column.removeAllViews()
        }

        // Sets up indexes. Events with the same index are overlapping.
        preparedCollection.forEach { indexedTimetableEvent1: IndexedTimetableEvent ->
            if (indexedTimetableEvent1.index == -1) {
                indexedTimetableEvent1.index = currIndex

                preparedCollection.forEach { indexedTimetableEvent2: IndexedTimetableEvent ->
                    if (indexedTimetableEvent2.index == -1) {
                        if (indexedTimetableEvent1.timetableEvent.overlapsWith(indexedTimetableEvent2.timetableEvent)) {
                            indexedTimetableEvent2.index = currIndex
                        }
                    }
                }
            }

            currIndex++
        }

        preparedCollection.groupBy { it.index }
            .forEach { mapEntry ->
                val currEvents = mapEntry.value.map { it.timetableEvent }

                if (currEvents.size == 1) {
                    // Add lone event to the view
                    addEvent(currEvents.first())
                } else {
                    // Add overlapping event to the view
                    addOverlappingEvents(currEvents)
                }
            }
    }

    /**
     * Adds new non overlapping event into the view.
     */
    private fun addEvent(event: TimetableEvent) {
        // Create event view
        val eventView = createEventView(event)

        // Add event view into the correct column
        val column = getEventsColumn(event)
        column.addView(eventView)

        val set = ConstraintSet()
        set.clone(column)

        set.connect(eventView.id, ConstraintSet.TOP, column.id, ConstraintSet.TOP, getEventViewStart(event))
        set.constrainWidth(eventView.id, 0f.toDp(context!!))
        set.connect(eventView.id, ConstraintSet.START, column.id, ConstraintSet.START, eventPadding)
        set.connect(eventView.id, ConstraintSet.END, column.id, ConstraintSet.END, eventPadding)

        set.applyTo(column)
    }

    /**
     * Adds new overlapping events into the view.
     */
    private fun addOverlappingEvents(events: List<TimetableEvent>) {
        // Add event view into the correct column
        val column = getEventsColumn(events.first())

        // Add events to the constLayout and get a list of ids in the process
        val eventIds = events
            .map {
                val eventView = createEventView(it)
                column.addView(eventView)

                return@map Pair(eventView.id, getEventViewStart(it))
            }

        val set = ConstraintSet()
        set.clone(column)

        // Set constraints
        val idsArray = eventIds.map { pair ->
            set.constrainWidth(pair.first, 0f.toDp(context!!))
            set.connect(pair.first, ConstraintSet.TOP, column.id, ConstraintSet.TOP, pair.second)
            set.setMargin(pair.first, ConstraintSet.START, eventPadding)
            set.setMargin(pair.first, ConstraintSet.END, eventPadding)

            return@map pair.first
        }.toIntArray()

        // Create chain
        set.createHorizontalChain(
            column.id, ConstraintSet.LEFT, column.id, ConstraintSet.RIGHT,
            idsArray, null, ConstraintSet.CHAIN_SPREAD
        )

        set.applyTo(column)

    }

    /**
     * @return An EventView to be displayed in the view.
     */
    private fun createEventView(event: TimetableEvent): TextView {
        val eventView = TextView(context!!)
        eventView.text = event.acronym
        eventView.setBackgroundColor(resources.getColor(event.color, null))
        eventView.height = getEventViewHeight(event)
        eventView.tag = event
        eventView.setOnClickListener(onEventClickListener)

        if (eventView.id == -1) {
            eventView.id = View.generateViewId()
        }

        return eventView
    }

    /**
     * @return The column where the event is to be placed.
     */
    private fun getEventsColumn(event: TimetableEvent): ConstraintLayout {
        val index = Days.daysBetween(firstDate, event.starts_at).days
        return eventsColumns.elementAt(index)
    }

    /**
     * Calculates height of the EventView according to how much time the event takes.
     * @return Height of the EventView in dps.
     */
    private fun getEventViewHeight(event: TimetableEvent): Int {
        return Minutes.minutesBetween(event.starts_at, event.ends_at)
            .minutes
            .toDp(context!!)
    }

    /**
     * Calculates starting y point of the EventView according to its start time.
     * @return A starting y point of the EventView in dps.
     */
    private fun getEventViewStart(event: TimetableEvent): Int {
        return Minutes.minutesBetween(event.starts_at.withTime(lessonsStartTime), event.starts_at)
            .minutes
            .toDp(context!!)
    }

    // MARK: Initializers

    interface Callback {
        fun onAddEventClicked(startTime: DateTime, endTime: DateTime)

        fun onEventClicked(event: TimetableEvent)
    }

    companion object {

        const val ARG_COLUMN_COUNT = "column-count"
        const val ARG_START_DATE = "start-date"

        const val MAX_COLUMNS = 7

        @JvmStatic
        fun newInstance(columnCount: Int, firstDate: DateTime): MultidayViewFragment =
            MultidayViewFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                    putLong(ARG_START_DATE, firstDate.millis)
                }
            }
    }

    /**
     * Helper data class used when grouping events by the way they overlap.
     */
    private data class IndexedTimetableEvent(var index: Int, val timetableEvent: TimetableEvent)
}