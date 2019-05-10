package cz.budikpet.bachelorwork.screens.emailListView

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.calendarListView.CalendarsListSwipeDelete
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.MarginItemDecoration
import cz.budikpet.bachelorwork.util.toDp

class EmailListFragment : Fragment(), CalendarsListSwipeDelete.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    private lateinit var emailsList: RecyclerView

    /** Undo snackbar. */
    private val snackbar: Snackbar by lazy {
        val mainActivityLayout = activity?.findViewById<ConstraintLayout>(R.id.main_activity)
        val snackbar = Snackbar
            .make(
                mainActivityLayout!!, getString(R.string.snackbar_CalendarUnavailable),
                Snackbar.LENGTH_LONG
            )
            .setAction(getString(R.string.snackbar_Undo)) {
                val adapter = emailsList.adapter as EmailListAdapter?
                adapter?.undoDelete()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(snackbar: Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        // Undo button was not pressed, delete the calendar
                        val adapter = emailsList.adapter as EmailListAdapter?
                        val deletedEmail = adapter?.recentlyDeletedItem?.second

                        if (adapter != null && deletedEmail != null) {
                            // Remove the calendar from the Google Calendar service
                            viewModel.unsharePersonalTimetable(deletedEmail)
                        }
                    }
                }

                override fun onShown(snackbar: Snackbar) {}
            })

        return@lazy snackbar
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
        emailsList = inflater.inflate(
            R.layout.fragment_list,
            container,
            false
        ) as RecyclerView
        emailsList.layoutManager = LinearLayoutManager(context)
        emailsList.addItemDecoration(MarginItemDecoration(4.toDp(context!!)))

        emailsList.adapter = EmailListAdapter(context!!)

        val itemTouchHelper = ItemTouchHelper(CalendarsListSwipeDelete(context!!, this))
        itemTouchHelper.attachToRecyclerView(emailsList)

        return emailsList
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.emails_list_bar, menu)
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(R.string.sidebar_ShareTimetable)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun subscribeObservers() {
        viewModel.emails.observe(this, Observer { emailsList ->
            // Fill the recycler view

            if (emailsList != null && this.emailsList.adapter != null) {
                val adapter = this.emailsList.adapter as EmailListAdapter
                adapter.updateValues(emailsList)

            }
        })
    }

    // MARK: Swipe to delete

    override fun onSwipeDelete(position: Int) {
        val adapter = emailsList.adapter as EmailListAdapter?
        adapter?.removeItem(position)
        snackbar.show()
    }
}