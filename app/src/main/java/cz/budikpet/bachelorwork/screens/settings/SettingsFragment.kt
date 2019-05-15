package cz.budikpet.bachelorwork.screens.settings

import android.app.TimePickerDialog
import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceFragmentCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.widget.EditText
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.di.util.MyViewModelFactory
import cz.budikpet.bachelorwork.screens.main.MainActivity
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import cz.budikpet.bachelorwork.util.SharedPreferencesKeys
import cz.budikpet.bachelorwork.util.edit
import org.joda.time.LocalTime
import javax.inject.Inject

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    lateinit var viewModelFactory: MyViewModelFactory

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
            ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        setHasOptionsMenu(true)

        setLogoutButtons()

        setLessonButtons()

        val modileData = findPreference(SharedPreferencesKeys.USE_MOBILE_DATA.toString())
        val useMobData = sharedPreferences.getBoolean(SharedPreferencesKeys.USE_MOBILE_DATA.toString(), false)
        modileData.summary = when (useMobData) {
            true -> getString(R.string.prefSum_mobileData_true)
            else -> getString(R.string.prefSum_mobileData_false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = (activity as AppCompatActivity).supportActionBar
        supportActionBar?.title = getString(cz.budikpet.bachelorwork.R.string.sidebar_SavedCalendars)
        supportActionBar?.subtitle = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun setLessonButtons() {
        val context = context ?: return

        val numOfLessons = findPreference(SharedPreferencesKeys.NUM_OF_LESSONS.toString())
        val lessonStartTime = findPreference(SharedPreferencesKeys.LESSONS_START_TIME.toString())
        val lessonLength = findPreference(SharedPreferencesKeys.LENGTH_OF_LESSON.toString())
        val breakLength = findPreference(SharedPreferencesKeys.LENGTH_OF_BREAK.toString())

        // NumOfLessons
        numOfLessons.setOnPreferenceClickListener {
            val number = sharedPreferences.getInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 6)
            showNumberEditDialog(getString(R.string.alertDialog_title_numOfLessons), number, 1, 20) {
                sharedPreferences.edit {
                    putInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), it)
                }
            }
            return@setOnPreferenceClickListener true
        }
        var value = sharedPreferences.getInt(SharedPreferencesKeys.NUM_OF_LESSONS.toString(), 0)
        numOfLessons.summary = getString(R.string.prefSum_numOfLessons).format(value)

        // Lesson length
        lessonLength.setOnPreferenceClickListener {
            val number = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 6)
            showNumberEditDialog(getString(R.string.alertDialog_title_lessonLength), number, 30, 300) {
                sharedPreferences.edit {
                    putInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), it)
                }
            }
            return@setOnPreferenceClickListener true
        }
        value = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_LESSON.toString(), 0)
        lessonLength.summary = getString(R.string.prefSum_lessonLength).format(value)

        // Break length
        breakLength.setOnPreferenceClickListener {
            val number = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 6)
            showNumberEditDialog(getString(R.string.alertDialog_title_breakLength), number, 0, 150) {
                sharedPreferences.edit {
                    putInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), it)
                }
            }
            return@setOnPreferenceClickListener true
        }
        value = sharedPreferences.getInt(SharedPreferencesKeys.LENGTH_OF_BREAK.toString(), 0)
        breakLength.summary = getString(R.string.prefSum_breakLength).format(value)

        // Lesson start
        val millisOfDay = sharedPreferences.getInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), 0)
        val time = LocalTime().withMillisOfDay(millisOfDay)
        lessonStartTime.summary = time.toString("HH:mm")
        lessonStartTime.setOnPreferenceClickListener {
            showTimeEditDialog(time) {
                sharedPreferences.edit {
                    putInt(SharedPreferencesKeys.LESSONS_START_TIME.toString(), it.millisOfDay)
                }
            }
            return@setOnPreferenceClickListener true
        }
    }

    private fun setLogoutButtons() {
        val context = context ?: return
//
//        val ctuLogout = findPreference("prefSiriusLogout")
//        ctuLogout.setOnPreferenceClickListener {
//            AlertDialog.Builder(context)
//                .setTitle(context.getString(R.string.alertDialog_title_notice))
//                .setMessage(getString(R.string.alertDialog_message_ctuLogOut))
//                .setPositiveButton(context.getString(R.string.alertDialog_positive_yes)) { dialog, which ->
//                    viewModel.ctuLogOut()
//                }
//                .setNegativeButton(context.getString(R.string.alertDialog_negative_no)) { dialog, which -> }
//                .show()
//
//            return@setOnPreferenceClickListener true
//        }
//        val siriusAccount = sharedPreferences.getString(SharedPreferencesKeys.CTU_USERNAME.toString(), "")
//        ctuLogout.summary = getString(R.string.prefSum_Logout).format(siriusAccount)

        val googleLogout = findPreference("prefGoogleLogout")
        googleLogout.setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.alertDialog_title_notice))
                .setMessage(getString(R.string.alertDialog_message_googleLogout))
                .setPositiveButton(context.getString(R.string.alertDialog_positive_yes)) { dialog, which ->
                    activity?.startActivityForResult(credential.newChooseAccountIntent(), MainActivity.CODE_GOOGLE_LOGIN)
                }
                .setNegativeButton(context.getString(R.string.alertDialog_negative_no)) { dialog, which -> }
                .show()

            return@setOnPreferenceClickListener true
        }
        val googleAccount = sharedPreferences.getString(SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString(), "")
        googleLogout.summary = getString(R.string.prefSum_Logout).format(googleAccount)
    }

    private fun showTimeEditDialog(time: LocalTime, onPositiveFunction: (LocalTime) -> Unit) {
        val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
            val time = LocalTime(hourOfDay, minute)
            onPositiveFunction(time)
        }

        TimePickerDialog(context!!, listener, time.hourOfDay, time.minuteOfHour, true).show()
    }

    private fun showNumberEditDialog(
        title: String,
        number: Int,
        min: Int,
        max: Int,
        onPositiveFunction: (Int) -> Unit
    ) {
        val numberEditView = layoutInflater.inflate(R.layout.dialog_set_number, null)
        val editText = numberEditView.findViewById<EditText>(R.id.numberEditText)
        editText.setText("$number")

        val dialog = AlertDialog.Builder(context!!)
            .setView(numberEditView)
            .setTitle(title)
            .setPositiveButton(R.string.alertDialog_positive_yes) { dialog, which ->
                onPositiveFunction(editText.text.toString().toInt())
            }
            .setNegativeButton(R.string.alertDialog_negative_no) { dialog, which -> }
            .show()

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val number = when {
                    count <= 0 -> 0
                    else -> s.toString().toInt()
                }

                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                when {
                    number < min -> {
                        editText.error = getString(R.string.error_belowMin).format(min)
                        positiveButton.isEnabled = false
                    }
                    number > max -> {
                        editText.error = getString(R.string.error_aboveMax).format(max)
                        positiveButton.isEnabled = false
                    }
                    else -> positiveButton.isEnabled = true
                }
            }

        })
    }

    // MARK: Listener

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Settings change, value in sharedPreferences was already updated
        if (key == null || sharedPreferences == null) {
            Log.e(TAG, "Key and sharedPreferences should not be null.")
            return
        }

        when (key) {
            SharedPreferencesKeys.USE_MOBILE_DATA.toString() -> {
                val preference = findPreference(key)
                val value = sharedPreferences.getBoolean(key, false)

                val summaryRes =
                    when (value) {
                        true -> R.string.prefSum_mobileData_true
                        else -> R.string.prefSum_mobileData_false
                    }
                preference.setSummary(summaryRes)
            }
            SharedPreferencesKeys.NUM_OF_LESSONS.toString() -> {
                val preference = findPreference(key)
                val value = sharedPreferences.getInt(key, 0)
                preference.summary = getString(R.string.prefSum_numOfLessons).format(value)
            }
            SharedPreferencesKeys.LENGTH_OF_LESSON.toString() -> {
                val preference = findPreference(key)
                val value = sharedPreferences.getInt(key, 0)
                preference.summary = getString(R.string.prefSum_lessonLength).format(value)
            }
            SharedPreferencesKeys.LENGTH_OF_BREAK.toString() -> {
                val preference = findPreference(key)
                val value = sharedPreferences.getInt(key, 0)
                preference.summary = getString(R.string.prefSum_breakLength).format(value)
            }
            SharedPreferencesKeys.LESSONS_START_TIME.toString() -> {
                val preference = findPreference(key)
                val millisOfDay = sharedPreferences.getInt(key, 0)
                val time = LocalTime().withMillisOfDay(millisOfDay)
                preference.summary = time.toString("HH:mm")
            }
            SharedPreferencesKeys.CTU_USERNAME.toString() -> {
                val preference = findPreference(key)
                val googleAccount = sharedPreferences.getString(key, "")
                preference.summary = getString(R.string.prefSum_Logout).format(googleAccount)
            }
            SharedPreferencesKeys.GOOGLE_ACCOUNT_NAME.toString() -> {
                val preference = findPreference(key)
                val googleAccount = sharedPreferences.getString(key, "")
                preference.summary = getString(R.string.prefSum_Logout).format(googleAccount)
            }
        }
    }
}