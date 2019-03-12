package cz.budikpet.bachelorwork.mvp.main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import kotlinx.android.synthetic.main.activity_main.*

interface MainActivityView {
    fun showString(string: String)
}

/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), MainActivityView {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var mainActivityPresenter: MainActivityPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainActivityPresenter = MainActivityPresenter(this, MainActivityModel())

        initButtons()
    }

    override fun onStart() {
        super.onStart()

        mainActivityPresenter.checkAuthorization(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivityPresenter.onDestroy()
    }

    fun initButtons() {
        buttonRefresh.setOnClickListener { TODO("not implemented") }
        getEventsBtn.setOnClickListener { mainActivityPresenter.getEvents() }

        backBtn.setOnClickListener {
            mainActivityPresenter.signOut()

            val mainIntent = Intent(this, CTULoginActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(mainIntent)
            finish()
        }
    }

    // MARK: @MainActivityView implementations

    override fun showString(string: String) {
        showData.text = string
    }
}
