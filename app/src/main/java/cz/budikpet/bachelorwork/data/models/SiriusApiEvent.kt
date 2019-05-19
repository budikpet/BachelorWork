package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType
import java.util.*

data class Event(
    val id: Int,
    val name: EventName? = null,
    val starts_at: Date = Date(),
    val ends_at: Date = Date(),
    val deleted: Boolean = false,
    val capacity: Int = 0,
    val occupied: Int = 0,
    val event_type: EventType = EventType.OTHER,
    val original_data: OriginalData = OriginalData(null, null, null),
    val links: Links
)

data class EventName(val cs: String? = null)
data class OriginalData(
    val starts_at: Date?,
    val ends_at: Date?,
    val room_id: String?
)

data class Links(
    val room: String? = null,
    val course: String,
    val teachers: ArrayList<String> = arrayListOf(),
    val students: ArrayList<String>? = null
)