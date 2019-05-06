package cz.budikpet.bachelorwork.screens.eventEditView

import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Spinner
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_edit.*


class EventEditFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    private var event: TimetableEvent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_event_edit, container, false)

        val spinnerEventType = layout.findViewById<Spinner>(R.id.spinnerEventType)
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, getEventTypes())
        spinnerEventType.adapter = adapter

        setHasOptionsMenu(true)

        return layout
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val toolbar = eventEditToolbar
        inflater?.inflate(R.menu.event_edit_bar, toolbar.menu)

        toolbar.setNavigationIcon(R.drawable.ic_close_black_24dp)

        toolbar.setNavigationOnClickListener {
            viewModel.selectedEvent.postValue(Pair(false, event))
        }

    }

    private fun subscribeObservers() {

    }

    private fun getEventTypes(): List<String> {
        return EventType.values()
            .map { it.getLabel(context!!) }
    }


}
