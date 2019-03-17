package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.RadioGroup
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var mainActivityViewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        mainActivityViewModel.checkAuthorization(response, exception)

        initButtons()
        subscribeObservers()

        // TODO: Fix - Called before being authorized. Need to be called one after the other.
        // Should get user list from Google Calendar, not Sirius API
        if (savedInstanceState == null) {
            // TODO: Get users name from somewhere
//            mainActivityViewModel.searchSiriusApiEvents(ItemType.PERSON, "budikpet")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivityViewModel.onDestroy()
    }

    private fun initButtons() {
        getEventsBtn.setOnClickListener {
            val itemType = when {
                personBtn.isChecked -> {
                    ItemType.PERSON
                }
                courseBtn.isChecked -> {
                    ItemType.COURSE
                }
                else -> {
                    ItemType.ROOM
                }
            }
            mainActivityViewModel.searchSiriusApiEvents(itemType, providedId.text.toString())
        }

        signoutBtn.setOnClickListener {
            mainActivityViewModel.signOut()

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        }

        personBtn.isChecked = true
    }

    private fun subscribeObservers() {
        mainActivityViewModel.getSiriusApiEvents().observe(this, Observer { eventsList ->
            if (eventsList != null) {
                Log.i(TAG, "Observing events from LiveData.")
                showString(eventsList)
            }
        })
    }

    // MARK: @MainActivityView implementations

    fun showString(result: List<Model.Event>) {
        var builder = StringBuilder()
        for (event in result) {
            builder.append("${event.links.course} ${event.event_type}: ${event.starts_at}\n")
        }

        showData.text = builder.toString()
    }
}
