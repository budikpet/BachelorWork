package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType

data class GoogleCalendarMetadata(
    val id: Int?,
    val teachers: ArrayList<String>,
    val students: ArrayList<String>? = null,
    val capacity: Int,
    val occupied: Int = 0,
    val eventType: EventType,
    val deleted: Boolean = false
)

data class GoogleCalendarListItem(val id: Int, val displayName: String)