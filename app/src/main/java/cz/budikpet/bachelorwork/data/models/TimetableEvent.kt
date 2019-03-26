package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime
import java.util.*

data class TimetableEvent(
    val siriusId: Int?,
    val room: String,
    val acronym: String,
    val fullName: String = acronym,
    val event_type: EventType,
    val starts_at: DateTime,
    val ends_at: DateTime,
    val deleted: Boolean = false,
    val changed: Boolean = false,
    val capacity: Int,
    val occupied: Int = 0,
    val teachers: ArrayList<String>,
    val students: ArrayList<String>? = null
) {
    companion object {
        fun from(event: Event): TimetableEvent {
            return TimetableEvent(event.id, event.links.room, event.links.course, event_type = event.event_type,
                starts_at = DateTime(event.starts_at), ends_at = DateTime(event.ends_at), deleted = event.deleted,
                changed = event.original_data != null, capacity = event.capacity, occupied = event.occupied,
                teachers = event.links.teachers, students = event.links.students)
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(siriusId)
    }

    override fun equals(other: Any?): Boolean {
        if(other is TimetableEvent) {
            return siriusId == other.siriusId
        }

        return super.equals(other)
    }
}