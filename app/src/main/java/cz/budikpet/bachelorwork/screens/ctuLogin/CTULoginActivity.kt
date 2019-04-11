package cz.budikpet.bachelorwork.screens.ctuLogin

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.util.AppAuthManager
import cz.budikpet.bachelorwork.util.PermissionsHandler
import cz.budikpet.bachelorwork.util.PermissionsHandler.Companion.requiredPerms
import net.openid.appauth.AuthorizationService
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject

/**
 * The activity checks whether a user is already authorized (logged in).
 *
 * If he is logged in, the app goes to @MainActivity activity immediately.
 *
 * If he isn't logged in, the app starts an authorization flow that moves the user to the browser to log in.
 */
class CTULoginActivity : AppCompatActivity(), PermissionsHandler.Callback {
    private val TAG = "MY_${this.javaClass.simpleName}"

    // TODO: Do StartAuthorization better
    @Inject
    internal lateinit var appAuthManager: AppAuthManager

    val permissionsHandler = PermissionsHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

//        setContentView(R.layout.activity_ctu_login)

        AlertDialog.Builder(this)
            .setTitle("Notice")
            .setMessage(
                "The application uses CTU timetable and Google Calendar. " +
                        "The user has to be logged into a CTU account and a Google account."
            )
            .setPositiveButton("Continue") { dialog, id ->
                permissionsHandler.checkPermissions()
            }
            .setNegativeButton("Quit") { dialog, id ->
                finishAffinity()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
//        appAuthManager.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "OnActivityResult")

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // The user was asked to go to settings to grant permissions
            Log.i(TAG, "The user returned from settings dialog.")
            if (EasyPermissions.hasPermissions(this, *requiredPerms)) {
                checkAuthorization()
            } else {
                finishAffinity()
            }
        }
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
        authService.performAuthorizationRequest(
            appAuthManager.authRequest,
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0),
            PendingIntent.getActivity(this, 0, Intent(this, CTULoginActivity::class.java), 0)
        )
    }

    // MARK: Permissions

    override fun onAllPermissionsGranted() {
        checkAuthorization()
    }

    override fun getActivity(): Activity {
        return this
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, permissionsHandler);
    }
}
