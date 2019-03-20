package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime

data class Event(
    val id: Int,
    val name: EventName? = null,
    val starts_at: DateTime,
    val ends_at: DateTime,
    val deleted: Boolean = false,
    val capacity: Int,
    val occupied: Int = 0,
    val event_type: EventType?,
    val original_data: OriginalData? = null,
    val links: Links
)

data class EventName(val cs: String? = null)
data class OriginalData(
    val starts_at: DateTime,
    val ends_at: DateTime,
    val room_id: String
)

data class Links(
    val room: String,
    val course: String,
    val teachers: List<String>,
    val students: List<String>? = null
)