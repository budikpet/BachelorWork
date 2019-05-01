package cz.budikpet.bachelorwork.screens


import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_view.*


class EventViewFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var selectedEvent: TimetableEvent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_event_view, container, false)
    }

    private fun subscribeObservers() {
        viewModel.selectedEvent.observe(this, Observer { selectedEvent ->
            if(selectedEvent != null) {
                this.selectedEvent = selectedEvent
                updateView()
            }
        })
    }

    private fun updateView() {
        val selectedEvent = viewModel.selectedEvent.value!!

        this.exit.setOnClickListener {
            viewModel.selectedEvent.postValue(null)
        }

        this.headerView.setBackgroundColor(resources.getColor(selectedEvent.color, null))
        this.acronym.text = selectedEvent.acronym
        this.eventType.text = selectedEvent.event_type.nameString
        this.eventName.text = selectedEvent.fullName
        this.eventRoom.text = selectedEvent.room
        this.studentsCount.text = selectedEvent.occupied.toString()
        this.capacity.text = selectedEvent.capacity.toString()
    }
}
