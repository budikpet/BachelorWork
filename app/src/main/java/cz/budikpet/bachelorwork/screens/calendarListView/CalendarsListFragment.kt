package cz.budikpet.bachelorwork.screens.calendarListView

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.MarginItemDecoration
import cz.budikpet.bachelorwork.util.toDp
import android.support.v7.widget.helper.ItemTouchHelper


class CalendarsListFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    private lateinit var calendarsList: RecyclerView

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
        calendarsList = inflater.inflate(cz.budikpet.bachelorwork.R.layout.fragment_calendars_list, container, false) as RecyclerView
        calendarsList.layoutManager = LinearLayoutManager(context)
        calendarsList.addItemDecoration(MarginItemDecoration(4.toDp(context!!)))

        val adapter = CalendarsListAdapter(context!!) { searchItem ->
            Log.i(TAG, "$searchItem")
        }
        calendarsList.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(CalendarsListSwipeDelete(context!!, adapter))
        itemTouchHelper.attachToRecyclerView(calendarsList)

        return calendarsList
    }

    private fun subscribeObservers() {
        viewModel.savedTimetables.observe(this, Observer { searchItemsList ->
            // Fill the recycler view

            if(searchItemsList != null) {
                if(calendarsList.adapter != null) {
                    val adapter = calendarsList.adapter as CalendarsListAdapter
                    adapter.updateValues(searchItemsList)
                }
            }
        })
    }
}
