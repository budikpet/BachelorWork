package cz.budikpet.bachelorwork.mvp.main

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.dataModel.ItemType
import cz.budikpet.bachelorwork.mvp.ctuLogin.CTULoginActivity
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

/**
 * The first screen a user sees after logging into CTU from @CTULoginActivity.
 */
class MainActivity : AppCompatActivity(), MainContract.View {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var mainActivityPresenter: MainContract.Presenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainActivityPresenter = MainActivityPresenter(this, MainActivityModel())

        initButtons()
    }

    override fun onStart() {
        super.onStart()

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        mainActivityPresenter.checkAuthorization(response, exception)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivityPresenter.onDestroy()
    }

    private fun initButtons() {
        buttonRefresh.setOnClickListener { TODO("not implemented") }
        getEventsBtn.setOnClickListener { mainActivityPresenter.getSiriusApiEvents(ItemType.COURSE, "MI-IOS") }

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
