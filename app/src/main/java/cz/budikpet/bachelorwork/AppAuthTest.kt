package cz.budikpet.bachelorwork

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_app_auth_test.*

class AppAuthTest : AppCompatActivity() {
    private val TAG = "MY_AppAuthTest"

    private lateinit var appAuthHandler: AppAuthHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_auth_test)

        appAuthHandler = AppAuthHandler(this)

        backBtn.setOnClickListener { signOut() }

        buttonRefresh.setOnClickListener { appAuthHandler.refreshAccessToken() }

        getEventsBtn.setOnClickListener {
            appAuthHandler.getEvents()
        }
    }

    override fun onStart() {
        super.onStart()

        if (appAuthHandler.isAuthorized()) {
            Log.i(TAG, "Already authorized.")
            return
        } else {
            Log.i(TAG, "Not authorized")
        }

        appAuthHandler.startAuthCodeExchange(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        appAuthHandler.close()
    }

    private fun signOut() {
        appAuthHandler.signOut()

        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }
}
