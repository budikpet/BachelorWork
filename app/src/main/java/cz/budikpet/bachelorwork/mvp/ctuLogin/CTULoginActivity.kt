package cz.budikpet.bachelorwork.mvp.ctuLogin

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.mvp.main.MainActivity
import cz.budikpet.bachelorwork.util.AppAuthHolder
import javax.inject.Inject

/**
 * The activity checks whether a user is already authorized (logged in).
 *
 * If he is logged in, the app goes to @MainActivity activity immediately.
 *
 * If he isn't logged in, the app starts an authorization flow that moves the user to the browser to log in.
 */
class CTULoginActivity : AppCompatActivity() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    internal lateinit var appAuthHolder: AppAuthHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

        // Use refreshToken to skip authorization

        if (appAuthHolder.isAuthorized()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
            startAuthorization()
        }

//        setContentView(R.layout.activity_ctu_login)
    }

    override fun onDestroy() {
        super.onDestroy()
        appAuthHolder.close()
    }

    // MARK: User authorization

    /**
     * Starts the authorization flow.
     *
     * A user is redirected to CTU login page to provide username and password.
     */
    private fun startAuthorization() {
        var errorIntent = Intent(this, CTULoginActivity::class.java)
        errorIntent.putExtra("TEST", "error")

        appAuthHolder.authService.performAuthorizationRequest(
            appAuthHolder.authRequest,
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0),
            PendingIntent.getActivity(this, 0, errorIntent, 0)
        )
    }
}
