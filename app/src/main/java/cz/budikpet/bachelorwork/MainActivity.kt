package cz.budikpet.bachelorwork

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

interface MainActivityInterface {
    fun onSignOutClick()
    fun onRefreshClick()
    fun onGetEventsClick()
    fun showString(string: String)
}

/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), MainActivityInterface {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var mainActivityPresenter: MainActivityPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appAuthHolder = AppAuthHolder(this)
        mainActivityPresenter = MainActivityPresenter(this, appAuthHolder, MainActivityModel(appAuthHolder))

        backBtn.setOnClickListener { onSignOutClick() }
        buttonRefresh.setOnClickListener { onRefreshClick() }
        getEventsBtn.setOnClickListener { onGetEventsClick() }
    }

    override fun onStart() {
        super.onStart()

        mainActivityPresenter.checkAuthorization(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivityPresenter.onDestroy()
    }

    // MARK: interface

    override fun onSignOutClick() {
        mainActivityPresenter.signOut()

        val mainIntent = Intent(this, CTULoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    override fun onRefreshClick() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onGetEventsClick() {
        mainActivityPresenter.getEvents()
    }

    override fun showString(string: String) {
        showData.text = string
    }
}
