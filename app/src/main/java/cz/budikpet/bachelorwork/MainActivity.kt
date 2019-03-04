package cz.budikpet.bachelorwork

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

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

    override fun onDestroy() {
        super.onDestroy()

        appAuthHandler.close()
    }
}
