package cz.budikpet.bachelorwork.screens.main

import android.accounts.AccountManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment.Companion.requiredPerms
import cz.budikpet.bachelorwork.screens.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject


/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), PermissionsCheckerFragment.Callback {
    private val TAG = "AMY_${this.javaClass.simpleName}"

    companion object {
        private const val CODE_GOOGLE_LOGIN = 0
    }

    private lateinit var mainActivityViewModel: MainActivityViewModel

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private lateinit var permissionsCheckerFragment: PermissionsCheckerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(cz.budikpet.bachelorwork.R.layout.activity_main)
        MyApplication.appComponent.inject(this)

        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()
            supportFragmentManager.beginTransaction()
                .add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
                .commit()
        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment
        }

        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)
        subscribeObservers()

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        mainActivityViewModel.checkSiriusAuthorization(response, exception)

        checkGoogleLogin()

        initButtons()

        // TODO: Fix - Called before being authorized. Need to be called one after the other.
        // Should get user list from Google Calendar, not Sirius API
        if (savedInstanceState == null) {
            // TODO: Get users name from somewhere
//            mainActivityViewModel.getSiriusEventsOf(ItemType.PERSON, "budikpet")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Need to test disposing
        mainActivityViewModel.onDestroy()
    }

    private fun initButtons() {
        disposeBtn.setOnClickListener {
            mainActivityViewModel.onDestroy()
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
            mainActivityViewModel.getSiriusEventsOf(itemType, providedId.text.toString())
        }

        signoutBtn.setOnClickListener {
            mainActivityViewModel.signOut()

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        }

        personRadioBtn.isChecked = true

        getCalendarsBtn.setOnClickListener {
            Log.i(TAG, "Selected account: ${credential.selectedAccount}")
            // TODO: Change
//            mainActivityViewModel.getGoogleCalendarList()
//            mainActivityViewModel.addSecondaryGoogleCalendar("T9:350_${MyApplication.calendarsName}")
//            mainActivityViewModel.getLocalCalendarList()
//            mainActivityViewModel.getGoogleCalendarEvents(3)
//            mainActivityViewModel.addGoogleCalendarEvent()
            mainActivityViewModel.updateAllCalendars()
//            mainActivityViewModel.sharePersonalCalendar("sgt.petrov@gmail.com")
//            mainActivityViewModel.unsharePersonalCalendar("sgt.petrov@gmail.com")
        }
    }

    private fun subscribeObservers() {
        mainActivityViewModel.getSiriusApiEvents().observe(this, Observer { eventsList ->
            if (eventsList != null) {
                Log.i(TAG, "Observing events from LiveData.")
                showString(eventsList)
            }
        })


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
            Log.i(TAG, "Has all required permissions. Checking Google login")

            if (sharedPreferences.contains(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString())) {
                Log.i(TAG, "Google account name is in SharedPreferences.")

                if (credential.selectedAccountName == null) {
                    // Add the selected google account into credential
                    credential.selectedAccountName =
                        sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), null)
                }

                // TODO: Check if the account still exists
            } else {
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
                val editor = sharedPreferences.edit()
                editor.putString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), accountName)
                editor.apply()
                credential.selectedAccountName = accountName

                mainActivityViewModel.updateAllCalendars()
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

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkGoogleLogin()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}
