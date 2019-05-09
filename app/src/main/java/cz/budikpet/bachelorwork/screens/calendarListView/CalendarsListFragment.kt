package cz.budikpet.bachelorwork.screens.calendarListView

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.*
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.MarginItemDecoration
import cz.budikpet.bachelorwork.util.toDp


class CalendarsListFragment : Fragment(), CalendarsListSwipeDelete.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    private lateinit var calendarsList: RecyclerView

    /** Undo snackbar. */
    private val snackbar: Snackbar by lazy {
        val mainActivityLayout = activity?.findViewById<ConstraintLayout>(R.id.main_activity)
        val snackbar = Snackbar
            .make(
                mainActivityLayout!!, getString(R.string.snackbar_CalendarUnavailable),
                Snackbar.LENGTH_LONG
            )
            .setAction(getString(R.string.snackbar_Undo)) {
                val adapter = calendarsList.adapter as CalendarsListAdapter?
                adapter?.undoDelete()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        // Undo button was not pressed, delete the calendar
                        val adapter = calendarsList.adapter as CalendarsListAdapter?
                        val deletedItem = adapter?.recentlyDeletedItem?.second

                        if (adapter != null && deletedItem != null) {
                            // Remove the calendar from the Google Calendar service
                            viewModel.removeCalendar(MyApplication.calendarNameFromId(deletedItem.id))
                        }
                    }
                }

                override fun onShown(snackbar: Snackbar) {}
            })

        return@lazy snackbar
    }

    private val goToTimetableDialogBuilder: AlertDialog.Builder by lazy {
        AlertDialog.Builder(context!!)
            .setTitle(R.string.alertDialog_title_notice)
            .setNegativeButton(getString(R.string.alertDialog_negative_no)) { dialog, id -> }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        setHasOptionsMenu(true)

        subscribeObservers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        calendarsList = inflater.inflate(
            R.layout.fragment_calendars_list,
            container,
            false
        ) as RecyclerView
        calendarsList.layoutManager = LinearLayoutManager(context)
        calendarsList.addItemDecoration(MarginItemDecoration(4.toDp(context!!)))

        val adapter = CalendarsListAdapter(context!!) { searchItem ->
            Log.i(TAG, "$searchItem")
            val message = String.format(getString(R.string.alertDialog_message_eventClicked), searchItem.id)
            goToTimetableDialogBuilder
                .setMessage(message)
                .setPositiveButton(getString(R.string.alertDialog_positive_yes)) { dialog, id ->
                    viewModel.goToLastMultidayView()
                    viewModel.timetableOwner.postValue(Pair(searchItem.id, searchItem.type))
                }
                .show()
        }
        calendarsList.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(CalendarsListSwipeDelete(context!!, this))
        itemTouchHelper.attachToRecyclerView(calendarsList)

        return calendarsList
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(R.string.sidebar_SavedCalendars)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun subscribeObservers() {
        viewModel.savedTimetables.observe(this, Observer { searchItemsList ->
            // Fill the recycler view

            if (searchItemsList != null) {
                if (calendarsList.adapter != null) {
                    val adapter = calendarsList.adapter as CalendarsListAdapter
                    val items = searchItemsList.filter { it.id != viewModel.ctuUsername }
                    adapter.updateValues(items)
                }
            }
        })
    }

    // MARK: Swipe to delete

    override fun onSwipeDelete(position: Int) {
        val adapter = calendarsList.adapter as CalendarsListAdapter?
        adapter?.removeItem(position)
        showUndoSnackbar()
    }

    private fun showUndoSnackbar() {
        snackbar.show()
    }
}
