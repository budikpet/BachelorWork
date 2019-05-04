package cz.budikpet.bachelorwork.screens.main

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import kotlinx.android.synthetic.main.search_item_card.view.*

class SearchSuggestionsAdapter(
    val context: Context,
    private val items: ArrayList<SearchItem> = arrayListOf(),
    val onItemClickFunction: (SearchItem) -> (Unit)
) :
    RecyclerView.Adapter<SearchSuggestionsAdapter.ViewHolder>() {

    private val listener = View.OnClickListener {
        onItemClickFunction(it.tag as SearchItem)
    }

    override fun getItemCount() = items.count()

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val searchItem = items.elementAt(index)
        holder.id.text = searchItem.id
        holder.title.text = searchItem.title

        holder.parentView.setOnClickListener(listener)
        holder.parentView.tag = searchItem

        if(searchItem.title == null || searchItem.title == "") {
            holder.id.visibility = View.GONE
            holder.title.text = searchItem.id
        } else {
            holder.id.visibility = View.VISIBLE
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val searchItemView = LayoutInflater.from(context).inflate(R.layout.search_item_card, parent, false)
        searchItemView.setOnClickListener(listener)
        return ViewHolder(searchItemView)
    }

    fun clear() {
        this.items.clear()
        this.notifyDataSetChanged()
    }

    fun updateValues(items: List<SearchItem>) {
        this.items.clear()
        this.items.addAll(items)
        this.notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parentView = itemView
        val id: TextView = itemView.searchItemId2
        val title: TextView = itemView.searchItemTitle2
    }

}