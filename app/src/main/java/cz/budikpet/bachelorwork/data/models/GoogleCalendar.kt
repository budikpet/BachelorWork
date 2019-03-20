package cz.budikpet.bachelorwork.data.models

data class GoogleCalendarMetadata(
    val id: Int,
    val teachers: ArrayList<String>,
    val students: ArrayList<String>? = null
)

data class GoogleCalendarListItem(val id: String, val displayName: String)