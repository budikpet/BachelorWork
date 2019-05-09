package cz.budikpet.bachelorwork.screens.main

import android.accounts.AccountManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.calendarListView.CalendarsListFragment
import cz.budikpet.bachelorwork.screens.eventEditView.EventEditFragment
import cz.budikpet.bachelorwork.screens.eventView.EventViewFragment
import cz.budikpet.bachelorwork.screens.freeTimeView.FreeTimeFragment
import cz.budikpet.bachelorwork.screens.multidayView.MultidayFragmentHolder
import cz.budikpet.bachelorwork.util.*
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import pub.devrel.easypermissions.EasyPermissions
import retrofit2.HttpException
import javax.inject.Inject


/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), PermissionsCheckerFragment.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        private const val CODE_GOOGLE_LOGIN = 0
    }

    private lateinit var viewModel: MainViewModel

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private lateinit var searchSuggestions: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var permissionsCheckerFragment: PermissionsCheckerFragment

    private val alertDialogBuilder: AlertDialog.Builder by lazy {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alterDialog_title_googleAccount))
            .setMessage(getString(R.string.alertDialog_message_googleAccount))
            .setPositiveButton(getString(R.string.alertDialog_positive_googleAccount)) { dialog, id ->
                checkGoogleLogin()
            }
            .setNegativeButton(getString(R.string.alertDialog_quit)) { dialog, id ->
                quitApplication()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MyApplication.appComponent.inject(this)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        val toolbar = findViewById<Toolbar>(R.id.mainToolbar)
        setSupportActionBar(toolbar)
        progressBar = findViewById(R.id.progressBar)

        initSideBar()

        initFragments(savedInstanceState)

        initSearchSuggestionRecyclerView()

        subscribeObservers()

        // Check logins
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        viewModel.checkSiriusAuthorization(response, exception)

        checkGoogleLogin()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Need to test disposing
        viewModel.onDestroy()
    }

    private fun initSideBar() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val sidebarNavView = findViewById<NavigationView>(R.id.sidebarNavView)

        sidebarNavView.setNavigationItemSelectedListener { sidebarItem ->
            displaySelectedMainFragment(sidebarItem.itemId)
            drawerLayout.closeDrawer(Gravity.START)
            true
        }
    }

    private fun initFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()

            supportFragmentManager.inTransaction {
                add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
            }

        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment
        }
    }

    private fun initSearchSuggestionRecyclerView() {
        searchSuggestions = this.findViewById(R.id.searchSuggestions)
        searchSuggestions.layoutManager = LinearLayoutManager(this)
        searchSuggestions.addItemDecoration(MarginItemDecoration(4.toDp(this)))
        // Init searchSuggestions RecyclerView adapter
        val adapter = SearchSuggestionsAdapter(this, onItemClickFunction = {
            // Signal to exit search
            viewModel.searchItems.postValue(listOf())
            viewModel.lastSearchQuery = ""

            // Get events of the picked item
            viewModel.timetableOwner.postValue(Pair(it.id, it.type))
        })
        searchSuggestions.adapter = adapter
    }

    private fun subscribeObservers() {
        viewModel.operationRunning.observe(this, Observer { updating ->
            Log.i(TAG, "Calendars updating: $updating")

            if (updating != null) {
                if (!updating) {
                    // Update done
                    progressBar.visibility = View.GONE
                } else {
                    // Update started
                    progressBar.visibility = View.VISIBLE
                }
            }
        })

        viewModel.searchItems.observe(this, Observer { searchItems ->
            if (searchItems != null) {
                val adapter = searchSuggestions.adapter as SearchSuggestionsAdapter?
                adapter?.updateValues(searchItems)
            }
        })

        viewModel.timetableOwner.observe(this, Observer { pair ->
            val username = pair?.first
            if (username != null && username != "") {
                supportActionBar?.title = username
            }

            // Load events of the newly selected timetable
            viewModel.updatedEventsInterval = null
            viewModel.loadEvents()
        })

        viewModel.selectedEvent.observe(this, Observer { selectedEvent ->
            supportFragmentManager.inTransaction {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

                if (selectedEvent == null) {
                    // Hide the event view/ edit entirely
                    val holder = supportFragmentManager.findFragmentById(R.id.eventViewFragmentHolder)
                    if (holder != null) {
                        hide(holder)
                    }

                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

                    return@inTransaction this
                }

                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                replace(R.id.eventViewFragmentHolder, EventViewFragment())
            }
        })

        viewModel.eventToEdit.observe(this, Observer { eventToEdit ->
            supportFragmentManager.inTransaction {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

                if (eventToEdit == null) {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

                    if (viewModel.selectedEvent.value != null) {
                        // Event was selected before editing, go back to it
                        replace(R.id.eventViewFragmentHolder, EventViewFragment())
                        return@inTransaction this
                    }

                    // Hide the event view/ edit entirely
                    val holder = supportFragmentManager.findFragmentById(R.id.eventViewFragmentHolder)
                    if (holder != null) {
                        hide(holder)
                    }
                    return@inTransaction this
                }

                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                replace(R.id.eventViewFragmentHolder, EventEditFragment())
            }
        })

        viewModel.thrownException.observe(this, Observer {
            if (it != null) {
                handleException(it)
            }
        })

        viewModel.selectedSidebarItem.observe(this, Observer {
            if (it != null) {
                displaySelectedMainFragment(it)
            }
        })
    }

    private fun displaySelectedMainFragment(itemId: Int) {
        supportFragmentManager.inTransaction {
            when (itemId) {
                R.id.sidebarDayView -> {
                    viewModel.daysPerMultidayViewFragment = 1
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                }
                R.id.sidebarThreeDayView -> {
                    viewModel.daysPerMultidayViewFragment = 3
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                }
                R.id.sidebarWeekView -> {
                    viewModel.daysPerMultidayViewFragment = 7
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                }
                R.id.sidebarSavedCalendars -> replace(R.id.mainFragmentHolder, CalendarsListFragment())
                R.id.sidebarFreeTime -> replace(R.id.mainFragmentHolder, FreeTimeFragment())
                R.id.sidebarSettings -> Log.i(TAG, "settings")
            }
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    /*
    private fun initButtons() {
        signoutBtn.setOnClickListener {
            viewModel.signOut()

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        }
    }
*/

    // MARK: Google account

    private fun checkGoogleLogin() {
        // Ask a user to log into a Google account once after he logged into CTU

        if (EasyPermissions.hasPermissions(this, *requiredPerms)) {
            if (sharedPreferences.contains(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString())) {
                Log.i(TAG, "Google account name is in SharedPreferences.")

                if (credential.selectedAccountName == null) {
                    // Add the selected google account into credential
                    credential.selectedAccountName =
                        sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), null)
                }

                // TODO: Check if the google account still exists

                viewModel.ready()

            } else {
                // Ask for a Google Account
                Log.i(TAG, "Started Google account log in.")
                startActivityForResult(credential.newChooseAccountIntent(), CODE_GOOGLE_LOGIN)
            }
        } else {
            permissionsCheckerFragment.checkPermissions()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "OnActivityResult")

        if (requestCode == CODE_GOOGLE_LOGIN) {
            if (data != null) {
                // User logged into a Google account, store its name
                val accountName = data.extras!!.getString(AccountManager.KEY_ACCOUNT_NAME)
                sharedPreferences.edit {
                    putString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), accountName)
                }

                credential.selectedAccountName = accountName

                viewModel.ready()

                viewModel.updateCalendars()
            } else {
                Log.i(TAG, "Google account not specified.")
                alertDialogBuilder.show()
            }

        }
    }

    // MARK: Tab Bar

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.itemSync -> {
                Log.i(TAG, "Selected account: ${credential.selectedAccount}")
                viewModel.updateCalendars()

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    // MARK: Exceptions

    private fun handleException(exception: Throwable) {
        var text = getString(R.string.exceptionUnknown)

        if (exception is GoogleAccountNotFoundException) {
            // Prompt the user to select a new google account
            Log.e(TAG, "Used google account not found.")
            text = getString(R.string.exceptionGoogleAccountNotFound)
        } else if (exception is HttpException) {
            Log.e(TAG, "Retrofit 2 HTTP ${exception.code()} exception: ${exception.response()}")
            if (exception.code() == 500) {
                text = getString(R.string.exceptionCTUInternal)
            } else if (exception.code() == 404) {
                text = getString(R.string.exceptionTimetableNotFound)
            }
        } else if (exception is NoInternetConnectionException) {
            Log.e(TAG, "Could not connect to the internet.")
            text = getString(R.string.exceptionInternetUnavailable)
        } else {
            Log.e(TAG, "Unknown exception occurred: $exception")
        }

        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}