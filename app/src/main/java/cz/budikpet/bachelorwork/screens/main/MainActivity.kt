package cz.budikpet.bachelorwork.screens.main

import android.accounts.AccountManager
import android.app.SearchManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.eventView.EventViewFragment
import cz.budikpet.bachelorwork.screens.multidayView.MultidayFragmentHolder
import cz.budikpet.bachelorwork.util.*
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
        private const val CODE_GOOGLE_LOGIN = 0
    }

    private lateinit var viewModel: MainViewModel

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private lateinit var searchSuggestions: RecyclerView

    private lateinit var permissionsCheckerFragment: PermissionsCheckerFragment
    private lateinit var multidayFragmentHolder: MultidayFragmentHolder
    private lateinit var eventViewFragment: EventViewFragment

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

        initFragments(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        searchSuggestions = this.findViewById(R.id.searchSuggestions)
        searchSuggestions.layoutManager = LinearLayoutManager(this)
        searchSuggestions.addItemDecoration(MarginItemDecoration(4.toDp(this)))

        subscribeObservers()

        // Check logins
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        viewModel.checkSiriusAuthorization(response, exception)

        checkGoogleLogin()
    }

    private fun initFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()
            multidayFragmentHolder = MultidayFragmentHolder()
            eventViewFragment = EventViewFragment()

            supportFragmentManager.inTransaction {
                add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
                add(R.id.multidayViewFragmentHolder, multidayFragmentHolder)
                add(R.id.eventViewFragmentHolder, eventViewFragment)
                hide(eventViewFragment)
            }

        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment

            multidayFragmentHolder =
                supportFragmentManager.findFragmentById(R.id.multidayViewFragmentHolder) as MultidayFragmentHolder

            eventViewFragment =
                supportFragmentManager.findFragmentById(R.id.eventViewFragmentHolder) as EventViewFragment
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Need to test disposing
        viewModel.onDestroy()
    }

    private fun subscribeObservers() {
        viewModel.searchItems.observe(this, Observer { searchItems ->
            if (searchItems != null && searchItems.isNotEmpty()) {
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
                if (selectedEvent != null) {
                    supportActionBar?.hide()
                    show(eventViewFragment)
                    hide(multidayFragmentHolder)
                } else {
                    supportActionBar?.show()
                    hide(eventViewFragment)
                    show(multidayFragmentHolder)
                }
            }
        })
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

    private fun showString(result: List<Event>) {
        var builder = StringBuilder()
        for (event in result) {
            builder.append("${event.links.course} ${event.event_type}: ${event.starts_at}\n")
        }

        showData.text = builder.toString()
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

                viewModel.signedInToGoogle()

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

                viewModel.signedInToGoogle()

                viewModel.updateCalendars()
            } else {
                Log.i(TAG, "Google account not specified.")
                alertDialogBuilder.show()
            }

        }
    }

    // MARK: Tab Bar

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_bar, menu)

        val searchMenuItem = menu?.findItem(R.id.itemSearch)!!
        val searchView = searchMenuItem.actionView as SearchView
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.isSubmitButtonEnabled = true

        // Init searchSuggestions RecyclerView adapter
        val adapter = SearchSuggestionsAdapter(this, onItemClickFunction = {
            searchMenuItem.collapseActionView()

            // Get events of the picked item
            viewModel.timetableOwner.postValue(Pair(it.id, it.type))
        })
        searchSuggestions.adapter = adapter

        // Watch for user input
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String?): Boolean {
                if (query != null) {
                    viewModel.lastSearchQuery = query

                    if (query.count() >= 1) {
                        viewModel.searchSirius(query)
                    }
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.i(TAG, "Submitted: <$query>")
                viewModel.lastSearchQuery = ""
                return true
            }

        })

        // Watch when the user closes the searchView
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                // User entered the searchView
                searchSuggestions.visibility = View.VISIBLE
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                // User left the searchView
                adapter.clear()
                viewModel.lastSearchQuery = ""
                searchSuggestions.visibility = View.GONE
                return true
            }

        })

        // Restore the search view after configuration changes
        val query = viewModel.lastSearchQuery
        if (query.count() > 0) {
            // There are searchItems now
            searchMenuItem.expandActionView()
            searchView.setQuery(query, false)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.itemSync) {
            Log.i(TAG, "Selected account: ${credential.selectedAccount}")
            viewModel.updateCalendars()

            return true
        }


        return super.onOptionsItemSelected(item)
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}