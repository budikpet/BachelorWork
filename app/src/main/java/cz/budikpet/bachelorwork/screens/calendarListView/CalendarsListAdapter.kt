package cz.budikpet.bachelorwork.screens.calendarListView

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem
import kotlinx.android.synthetic.main.search_item_card.view.*

class CalendarsListAdapter(
    val context: Context,
    private val items: ArrayList<SearchItem> = arrayListOf(),
    val onItemClickFunction: (SearchItem) -> (Unit)
) : RecyclerView.Adapter<CalendarsListAdapter.ViewHolder>() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    // Recently deleted item and position
    var recentlyDeletedItem: Pair<Int, SearchItem>? = null
        private set

    private val listener = View.OnClickListener {
        onItemClickFunction(it.tag as SearchItem)
    }

    override fun getItemCount() = items.count()

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val searchItem = items.elementAt(index)
        holder.id.text = searchItem.id
        holder.title.text = searchItem.title
        setImage(searchItem, holder)

        holder.itemView.setOnClickListener(listener)
        holder.itemView.tag = searchItem

        if (searchItem.title == null || searchItem.title == "") {
            holder.id.visibility = View.GONE
            holder.title.text = searchItem.id
        } else {
            holder.id.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val searchItemView =
            LayoutInflater.from(context).inflate(cz.budikpet.bachelorwork.R.layout.search_item_card, parent, false)
        searchItemView.setOnClickListener(listener)
        return ViewHolder(searchItemView)
    }

    private fun setImage(searchItem: SearchItem, holder: ViewHolder) {
        val itemId = when (searchItem.type) {
            ItemType.ROOM -> cz.budikpet.bachelorwork.R.drawable.ic_place_black_24dp
            ItemType.PERSON -> cz.budikpet.bachelorwork.R.drawable.ic_person_black_24dp
            ItemType.COURSE -> cz.budikpet.bachelorwork.R.drawable.ic_class_black_24dp
            ItemType.UNKNOWN -> android.R.drawable.screen_background_light_transparent
        }

        holder.image.setImageResource(itemId)
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

    fun removeItem(position: Int) {
        recentlyDeletedItem = Pair(position, items[position])
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun undoDelete() {
        val position = recentlyDeletedItem?.first
        val item = recentlyDeletedItem?.second

        if (position != null && item != null) {
            items.add(position, item)
            notifyItemInserted(position)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val id: TextView = itemView.searchItemId
        val title: TextView = itemView.searchItemTitle
        val image: ImageView = itemView.itemImage
    }

}