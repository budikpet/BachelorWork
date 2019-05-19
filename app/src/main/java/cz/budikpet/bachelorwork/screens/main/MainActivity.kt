package cz.budikpet.bachelorwork.screens.main

import android.accounts.AccountManager
import android.app.Activity
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
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.di.util.MyViewModelFactory
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.calendarListView.CalendarsListFragment
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.screens.emailListView.EmailListFragment
import cz.budikpet.bachelorwork.screens.eventEditView.EventEditFragment
import cz.budikpet.bachelorwork.screens.eventView.EventViewFragment
import cz.budikpet.bachelorwork.screens.freeTimeView.FreeTimeFragment
import cz.budikpet.bachelorwork.screens.multidayView.MultidayFragmentHolder
import cz.budikpet.bachelorwork.screens.settings.SettingsFragment
import cz.budikpet.bachelorwork.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_share_timetable.view.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject


/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), PermissionsCheckerFragment.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    companion object {
        const val CODE_GOOGLE_LOGIN = 0
        const val REQUEST_AUTHORIZATION = 1
    }

    private lateinit var viewModel: MainViewModel

    @Inject
    lateinit var viewModelFactory: MyViewModelFactory

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

    private var requested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MyApplication.appComponent.inject(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)

        val toolbar = findViewById<Toolbar>(R.id.mainToolbar)
        setSupportActionBar(toolbar)
        progressBar = findViewById(R.id.progressBar)

        initSideBar(savedInstanceState)

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

    private fun initSideBar(savedInstanceState: Bundle?) {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val sidebarNavView = findViewById<NavigationView>(R.id.sidebarNavView)

        sidebarNavView.setNavigationItemSelectedListener { sidebarItem ->
            viewModel.selectedSidebarItem.postValue(sidebarItem.itemId)
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
        viewModel.operationsRunning.observe(this, Observer { operationsRunning ->
            Log.i(TAG, "Running: $operationsRunning")
            if (operationsRunning != null) {
                if (operationsRunning.number <= 0) {
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

        viewModel.thrownException.observe(this, Observer { exception ->
            if (exception is UserRecoverableAuthIOException) {
                val exception = exception as UserRecoverableAuthIOException
                if(!requested) {
                    requested = true
                    startActivityForResult(exception.intent, REQUEST_AUTHORIZATION)
                }
            }
        })

        viewModel.showMessage.observe(this, Observer {
            val passableResource = it ?: return@Observer

            val string = if (passableResource.args == null) {
                getString(passableResource.resId)
            } else {
                getString(passableResource.resId).format(passableResource.args)
            }

            Toast.makeText(this, string, Toast.LENGTH_LONG).show()
        })

        viewModel.selectedSidebarItem.observe(this, Observer {
            if (it != null) {
                displaySelectedMainFragment(it)
            }
        })

        viewModel.ctuSignedOut.observe(this, Observer {
            sharedPreferences.edit {
                remove(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString())
                remove(SharedPreferencesKeys.CTU_USERNAME.toString())
            }

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        })
    }

    private fun displaySelectedMainFragment(itemId: Int) {
        supportFragmentManager.inTransaction {
            when (itemId) {
                R.id.sidebarDayView -> {
                    viewModel.daysPerMultidayViewFragment = 1
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                    searchSuggestions.visibility = View.VISIBLE
                }
                R.id.sidebarThreeDayView -> {
                    viewModel.daysPerMultidayViewFragment = 3
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                    searchSuggestions.visibility = View.VISIBLE
                }
                R.id.sidebarWeekView -> {
                    viewModel.daysPerMultidayViewFragment = 7
                    replace(R.id.mainFragmentHolder, MultidayFragmentHolder())
                    searchSuggestions.visibility = View.VISIBLE
                }
                R.id.sidebarSavedCalendars -> {
                    replace(R.id.mainFragmentHolder, CalendarsListFragment())
                    searchSuggestions.visibility = View.GONE
                }
                R.id.sidebarFreeTime -> {
                    replace(R.id.mainFragmentHolder, FreeTimeFragment())
                    searchSuggestions.visibility = View.GONE
                }
                R.id.sidebarShareTimetable -> {
                    replace(R.id.mainFragmentHolder, EmailListFragment())
                    searchSuggestions.visibility = View.GONE
                }
                R.id.sidebarSettings -> {
                    replace(R.id.mainFragmentHolder, SettingsFragment())
                    searchSuggestions.visibility = View.GONE
                }
            }
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

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

                viewModel.ready(true)

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

                viewModel.ready(true)
            } else {
                Log.i(TAG, "Google account not specified.")
                alertDialogBuilder.show()
            }
        } else if(requestCode == REQUEST_AUTHORIZATION) {
            requested = false
            if (resultCode == Activity.RESULT_OK) {
                viewModel.ready(true)
            } else {
                startActivityForResult(credential.newChooseAccountIntent(), CODE_GOOGLE_LOGIN)
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
            R.id.itemSharePersonalTimetable -> showShareDialog()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showShareDialog() {
        val shareDialogView = layoutInflater.inflate(R.layout.dialog_share_timetable, null)
        val emailEditText = shareDialogView.emailEditText

        AlertDialog.Builder(this)
            .setView(shareDialogView)
            .setPositiveButton(getString(R.string.alertDialog_share)) { dialog, id ->
                viewModel.shareTimetable(emailEditText.text.toString())
            }
            .setNegativeButton(getString(R.string.alertDialog_quit)) { _, _ -> }
            .show()

    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}