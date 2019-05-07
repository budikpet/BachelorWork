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
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_edit.*
import org.joda.time.DateTime


class EventEditFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel
    private var selectedAutoTextView: AutoCompleteTextView? = null
    private var selectedTimeView: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        subscribeObservers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(cz.budikpet.bachelorwork.R.layout.fragment_event_edit, container, false)

        if (viewModel.eventToEditChanges == null) {
            Log.e(TAG, "viewModel.eventToEditChanges should not be null")
            return layout
        }

        layout.findViewById<EditText>(cz.budikpet.bachelorwork.R.id.editEventName)
            .setText(viewModel.eventToEditChanges!!.fullName, TextView.BufferType.EDITABLE)
        layout.findViewById<EditText>(cz.budikpet.bachelorwork.R.id.editEventAcronym)
            .setText(viewModel.eventToEditChanges!!.acronym, TextView.BufferType.EDITABLE)

        val spinnerEventType = layout.findViewById<Spinner>(cz.budikpet.bachelorwork.R.id.spinnerEventType)
        val eventTypes = getEventTypes()
        spinnerEventType.adapter =
            ArrayAdapter<String>(context!!, android.R.layout.simple_dropdown_item_1line, eventTypes)
        spinnerEventType.setSelection(eventTypes.indexOf(viewModel.eventToEditChanges!!.event_type.getLabel(context!!)))
        spinnerEventType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.eventToEditChanges!!.event_type = EventType.values()[position]
            }

        }

        val autoRoom = layout.findViewById<AutoCompleteTextView>(cz.budikpet.bachelorwork.R.id.autoEventRoom)
        autoRoom.setText(viewModel.eventToEditChanges!!.room, TextView.BufferType.EDITABLE)
        initAutoTextView(autoRoom) {
            viewModel.eventToEditChanges?.room = it.id
        }

        initTimeButtons(layout)

        val multiAutoTextView =
            layout.findViewById<MultiAutoCompleteTextView>(cz.budikpet.bachelorwork.R.id.teachersAutoComplete)
        multiAutoTextView.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer());
        initTeachersAutoTextView(multiAutoTextView) { clickedItem ->
            viewModel.eventToEditChanges?.teachers?.add(clickedItem.id)
        }

        return layout
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        val toolbar = eventEditToolbar
        inflater?.inflate(cz.budikpet.bachelorwork.R.menu.event_edit_bar, toolbar.menu)

        toolbar.setNavigationIcon(cz.budikpet.bachelorwork.R.drawable.ic_close_black_24dp)

        toolbar.setNavigationOnClickListener {
            // Edit cancelled
            viewModel.eventToEditChanges = null
            viewModel.eventToEdit.postValue(null)
        }

    }

    private fun initTeachersAutoTextView(autoTextView: AutoCompleteTextView, onItemClicked: (SearchItem) -> Unit) {
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
                val newText = query.toString()
                val parts = newText.split(",".toRegex())
                if (parts.isNotEmpty()) {
                    viewModel.searchSirius(parts.last())
                }
            }

        })
    }

    private fun initTimeButtons(layout: View) {
        var date = DateTime()

        val buttonDate = layout.findViewById<Button>(cz.budikpet.bachelorwork.R.id.buttonDate)
        val buttonTimeFrom = layout.findViewById<Button>(cz.budikpet.bachelorwork.R.id.buttonTimeFrom)
        val buttonTimeTo = layout.findViewById<Button>(cz.budikpet.bachelorwork.R.id.buttonTimeTo)

        buttonDate.text = viewModel.eventToEditChanges!!.starts_at.toString("dd.MM.YYYY")
        buttonTimeFrom.text = viewModel.eventToEditChanges!!.starts_at.toString("hh:mm")
        buttonTimeTo.text = viewModel.eventToEditChanges!!.ends_at.toString("hh:mm")

        buttonDate.setOnClickListener {
            val listener = DatePickerDialog.OnDateSetListener { datePicker, year, month, day ->
                date = date.withDate(year, month + 1, day)
                buttonDate.text = date.toString("dd.MM.YYYY")
                viewModel.eventToEditChanges!!.starts_at =
                    viewModel.eventToEditChanges!!.starts_at.withDate(year, month, day)
                viewModel.eventToEditChanges!!.ends_at =
                    viewModel.eventToEditChanges!!.ends_at.withDate(year, month, day)
            }
            DatePickerDialog(context!!, listener, date.year, date.monthOfYear - 1, date.dayOfMonth).show()
        }

        buttonTimeFrom.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                val time = date.withTime(hourOfDay, minute, 0, 0)
                val millisBetween =
                    viewModel.eventToEditChanges!!.ends_at.millisOfDay - viewModel.eventToEditChanges!!.starts_at.millisOfDay
                buttonTimeFrom.text = time.toString("hh:mm")
                viewModel.eventToEditChanges?.starts_at = time

                buttonTimeTo.text = time.plusMillis(millisBetween).toString("hh:mm")
                viewModel.eventToEditChanges?.ends_at = time.plusMillis(millisBetween)
            }
            TimePickerDialog(context!!, listener, 0, 0, true).show()
        }

        buttonTimeTo.setOnClickListener {
            val listener = TimePickerDialog.OnTimeSetListener { timePicker, hourOfDay, minute ->
                val time = date.withTime(hourOfDay, minute, 0, 0)
                buttonTimeTo.text = time.toString("hh:mm")
                viewModel.eventToEditChanges?.ends_at = time
            }
            TimePickerDialog(context!!, listener, 0, 0, true).show()
        }
    }

    private fun initAutoTextView(autoTextView: AutoCompleteTextView, onItemClicked: (SearchItem) -> Unit) {
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
                viewModel.searchSirius(query)
            }

        })
    }

    private fun subscribeObservers() {
        viewModel.searchItems.observe(this, Observer { searchItemsList ->
            val adapter = selectedAutoTextView?.adapter as AutoSuggestAdapter?
            if (adapter != null && searchItemsList != null) {
                val list = searchItemsList.map {
                    // Return title if it exists, else id
                    it.title ?: it.id
                }
                adapter.setData(list)
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

}
