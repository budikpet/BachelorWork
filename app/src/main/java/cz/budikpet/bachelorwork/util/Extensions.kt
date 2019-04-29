package cz.budikpet.bachelorwork.util

import android.content.Context
import android.content.SharedPreferences
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
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
 * Gets value of this int in dps.
 */
fun Int.toDp(context: Context): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        context.resources.displayMetrics
    ).toInt()
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

/**
 * Makes using FragmentManager easier.
 */
inline fun FragmentManager.inTransaction(func: FragmentTransaction.() -> FragmentTransaction) {
    beginTransaction().func().commitNow()
}

/**
 * Makes editing of SharedPreferences easier.
 */
internal inline fun SharedPreferences.edit(func: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
    this.edit().func().apply()
}