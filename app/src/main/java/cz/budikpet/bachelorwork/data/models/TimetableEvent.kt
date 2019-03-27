package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime
import java.util.*

data class TimetableEvent(
    val siriusId: Int?,
    var googleId: Long? = null,
    val room: String,
    val acronym: String,
    val fullName: String = acronym,
    val event_type: EventType,
    val starts_at: DateTime,
    val ends_at: DateTime,
    var deleted: Boolean = false,
    val changed: Boolean = false,
    val capacity: Int,
    val occupied: Int = 0,
    val teachers: ArrayList<String>,
    val students: ArrayList<String>? = null
) {
    companion object {
        fun from(event: Event): TimetableEvent {
            return TimetableEvent(
                event.id, room = event.links.room, acronym = event.links.course, event_type = event.event_type,
                starts_at = DateTime(event.starts_at), ends_at = DateTime(event.ends_at), deleted = event.deleted,
                changed = hasEventChanged(event), capacity = event.capacity, occupied = event.occupied,
                teachers = event.links.teachers, students = event.links.students
            )
        }

        private fun hasEventChanged(event: Event): Boolean {
            if(event.id == 521654319) { // TODO: Remove
                return true
            }

            return event.original_data.ends_at != null ||
                    event.original_data.starts_at != null ||
                    event.original_data.room_id != null
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(siriusId)
    }

    override fun equals(other: Any?): Boolean {
        if (other is TimetableEvent) {
            return siriusId == other.siriusId
        }

        return super.equals(other)
    }
}