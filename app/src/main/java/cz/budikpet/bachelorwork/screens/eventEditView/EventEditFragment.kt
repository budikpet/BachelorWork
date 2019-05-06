package cz.budikpet.bachelorwork.screens.eventEditView

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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.models.SearchItem
import cz.budikpet.bachelorwork.screens.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_event_edit.*


class EventEditFragment : Fragment() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private lateinit var viewModel: MainViewModel

    private var selectedAutoTextView: AutoCompleteTextView? = null

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

        val spinnerEventType = layout.findViewById<Spinner>(cz.budikpet.bachelorwork.R.id.spinnerEventType)
        spinnerEventType.adapter =
            ArrayAdapter<String>(context!!, android.R.layout.simple_spinner_dropdown_item, getEventTypes())

        val autoRoom = layout.findViewById<AutoCompleteTextView>(cz.budikpet.bachelorwork.R.id.autoEventRoom)
        initAutoTextView(autoRoom) {
            viewModel.editedEventChanges?.room = it.id
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
            viewModel.editedEventChanges = null
            viewModel.eventToEdit.postValue(null)
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
                Log.i(TAG, "Query: $query")
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
