package cz.budikpet.bachelorwork.util

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import com.google.api.services.calendar.model.Calendar
import com.google.api.services.calendar.model.CalendarListEntry

/**
 * Gets value of this float in dps.
 */
fun Float.toDp(context: Context): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    ).toInt()
}

/**
 * Makes editing of SharedPreferences easier.
 */
fun SharedPreferences.edit(editCode: (editor: SharedPreferences.Editor) -> Unit) {
    val editor = edit()
    editCode(editor)
    editor.apply()
}

/**
 * Make the entry hidden with a specific color.
 */
fun Calendar.createMyEntry(): CalendarListEntry {
    val entry = CalendarListEntry()
    entry.id = id
    entry.hidden = false
    entry.foregroundColor = "#000000"
    entry.backgroundColor = "#d3d3d3"

    return entry
}