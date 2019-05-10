package cz.budikpet.bachelorwork.settings

import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.Menu
import android.view.MenuInflater
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import javax.inject.Inject


class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    @Inject
    internal lateinit var sharedPreferences: SharedPreferences

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

        setEmailListPreference()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(cz.budikpet.bachelorwork.R.string.sidebar_SavedCalendars)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setEmailListPreference() {
        val emailList = findPreference("prefEmails") as ListPreference

        emailList.entries = arrayOf<CharSequence>("Aoj", "do", "das")
        emailList.entryValues = arrayOf<CharSequence>("AojV", "doV", "dasV")

//        emailList.onPreferenceClickListener = Preference.OnPreferenceClickListener { false }
    }
}