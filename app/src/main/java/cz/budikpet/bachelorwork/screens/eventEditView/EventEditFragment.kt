package cz.budikpet.bachelorwork.screens.eventEditView

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.tokenautocomplete.TokenCompleteTextView
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.data.models.TimetableEvent
import cz.budikpet.bachelorwork.di.util.MyViewModelFactory
import cz.budikpet.bachelorwork.screens.TokenCompletionView
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_edit.*
import org.joda.time.LocalDate
import javax.inject.Inject

// TODO: Disable edit if the timetable is not saved

class EventEditFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    @Inject
    lateinit var viewModelFactory: MyViewModelFactory

    private lateinit var viewModel: MainViewModel
    private var selectedAutoTextView: AutoCompleteTextView? = null

    private var selectedEvent: TimetableEvent? = null
        set(value) {
            field = value
            viewModel.eventToEditChanges = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        MyApplication.appComponent.inject(this)

        viewModel = activity?.run {
            ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        selectedEvent = viewModel.eventToEditChanges

        subscribeObservers()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val toolbar = eventEditToolbar
        inflater?.inflate(R.menu.event_edit_bar, toolbar.menu)

        toolbar.setNavigationIcon(R.drawable.ic_close_black_24dp)

        toolbar.setOnMenuItemClickListener { menuItem ->
            menuItemClicked(menuItem)
        }

        toolbar.setNavigationOnClickListener {
            // Edit cancelled
            prepareExit()
            viewModel.eventToEditChanges = null
            viewModel.eventToEdit.postValue(null)
        }

    }

    private fun menuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.itemSaveEvent) {
            prepareExit()

            when {
                selectedEvent!!.fullName.count() <= 0 -> editEventName.error = getString(R.string.error_fieldBlank)
                selectedEvent!!.acronym.count() <= 0 -> editEventAcronym.error = getString(R.string.error_fieldBlank)
                else -> viewModel.addCalendarEvent(selectedEvent!!)

            }
            return true
        }

        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_event_edit, container, false)

        if (selectedEvent == null) {
            Log.e(TAG, "selectedEvent should not be null")
            return layout
        }

        initEditTexts(layout)

        val spinnerEventType = layout.findViewById<Spinner>(R.id.spinnerEventType)
        val eventTypes = getEventTypes()
        spinnerEventType.adapter =
            ArrayAdapter<String>(context!!, android.R.layout.simple_dropdown_item_1line, eventTypes)
        spinnerEventType.setSelection(eventTypes.indexOf(selectedEvent!!.event_type.getLabel(context!!)))
        spinnerEventType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedEvent = selectedEvent?.deepCopy(event_type = EventType.values()[position])
            }

        }

        val autoRoom = layout.findViewById<AutoCompleteTextView>(R.id.autoEventRoom)
        autoRoom.setText(selectedEvent!!.room, TextView.BufferType.EDITABLE)
        initAutoTextView(autoRoom, ItemType.ROOM) {
            selectedEvent = selectedEvent?.deepCopy(room = it.id)
        }

        initTimeButtons(layout)

        val teachersTokenAuto = layout.findViewById<TokenCompletionView>(R.id.teachersTokenAuto)
        teachersTokenAuto.allowCollapse(false)
        initTeachersAutoTextView(teachersTokenAuto, ItemType.PERSON)

        return layout
    }

    private fun initEditTexts(layout: View) {
        val editEventAcronym = layout.findViewById<EditText>(R.id.editEventAcronym)
        editEventAcronym.setText(selectedEvent!!.acronym, TextView.BufferType.EDITABLE)
        editEventAcronym.setOnFocusChangeListener { editText, hasFocus ->
            if (!hasFocus) {
                val text = (editText as EditText).text.toString()
                selectedEvent!!.acronym = text
            }
        }

        val editEventName = layout.findViewById<EditText>(R.id.editEventName)
        editEventName.setText(selectedEvent!!.fullName, TextView.BufferType.EDITABLE)
        editEventName.setOnFocusChangeListener { editText, hasFocus ->
            if (!hasFocus) {
                val text = (editText as EditText).text.toString()
                selectedEvent!!.fullName = text

                if (editEventAcronym.text.count() <= 0) {
                    editEventAcronym.setText(text)
                    selectedEvent!!.acronym = text
                }
            }
        }

        val editEventNote = layout.findViewById<EditText>(R.id.editEventNote)
        editEventNote.setText(selectedEvent!!.note, TextView.BufferType.EDITABLE)
        editEventNote.setOnFocusChangeListener { editText, hasFocus ->
            if (!hasFocus) {
                val text = (editText as EditText).text.toString()
                selectedEvent!!.note = text
            }
        }
    }

    private fun initTeachersAutoTextView(teachersTokenAuto: TokenCompletionView, itemType: ItemType) {
        teachersTokenAuto.setAdapter(AutoSuggestAdapter(context!!, android.R.layout.simple_list_item_1) {
            // Filter out items that were already selected
            !selectedEvent!!.teacherIds.contains(it.id)
        })
        teachersTokenAuto.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                selectedAutoTextView = teachersTokenAuto
            }

            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = query.toString()
                val parts = newText.split(",".toRegex())
                if (parts.isNotEmpty()) {
                    viewModel.searchSirius(parts.last(), itemType)
                }
            }

        })

        teachersTokenAuto.setTokenListener(object : TokenCompleteTextView.TokenListener<SearchItem> {
            override fun onTokenIgnored(token: SearchItem?) {}

            override fun onTokenAdded(token: SearchItem?) {
                val token = token ?: return
                if (!selectedEvent!!.teacherIds.contains(token.id)) {
                    selectedEvent?.addTeacher(token)
                }
            }

            override fun onTokenRemoved(token: SearchItem?) {
                val token = token ?: return
                selectedEvent?.removeTeacher(token)
            }
        })

        // Add teacher names user already picked
        selectedEvent!!.teacherIds.forEachIndexed { i, id ->
            val name = selectedEvent!!.teachersNames.elementAtOrNull(i)
            teachersTokenAuto.addObjectAsync(SearchItem(id, name, ItemType.PERSON))
        }
    }

    private fun initAutoTextView(
        autoTextView: AutoCompleteTextView,
        itemType: ItemType,
        onItemClicked: (SearchItem) -> Unit
    ) {
        autoTextView.setAdapter(AutoSuggestAdapter(context!!, android.R.layout.simple_list_item_1))

        autoTextView.setOnItemClickListener { parent, view, position, id ->
            hideSoftKeyboard(view)

            val selectedItem = viewModel.searchItems.value?.elementAt(position) ?: return@setOnItemClickListener
            onItemClicked(selectedItem)
        }

        autoTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                selectedAutoTextView = autoTextView
            }

            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                val query = query.toString()
                viewModel.searchSirius(query, itemType)
            }

        })
    }

    private fun initTimeButtons(layout: View) {
        val buttonDate = layout.findViewById<Button>(R.id.buttonDate)
        val buttonTimeFrom = layout.findViewById<Button>(R.id.buttonTimeFrom)
        val buttonTimeTo = layout.findViewById<Button>(R.id.buttonTimeTo)

        buttonDate.text = selectedEvent!!.starts_at.toString("dd.MM.YYYY")
        buttonTimeFrom.text = selectedEvent!!.starts_at.toString("HH:mm")
        buttonTimeTo.text = selectedEvent!!.ends_at.toString("HH:mm")

        buttonDate.setOnClickListener {
            val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, day ->
                val date = LocalDate(year, month + 1, day)
                buttonDate.text = date.toString("dd.MM.YYYY")
                val startsAt = selectedEvent!!.starts_at.withDate(date)
                selectedEvent = selectedEvent?.deepCopy(starts_at = startsAt)

                val endsAt = selectedEvent!!.ends_at.withDate(year, month, day)
                selectedEvent = selectedEvent?.deepCopy(ends_at = endsAt)
            }
            val date = LocalDate()
            DatePickerDialog(context!!, listener, date.year, date.monthOfYear - 1, date.dayOfMonth).show()
        }

        buttonTimeFrom.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                val time = selectedEvent!!.starts_at.withTime(hourOfDay, minute, 0, 0)
                val millisBetween =
                    selectedEvent!!.ends_at.millisOfDay - selectedEvent!!.starts_at.millisOfDay
                buttonTimeFrom.text = time.toString("HH:mm")

                selectedEvent = selectedEvent?.deepCopy(starts_at = time)

                buttonTimeTo.text = time.plusMillis(millisBetween).toString("HH:mm")
                selectedEvent = selectedEvent?.deepCopy(ends_at = time.plusMillis(millisBetween))
            }
            TimePickerDialog(context!!, listener, 0, 0, true).show()
        }

        buttonTimeTo.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                val time = selectedEvent!!.ends_at.withTime(hourOfDay, minute, 0, 0)
                buttonTimeTo.text = time.toString("HH:mm")
                selectedEvent = selectedEvent?.deepCopy(ends_at = time)
            }
            TimePickerDialog(context!!, listener, 0, 0, true).show()
        }
    }

    private fun subscribeObservers() {
        viewModel.searchItems.observe(this, Observer { searchItemsList ->
            val adapter = (selectedAutoTextView?.adapter as AutoSuggestAdapter?) ?: return@Observer
            if (searchItemsList != null) {
                adapter.setData(searchItemsList)
            }
        })
    }

    private fun getEventTypes(): List<String> {
        return EventType.values()
            .map { it.getLabel(context!!) }
    }

    private fun hideSoftKeyboard(view: View) {
        val imm = context!!.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.applicationWindowToken, 0)
    }

    private fun prepareExit() {
        editEventAcronym.clearFocus()
        editEventName.clearFocus()
        editEventNote.clearFocus()
        hideSoftKeyboard(eventEditLayout)

        viewModel.searchItems.postValue(listOf())
    }
}
