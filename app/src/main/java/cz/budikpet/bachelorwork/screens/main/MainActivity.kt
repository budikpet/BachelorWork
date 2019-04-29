package cz.budikpet.bachelorwork.screens.main

import android.accounts.AccountManager
import android.app.SearchManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
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
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.edit
import cz.budikpet.bachelorwork.util.inTransaction
import cz.budikpet.bachelorwork.util.toDp
import io.reactivex.Observable
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
    private lateinit var searchSuggestions: RecyclerView

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

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
        subscribeObservers()

        // Check logins
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        viewModel.checkSiriusAuthorization(response, exception)

        checkGoogleLogin()

        searchSuggestions = this.findViewById(R.id.searchSuggestions)
        searchSuggestions.layoutManager = LinearLayoutManager(this)
        searchSuggestions.addItemDecoration(MarginItemDecoration(4.toDp(this)))
        searchSuggestions.adapter = SearchSuggestionsAdapter(this)

//        initButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Need to test disposing
        viewModel.onDestroy()
    }

    private fun subscribeObservers() {
        viewModel.searchItems.observe(this, Observer { searchItems ->
            if (searchItems != null) {
                if (searchItems.isNotEmpty()) {
                    val adapter = searchSuggestions.adapter as SearchSuggestionsAdapter
                    adapter.updateValues(searchItems)
                }
            }
        })
    }

    private fun initButtons() {
        disposeBtn.setOnClickListener {
            viewModel.onDestroy()
        }

        getEventsBtn.setOnClickListener {
            val itemType = when {
                personRadioBtn.isChecked -> {
                    ItemType.PERSON
                }
                courseRadioBtn.isChecked -> {
                    ItemType.COURSE
                }
                else -> {
                    ItemType.ROOM
                }
            }
            viewModel.getSiriusEventsOf(itemType, providedId.text.toString())
        }

        signoutBtn.setOnClickListener {
            viewModel.signOut()

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        }

        personRadioBtn.isChecked = true

        getCalendarsBtn.setOnClickListener {
            Log.i(TAG, "Selected account: ${credential.selectedAccount}")
            // TODO: Change
//            viewModel.getGoogleCalendarList()
//            viewModel.addSecondaryGoogleCalendar("T9:350_${MyApplication.calendarsName}")
//            viewModel.getLocalCalendarList()
//            viewModel.getCalendarEvents(3)
//            viewModel.addGoogleCalendarEvent()
            viewModel.updateAllCalendars()
//            viewModel.sharePersonalCalendar("sgt.petrov@gmail.com")
//            viewModel.unsharePersonalCalendar("sgt.petrov@gmail.com")
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

        val searchMenuItem = menu?.findItem(R.id.app_bar_menu_search)
        val searchView = searchMenuItem?.actionView as SearchView
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.isSubmitButtonEnabled = true

        val adapter = searchSuggestions.adapter as SearchSuggestionsAdapter

        // Watch for user input
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String?): Boolean {
                if (query != null && query.count() >= 2) {
                    viewModel.searchSirius(query)
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.i(TAG, "Submitted: <$query>")
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
                searchSuggestions.visibility = View.GONE
                return true
            }

        })

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == R.id.itemSync) {
            Log.i(TAG, "Selected account: ${credential.selectedAccount}")
            viewModel.updateAllCalendars()
//            test()

            return true
        }


        return false
    }

    private fun test() {
        val retryMax: Long = 5
        var retries: Long = 0
        var retries2: Long = 0

        val test = Observable.create<Int> {
            for (i in 0..10) {
                if (i == 4 && retries <= retryMax - 2) {
                    Log.i(TAG, "onError")
                    it.onError(NotImplementedError("onError1"))
                    break
                } else if (i == 5) {
                    Log.i(TAG, "throw")
//                    throw NotOwnerException()
                } else it.onNext(i)

            }

            it.onComplete()
        }
            .retry { t1, t2 ->
                Log.i(TAG, "Retry count: $retries")
                retries += 1
                retries <= retryMax + 1
            }
            .collect({ mutableListOf<Int>() }, { list, value ->
                list.add(value)
                Log.i(TAG, "Added $value, list has ${list.count()} values.")
            })
            .map { it.distinct() }
            .flatMapObservable { list ->
                Observable.create<Int> {
                    for (i in list) {
                        if (i == 4 && retries2 <= retryMax - 2) {
                            Log.i(TAG, "onError2")
                            it.onError(NotImplementedError("onError2"))
                            break
                        } else if (i == 5) {
                            Log.i(TAG, "throw")
//                            throw NotOwnerException()
                        } else it.onNext(i)

                    }

                    it.onComplete()
                }
            }
            .retry { t1, t2 ->
                Log.i(TAG, "Retry2 count: $retries2")
                retries2 += 1
                retries2 <= retryMax + 6
            }
            .subscribe(
                { result ->
                    Log.i(TAG, "Result: $result")
                },
                { error -> Log.e(TAG, "Chain ended with error: ${error}") }
            )
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}

class MarginItemDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        with(outRect) {
            if (parent.getChildAdapterPosition(view) == 0) {
                top = spaceHeight
            }
            left = spaceHeight
            right = spaceHeight
            bottom = spaceHeight
        }
    }
}