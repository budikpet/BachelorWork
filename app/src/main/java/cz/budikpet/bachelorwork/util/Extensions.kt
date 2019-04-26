package cz.budikpet.bachelorwork.util

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue

fun Float.toDp(context: Context): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    ).toInt()
}

fun SharedPreferences.edit(editCode: (editor: SharedPreferences.Editor) -> Unit) {
    val editor = edit()
    editCode(editor)
    editor.apply()
}
