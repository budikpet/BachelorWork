package cz.budikpet.bachelorwork

import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.AuthorizationRequest
import android.content.Intent
import android.app.PendingIntent
import android.util.Log
import net.openid.appauth.AuthorizationService

class MainActivity : AppCompatActivity() {
    private val TAG = "MY_MainActivity"
    private lateinit var appAuthHandler: AppAuthHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appAuthHandler = AppAuthHandler(this)

        if (appAuthHandler.isAuthorized()) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, AppAuthTest::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        appAuthPendingIntent.setOnClickListener {
            appAuthHandler.authorize()
        }

    }
}
