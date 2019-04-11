package cz.budikpet.bachelorwork.screens.ctuLogin

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.screens.PermissionsCheckerFragment
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import net.openid.appauth.AuthorizationService
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

    // TODO: Do StartAuthorization better
    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    private lateinit var permissionsCheckerFragment: PermissionsCheckerFragment
    private var onResumeCalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)
        onResumeCalled = false

//        setContentView(R.layout.activity_ctu_login)

        if (savedInstanceState == null) {
            permissionsCheckerFragment = PermissionsCheckerFragment()
            supportFragmentManager.beginTransaction()
                .add(permissionsCheckerFragment, PermissionsCheckerFragment.BASE_TAG)
                .commit()
        } else {
            permissionsCheckerFragment =
                supportFragmentManager.findFragmentByTag(PermissionsCheckerFragment.BASE_TAG) as PermissionsCheckerFragment
        }

    }

    override fun onResume() {
        super.onResume()

        // TODO: Find better way?
        // Asking for permissions starts onResume more than once. This makes it run only once
        if (onResumeCalled) {
            return
        } else {
            onResumeCalled = true
        }

        Log.i(TAG, "OnResume")

        // TODO: Catch cancelled Sirius login

        if (sharedPreferences.contains(SharedPreferencesKeys.FIRST_RUN.toString())) {
            // The application was already started at least once
            permissionsCheckerFragment.checkPermissions()
        } else {
            val editor = sharedPreferences.edit()
            editor.putBoolean(SharedPreferencesKeys.FIRST_RUN.toString(), false)
            editor.apply()

            AlertDialog.Builder(this)
                .setTitle("Notice")
                .setMessage(
                    "The application uses CTU timetable and Google Calendar. " +
                            "The user has to be logged into a CTU account and a Google account."
                )
                .setPositiveButton("Continue") { dialog, id ->
                    permissionsCheckerFragment.checkPermissions()
                }
                .setNegativeButton("Quit") { dialog, id ->
                    finishAffinity()
                }
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "OnPause")
    }

    override fun onDestroy() {
        super.onDestroy()
//        appAuthManager.close()
    }

    // MARK: User authorization

    private fun checkAuthorization() {
        if (appAuthManager.isAuthorized()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
            startAuthorization()
        }
    }

    /**
     * Starts the authorization flow.
     *
     * A user is redirected to CTU login page to provide username and password.
     */
    private fun startAuthorization() {
        val authService = AuthorizationService(this) // TODO: No way to fix this better?

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
