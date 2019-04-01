package cz.budikpet.bachelorwork.mvp.main

import android.Manifest
import android.accounts.AccountManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.Event
import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject


/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val CODE_GOOGLE_LOGIN = 0
    private val CODE_REQUEST_PERMISSIONS = 1

    private lateinit var mainActivityViewModel: MainActivityViewModel

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MyApplication.appComponent.inject(this)
        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        mainActivityViewModel.checkAuthorization(response, exception)

        checkGoogleLogin()

        initButtons()
        subscribeObservers()

        // TODO: Fix - Called before being authorized. Need to be called one after the other.
        // Should get user list from Google Calendar, not Sirius API
        if (savedInstanceState == null) {
            // TODO: Get users name from somewhere
//            mainActivityViewModel.getSiriusEventsOf(ItemType.PERSON, "budikpet")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Needs to be disposed
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
//            mainActivityViewModel.addSecondaryGoogleCalendar("budikpet_${MyApplication.calendarsName}")
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
        if (sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), null) == null) {
            // Ask a user to log into a Google account once after he logged into CTU
            // TODO: Enable
//            if(!sharedPreferences.getBoolean(SharedPreferencesKeys.GOOGLE_LOGIN_CHECKED.toString(), false)) {
            // Ask a user to log into a Google account
            Log.i(TAG, "Logging into Google")
            checkPermissions()

            val editor = sharedPreferences.edit()
            editor.putBoolean(SharedPreferencesKeys.GOOGLE_LOGIN_CHECKED.toString(), true)
            editor.apply()
//            }
        }
    }

    private fun checkPermissions() {
        val array: Array<String> = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_SYNC_STATS
        )

        requestPermissions(
            array,
            CODE_REQUEST_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "RequestPermsResult")

        if (requestCode == CODE_REQUEST_PERMISSIONS) {
            if (!grantResults.contains(-1)) {
                startActivityForResult(credential.newChooseAccountIntent(), CODE_GOOGLE_LOGIN)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "OnActivityResult")

        if (requestCode == CODE_GOOGLE_LOGIN) {
            // Store google account name
            val accountName = data?.extras!!.getString(AccountManager.KEY_ACCOUNT_NAME)
            val editor = sharedPreferences.edit()
            editor.putString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), accountName)
            editor.apply()
            credential.selectedAccountName = accountName

            mainActivityViewModel.updateAllCalendars()
        }
    }
}
