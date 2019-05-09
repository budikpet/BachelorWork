package cz.budikpet.bachelorwork.screens

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tokenautocomplete.TokenCompleteTextView
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.ItemType
import cz.budikpet.bachelorwork.data.models.SearchItem

class TokenCompletionView(context: Context, attrs: AttributeSet) :
    TokenCompleteTextView<SearchItem>(context, attrs) {

    init {
        threshold = 2
    }

    override fun getViewForObject(searchItem: SearchItem): View {

        val inflater = context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view =
            inflater.inflate(R.layout.contact_token, parent as ViewGroup, false) as TextView
        view.text = searchItem.title

        return view
    }

    override fun defaultObject(completionText: String): SearchItem {
        return SearchItem("", type = ItemType.UNKNOWN)
    }

    override fun shouldIgnoreToken(token: SearchItem): Boolean {
        return objects.contains(token)
    }
}