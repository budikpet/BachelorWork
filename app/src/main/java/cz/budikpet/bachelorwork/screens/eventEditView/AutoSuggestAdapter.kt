package cz.budikpet.bachelorwork.screens.eventEditView

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import cz.budikpet.bachelorwork.data.models.SearchItem


class AutoSuggestAdapter(context: Context, val resource: Int, val funFilter: ((SearchItem) -> Boolean)? = null) :
    ArrayAdapter<SearchItem>(context, resource) {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val items = arrayListOf<SearchItem>()

    private val inflater: LayoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun getCount(): Int {
        return items.count()
    }

    override fun getItem(position: Int): SearchItem? {
        return items[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView as TextView?
        val item = items[position]

        if (convertView == null) {
            view = inflater.inflate(resource, parent, false) as TextView
        }

        view?.text = item.toString()

        return view!!
    }

    fun setData(list: List<SearchItem>) {
        items.clear()

        when {
            funFilter != null -> items.addAll(list.filter { funFilter.invoke(it) })
            else -> items.addAll(list)
        }

        this.notifyDataSetChanged()
    }
}