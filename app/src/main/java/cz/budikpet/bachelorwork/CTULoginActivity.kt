package cz.budikpet.bachelorwork

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

/**
 * The activity checks whether a user is already authorized (logged in).
 *
 * If he is logged in, the app goes to @MainActivity activity immediately.
 *
 * If he isn't logged in, the app starts an authorization flow that moves the user to the browser to log in.
 */
class CTULoginActivity : AppCompatActivity() {
    private val TAG = "MY_MainActivity"
    private lateinit var appAuthHandler: AppAuthHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appAuthHandler = AppAuthHandler(this)

        // Use refreshToken to skip authorization
//        if(!appAuthHandler.isAuthorized() && appAuthHandler.) {
//
//        }

        if (appAuthHandler.isAuthorized()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
            appAuthHandler.startAuthorization()
        }

//        setContentView(R.layout.activity_ctu_login)
    }

    override fun onDestroy() {
        super.onDestroy()

        appAuthHandler.close()
    }
}
