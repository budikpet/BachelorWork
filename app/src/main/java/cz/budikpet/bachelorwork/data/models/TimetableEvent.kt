package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime

data class TimetableEvent(
    val id: Int,
    val room: String,
    val acronym: String,
    val fullName: String = acronym,
    val event_type: EventType?,
    val starts_at: DateTime,
    val ends_at: DateTime,
    val deleted: Boolean = false,
    val capacity: Int,
    val occupied: Int = 0,
    val teachers: ArrayList<String>,
    val students: ArrayList<String>? = null
)