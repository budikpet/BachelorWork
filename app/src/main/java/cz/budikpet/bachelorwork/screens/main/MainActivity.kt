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
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.util.*
import kotlinx.android.synthetic.main.activity_main_old.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MyApplication.appComponent.inject(this)

        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()
            multidayFragmentHolder = MultidayFragmentHolder()

            supportFragmentManager.inTransaction {
                add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
                add(R.id.fragmentHolder, multidayFragmentHolder)
            }

        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment

            multidayFragmentHolder =
                supportFragmentManager.findFragmentById(R.id.fragmentHolder) as MultidayFragmentHolder
        }

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

        viewModel.title.observe(this, Observer { title ->
            supportActionBar?.title = title
        })

        viewModel.timetableOwner.observe(this, Observer { pair ->
            val username = pair?.first
            if (username != null && username != "") {
                supportActionBar?.subtitle = username
            }
        })

        viewModel.lastAllCalendarsUpdate.observe(this, Observer {
            val pair = viewModel.timetableOwner.value

            if (pair != null) {
                val username = pair.first
                val itemType = pair.second
                viewModel.loadEvents(username, itemType)
            }
        })
    }

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

                viewModel.updateAllCalendars()
            } else {
                Log.i(TAG, "Google account not specified.")

                AlertDialog.Builder(this)
                    .setTitle("Google Account was not specified")
                    .setMessage("Google Account is needed.")
                    .setPositiveButton("Log in") { dialog, id ->
                        checkGoogleLogin()
                    }
                    .setNegativeButton("Quit") { dialog, id ->
                        quitApplication()
                    }
                    .show()

            }

        }
    }

    // MARK: Tab Bar

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_bar, menu)

        val searchMenuItem = menu?.findItem(R.id.app_bar_menu_search)!!
        val searchView = searchMenuItem.actionView as SearchView
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.isSubmitButtonEnabled = true

        // Init searchSuggestions RecyclerView adapter
        val adapter = SearchSuggestionsAdapter(this, onItemClickFunction = {
            searchMenuItem.collapseActionView()
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
        if (item!!.itemId == R.id.itemSync) {
            Log.i(TAG, "Selected account: ${credential.selectedAccount}")
            viewModel.updateAllCalendars()

            return true
        }


        return false
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}