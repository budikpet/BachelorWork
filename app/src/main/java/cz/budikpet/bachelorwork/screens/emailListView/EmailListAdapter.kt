package cz.budikpet.bachelorwork.screens.emailListView

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cz.budikpet.bachelorwork.R
import kotlinx.android.synthetic.main.search_item_card.view.*

class EmailListAdapter(
    val context: Context,
    private val items: ArrayList<String> = arrayListOf()
) : RecyclerView.Adapter<EmailListAdapter.ViewHolder>() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    // Recently deleted item and position
    var recentlyDeletedItem: Pair<Int, String>? = null
        private set

    override fun getItemCount() = items.count()

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val email = items.elementAt(index)
        holder.email.text = email

        holder.itemView.tag = email
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val emailView = LayoutInflater.from(context).inflate(R.layout.email_card, parent, false)
        return ViewHolder(emailView)
    }

    fun clear() {
        this.items.clear()
        this.notifyDataSetChanged()
    }

    fun updateValues(items: List<String>) {
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
        val email: TextView = itemView.searchItemId
    }

}