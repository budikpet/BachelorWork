package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType

data class GoogleCalendarMetadata(
    var id: Int? = null,
    val teachers: ArrayList<String> = arrayListOf(),
    val students: ArrayList<String>? = null,
    val capacity: Int = 0,
    val occupied: Int = 0,
    val eventType: EventType = EventType.OTHER,
    val deleted: Boolean = false
)

data class CalendarListItem(val id: Long, val displayName: String, var syncEvents: Boolean) {
    fun with(syncEvents: Boolean): CalendarListItem {
        this.syncEvents = syncEvents
        return this
    }
}