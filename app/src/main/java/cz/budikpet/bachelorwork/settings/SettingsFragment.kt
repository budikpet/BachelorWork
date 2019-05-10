package cz.budikpet.bachelorwork.settings

import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.Menu
import android.view.MenuInflater
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import javax.inject.Inject


class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

    @Inject
    internal lateinit var credential: GoogleAccountCredential

    override fun onCreatePreferences(p0: Bundle?, p1: String?) {
        addPreferencesFromResource(cz.budikpet.bachelorwork.R.xml.preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApplication.appComponent.inject(this)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        setHasOptionsMenu(true)

        setLogoutButtons()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(cz.budikpet.bachelorwork.R.string.sidebar_SavedCalendars)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setLogoutButtons() {
        val context = context ?: return

        val ctuLogout = findPreference("prefSiriusLogout")
        ctuLogout.setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.alertDialog_title_notice))
                .setMessage(getString(R.string.alertDialog_message_ctuLogOut))
                .setPositiveButton(context.getString(R.string.alertDialog_positive_yes)) { dialog, which ->
                    viewModel.ctuLogOut()
                }
                .setNegativeButton(context.getString(R.string.alertDialog_negative_no)) { dialog, which -> }
                .show()

            return@setOnPreferenceClickListener true
        }

        val googleLogout = findPreference("prefGoogleLogout")
        googleLogout.setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.alertDialog_title_notice))
                .setMessage(getString(R.string.alertDialog_message_googleLogout))
                .setPositiveButton(context.getString(R.string.alertDialog_positive_yes)) { dialog, which ->
                    startActivityForResult(credential.newChooseAccountIntent(), MainActivity.CODE_GOOGLE_LOGIN)
                }
                .setNegativeButton(context.getString(R.string.alertDialog_negative_no)) { dialog, which -> }
                .show()

            return@setOnPreferenceClickListener true
        }
    }
}