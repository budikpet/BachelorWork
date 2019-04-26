package cz.budikpet.bachelorwork.screens.main

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
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
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.toDp
import kotlinx.android.synthetic.main.fragment_multidayview_list.view.*
import org.joda.time.*

/**
 * Fragment used to show multiple days like Google Calendar Week View.
 */
class MultidayViewFragment : Fragment() {
    private val events = mutableListOf<TimetableEvent>()

    private var listener: Callback? = null
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

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var eventsColumns: List<ConstraintLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = context!!.getSharedPreferences("Pref", Context.MODE_PRIVATE)

        arguments?.let {
            eventsColumnsCount = it.getInt(ARG_COLUMN_COUNT)
            firstDate = DateTime().withMillis(it.getLong(ARG_START_DATE)).withTimeAtStartOfDay()

            if (eventsColumnsCount == MAX_COLUMNS) {
                firstDate = firstDate.withDayOfWeek(DateTimeConstants.MONDAY)
            }
        }

        createListeners()
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
                listener?.onAddEventClicked(selectedStartTime, selectedStartTime.plusMinutes(lessonLength))
            } else {
                // Make the picture symbolizing event adding visible
                emptySpace.alpha = 1f
            }

        }

        onEventClickListener = View.OnClickListener { eventView ->
            listener?.onEventClicked(eventView.tag as TimetableEvent)
        }
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
        val currRowTime = DateTime().withDayOfWeek(DateTimeConstants.MONDAY).withTime(lessonsStartTime)
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
                dayTextView.text = firstDate.plusDays(i).dayOfWeek().getAsShortText(null).capitalize()
            }
        }

        createDummyEvents()
        updateEventsView()

        return layout
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
        layoutParams.bottomMargin = (breakLength * dpPerMinRatio).toFloat().toDp(context!!)
        layoutParams.height = (lessonLength * dpPerMinRatio).toFloat().toDp(context!!)
        rowView.layoutParams = layoutParams
        timeTextView.layoutParams = layoutParams

        for (i in 0 until MAX_COLUMNS) {
            Log.i("MY_test", "$i")

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callback) {
            listener = context
        } else {
            throw RuntimeException("$context must implement Callback")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    // MARK: Dynamically added events

    private fun createDummyEvents() {
        val mondayDate = firstDate.withDayOfWeek(DateTimeConstants.MONDAY).withTimeAtStartOfDay()

        val event1Start = mondayDate.withTime(11, 0, 0, 0)
        val event1End = event1Start.plusMinutes(90)

        val event1 = TimetableEvent(
            room = "T9:151",
            fullName = "BI-BAP",
            acronym = "BI-BAP_1",
            capacity = 90,
            starts_at = event1Start,
            ends_at = event1End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("balikm"),
            occupied = 49
        )

        val event2Start = event1Start.withTime(9, 15, 0, 0)
        val event2End = event2Start.plusMinutes(195)

        val event2 = TimetableEvent(
            room = "T9:153",
            fullName = "BI-END",
            acronym = "BI-END_2",
            capacity = 90,
            starts_at = event2Start,
            ends_at = event2End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("bulim"),
            occupied = 49
        )

        val event3Start = mondayDate.withTime(8, 15, 0, 0).plusDays(4)
        val event3End = event3Start.withTime(10, 45, 0, 0)

        val event3 = TimetableEvent(
            room = "T9:153",
            fullName = "BI-LONE",
            acronym = "BI-LONE_3",
            capacity = 90,
            starts_at = event3Start,
            ends_at = event3End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("bulim"),
            occupied = 49
        )

        val event4Start = mondayDate.withTime(11, 0, 0, 0).plusDays(5)
        val event4End = event4Start.plusMinutes(90)

        val event4 = TimetableEvent(
            room = "T9:151",
            fullName = "BI-BAP",
            acronym = "BI-BAP_4",
            capacity = 90,
            starts_at = event4Start,
            ends_at = event4End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("balikm"),
            occupied = 49
        )

        val event5Start = event4Start.withTime(9, 15, 0, 0)
        val event5End = event5Start.plusMinutes(195)

        val event5 = TimetableEvent(
            room = "T9:153",
            fullName = "BI-END",
            acronym = "BI-END_5",
            capacity = 90,
            starts_at = event5Start,
            ends_at = event5End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("bulim"),
            occupied = 49
        )

        val event6Start = event4Start.withTime(14, 15, 0, 0)
        val event6End = event6Start.plusMinutes(90)

        val event6 = TimetableEvent(
            room = "T9:153",
            fullName = "BI-LONE",
            acronym = "BI-LONE_6",
            capacity = 90,
            starts_at = event6Start,
            ends_at = event6End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("bulim"),
            occupied = 49
        )

        val event7Start = mondayDate.withTime(19, 15, 0, 0).plusDays(2)
        val event7End = event7Start.plusMinutes(200)

        val event7 = TimetableEvent(
            room = "T9:153",
            fullName = "BI-LATE",
            acronym = "BI-LATE_7",
            capacity = 90,
            starts_at = event7Start,
            ends_at = event7End,
            event_type = EventType.COURSE_EVENT,
            teachers = arrayListOf("bulim"),
            occupied = 49
        )


        events.add(event1)
        events.add(event2)

        events.add(event3)
        events.add(event6)

        events.add(event4)
        events.add(event5)

        events.add(event7)
    }

    /**
     * Add events from the collection into the view.
     */
    private fun updateEventsView() {
        var currIndex = 0
        val lastDate = firstDate.plusDays(eventsColumnsCount)
        val preparedCollection = events
            .filter { firstDate.isBefore(it.starts_at.millis) && lastDate.isAfter(it.starts_at.millis) }
            .map { return@map IndexedTimetableEvent(-1, it) }

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
        eventView.setBackgroundColor(Color.RED)
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
            .toFloat()
            .toDp(context!!)
    }

    /**
     * Calculates starting y point of the EventView according to its start time.
     * @return A starting y point of the EventView in dps.
     */
    private fun getEventViewStart(event: TimetableEvent): Int {
        return Minutes.minutesBetween(event.starts_at.withTime(lessonsStartTime), event.starts_at)
            .minutes
            .toFloat()
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
        fun newInstance(columnCount: Int, startDate: DateTime): MultidayViewFragment {
            var columnCount = columnCount

            when {
                columnCount < 1 -> columnCount = 1
                columnCount > 7 -> columnCount = 7
            }

            return MultidayViewFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                    putLong(ARG_START_DATE, startDate.millis)
                }
            }
        }
    }

    /**
     * Helper data class used when grouping events by the way they overlap.
     */
    private data class IndexedTimetableEvent(var index: Int, val timetableEvent: TimetableEvent)
}