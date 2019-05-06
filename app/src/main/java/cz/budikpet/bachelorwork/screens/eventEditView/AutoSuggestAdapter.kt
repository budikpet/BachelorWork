package cz.budikpet.bachelorwork.screens.eventEditView

import android.content.Context
import android.widget.ArrayAdapter

class AutoSuggestAdapter(context: Context, val resource: Int) : ArrayAdapter<String>(context, resource) {
    private val items = arrayListOf<String>()

    fun setData(list: List<String>) {
        items.clear()
        items.addAll(list)
        this.notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.count()
    }

    override fun getItem(position: Int): String? {
        return items[position]
    }
}