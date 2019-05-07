package cz.budikpet.bachelorwork.screens.eventEditView

import android.content.Context
import android.widget.ArrayAdapter
import cz.budikpet.bachelorwork.data.models.SearchItem
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import cz.budikpet.bachelorwork.R


class AutoSuggestAdapter(context: Context, val resource: Int) : ArrayAdapter<SearchItem>(context, resource) {
    private val items = arrayListOf<SearchItem>()

    private val inflater: LayoutInflater by lazy {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    fun setData(list: List<SearchItem>) {
        items.clear()
        items.addAll(list)
        this.notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.count()
    }

    override fun getItem(position: Int): SearchItem? {
        return items[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // TODO: Custom view + holder
        var view = convertView as TextView?
        val item = items[position]

        if(convertView == null) {
            view = TextView(context)
        }

        view?.text = item.title

        return view!!
    }
}