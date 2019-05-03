package cz.budikpet.bachelorwork.screens.eventView


import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Paint.UNDERLINE_TEXT_FLAG
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.toDp
import kotlinx.android.synthetic.main.fragment_event_view.*
import org.joda.time.DateTime

// TODO: If clickable things do not exist, app freezes (user defined or edited events). Check if it exists.
// TODO: Teacher list should show full names

class EventViewFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var selectedEvent: TimetableEvent

    private val alertDialogBuilder: AlertDialog.Builder by lazy {
        AlertDialog.Builder(context!!)
            .setTitle(getString(R.string.alertDialog_title_notice))
            .setNegativeButton(getString(R.string.alertDialog_negative_no)) { dialog, id ->
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_event_view, container, false)
        val exit = layout.findViewById<ImageView>(R.id.exit)

        exit.setOnClickListener {
            exit()
        }

        return layout
    }

    private fun subscribeObservers() {
        viewModel.selectedEvent.observe(this, Observer { selectedEvent ->
            if (selectedEvent != null) {
                this.selectedEvent = selectedEvent
                updateView()
            }
        })
    }

    private fun updateView() {
        val selectedEvent = viewModel.selectedEvent.value!!

        // Room can be empty
        if (selectedEvent.room == null || selectedEvent.room == "") {
            eventRoom.visibility = View.GONE
            placeImage.visibility = View.GONE
        } else {
            eventRoom.visibility = View.VISIBLE
            placeImage.visibility = View.VISIBLE
        }

        // Update info
        this.headerView.setBackgroundColor(resources.getColor(selectedEvent.color, null))
        this.acronym.text = selectedEvent.acronym
        this.eventType.text = selectedEvent.event_type.getLabel(context!!)
        this.eventRoom.text = selectedEvent.room
        this.studentsCount.text = selectedEvent.occupied.toString()
        this.capacity.text = selectedEvent.capacity.toString()
        this.eventDate.text =
            "${selectedEvent.starts_at.dayOfWeek().asText} ${selectedEvent.starts_at.toString("dd.MM.YYYY")}"
        this.eventTime.text = "${timeString(selectedEvent.starts_at)} – ${timeString(selectedEvent.ends_at)}"

        clickableTextView(eventName, selectedEvent.fullName, ItemType.COURSE)
        clickableTextView(eventRoom, selectedEvent.room!!, ItemType.ROOM)

        teachersList.removeAllViews()
        for (teacher in selectedEvent.teachers) {
            addTeacher(teacher)
        }
    }

    private fun addTeacher(teacher: String) {
        val textView = TextView(context)
        clickableTextView(textView, teacher, ItemType.PERSON)

        val layoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.bottomMargin = 2.toDp(context!!)
        textView.layoutParams = layoutParams

        teachersList.addView(textView)
    }

    private fun clickableTextView(textView: TextView, text: String, itemType: ItemType) {
        textView.text = text
        textView.paintFlags = textView.paintFlags or UNDERLINE_TEXT_FLAG
        textView.setTextColor(resources.getColor(R.color.clickableText, null))
        textView.setOnClickListener {
            alertDialogBuilder.setMessage(
                String.format(
                    getString(R.string.alertDialog_message_eventViewTextClicked),
                    text
                )
            )
                .setPositiveButton(getString(R.string.alertDialog_positive_yes)) { dialog, id ->
                    viewModel.timetableOwner.postValue(Pair(text, itemType))
                    exit()
                }
                .show()
        }
    }

    private fun timeString(date: DateTime): String? {
        return date.toString("hh:mm")
    }

    private fun exit() {
        viewModel.selectedEvent.postValue(null)
    }
}