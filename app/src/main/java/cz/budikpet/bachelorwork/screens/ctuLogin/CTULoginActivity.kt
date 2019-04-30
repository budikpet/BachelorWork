package cz.budikpet.bachelorwork.screens.ctuLogin

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.edit
import cz.budikpet.bachelorwork.util.inTransaction
import net.openid.appauth.AuthorizationService
import org.joda.time.DateTime
import javax.inject.Inject

/**
 * The activity checks whether a user is already authorized (logged in).
 *
 * If he is logged in, the app goes to @MainActivity activity immediately.
 *
 * If he isn't logged in, the app starts an authorization flow that moves the user to the browser to log in.
 */
class CTULoginActivity : AppCompatActivity(), PermissionsCheckerFragment.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private lateinit var permissionsCheckerFragment: PermissionsCheckerFragment
    private var onResumeCalled = false

    private val alertDialogBuilder: AlertDialog.Builder by lazy {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alertDialog_title_firstNotice))
            .setMessage(
                getString(R.string.alertDialog_message_firstNotice)
            )
            .setPositiveButton(getString(R.string.alertDialog_positive_firstNotice)) { dialog, id ->
                permissionsCheckerFragment.checkPermissions()
            }
            .setNegativeButton(getString(R.string.alertDialog_quit)) { dialog, id ->
                finishAffinity()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)
        onResumeCalled = false

//        setContentView(R.layout.activity_ctu_login)

        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()
            supportFragmentManager.inTransaction {
                add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
            }
        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment
        }

        if (sharedPreferences.contains(SharedPreferencesKeys.FIRST_RUN.toString())) {
            // The application was already started at least once
            permissionsCheckerFragment.checkPermissions()
        } else {
            setPreferences()
            alertDialogBuilder.show()
        }

    }

    private fun setPreferences() {
        // Prepare needed preferences for the MultidayViewFragment
        val lessonsStartTime = DateTime().withTime(7, 30, 0, 0).millisOfDay

        sharedPreferences.edit {
            putBoolean(SharedPreferencesKeys.FIRST_RUN.toString(), false)
            putInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 8)
            putInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), lessonsStartTime)
            putInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 15)
            putInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 90)
        }

    }

    // MARK: User authorization

    private fun checkAuthorization() {
        if (appAuthManager.isAuthorized()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
            Log.i(TAG, "User is needs to be authorized in Sirius API.")
            startAuthorization()
        }
    }

    /**
     * Starts the authorization flow.
     *
     * A user is redirected to CTU login page to provide username and password.
     */
    private fun startAuthorization() {
        val authService = AuthorizationService(this)

        val cancelIntent = Intent(this, CTULoginActivity::class.java)
        cancelIntent.putExtra("isCancelIntent", true)

        authService.performAuthorizationRequest(
            appAuthManager.authRequest,
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0),
            PendingIntent.getActivity(this, 0, cancelIntent, 0)
        )
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkAuthorization()
    }

    override fun quitApplication() {
        finishAffinity()
    }
}
