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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.NoInternetConnectionException
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

        val viewExit = layout.findViewById<ImageView>(R.id.viewExit)
        viewExit.setOnClickListener {
            exit()
        }

        val viewEdit = layout.findViewById<ImageButton>(R.id.viewEdit)
        viewEdit.setOnClickListener {
            viewModel.editOrCreateEvent(viewModel.selectedEvent.value!!)
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
        // Room can be empty
        if (selectedEvent.room == null || selectedEvent.room == "") {
            viewEventRoom.visibility = View.GONE
            imagePlace.visibility = View.GONE
        } else {
            viewEventRoom.visibility = View.VISIBLE
            imagePlace.visibility = View.VISIBLE
        }

        // Update info
        this.headerView.setBackgroundColor(resources.getColor(selectedEvent.color, null))
        this.viewAcronym.text = selectedEvent.acronym
        this.viewEventType.text = selectedEvent.event_type.getLabel(context!!)
        this.viewEventRoom.text = selectedEvent.room
        this.viewEventDate.text =
            "${selectedEvent.starts_at.dayOfWeek().asText} ${selectedEvent.starts_at.toString("dd.MM.YYYY")}"
        this.viewEventTime.text = "${timeString(selectedEvent.starts_at)} – ${timeString(selectedEvent.ends_at)}"

        clickableTextView(viewEventName, SearchItem(selectedEvent.acronym, selectedEvent.fullName, ItemType.COURSE))
        if(selectedEvent.room != null) {
            clickableTextView(viewEventRoom, SearchItem(selectedEvent.room!!, type = ItemType.ROOM))
        }

        if(selectedEvent.capacity == 0 && selectedEvent.occupied == 0) {
            this.capacityGroup.visibility = View.GONE
        } else {
            this.capacityGroup.visibility = View.VISIBLE
            this.viewStudentsCount.text = selectedEvent.occupied.toString()
            this.viewCapacity.text = selectedEvent.capacity.toString()
        }

        teachersList.removeAllViews()
        selectedEvent.teacherIds.forEachIndexed { i, id ->
            val name = selectedEvent.teachersNames.elementAtOrNull(i)
            addTeacher(SearchItem(id, name, ItemType.PERSON))
        }
    }

    private fun addTeacher(searchItem: SearchItem) {
        val textView = TextView(context)
        clickableTextView(textView, searchItem)

        val layoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.bottomMargin = 2.toDp(context!!)
        textView.layoutParams = layoutParams

        teachersList.addView(textView)
    }

    private fun clickableTextView(textView: TextView, searchItem: SearchItem) {
        textView.text = searchItem.toString()

        if(!viewModel.canBeClicked(searchItem)) {
            return
        }

        // Make textview clickable
        textView.paintFlags = textView.paintFlags or UNDERLINE_TEXT_FLAG
        textView.setTextColor(resources.getColor(R.color.clickableText, null))
        textView.setOnClickListener {
            alertDialogBuilder.setMessage(
                String.format(
                    getString(R.string.alertDialog_message_eventClicked),
                    searchItem.toString()
                )
            )
                .setPositiveButton(getString(R.string.alertDialog_positive_yes)) { dialog, id ->
                    if(viewModel.canBeClicked(searchItem)) {
                        viewModel.timetableOwner.postValue(Pair(searchItem.id, searchItem.type))
                        exit()
                    } else {
                        viewModel.thrownException.postValue(NoInternetConnectionException())
                    }
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
