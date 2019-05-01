package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime
import org.joda.time.Interval
import java.util.*

data class TimetableEvent(
    val siriusId: Int? = null,
    val room: String? = null,      // Can be null
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
    val students: ArrayList<String>? = null,
    var color: Int = defaultColor(event_type)
) {
    var googleId: Long? = null

    companion object {
        fun from(event: Event): TimetableEvent {
            val room = when {
                event.links.room == null -> ""
                else -> event.links.room
            }

            return TimetableEvent(
                event.id, room = room, acronym = event.links.course, event_type = event.event_type,
                starts_at = DateTime(event.starts_at), ends_at = DateTime(event.ends_at), deleted = event.deleted,
                changed = hasEventChanged(event), capacity = event.capacity, occupied = event.occupied,
                teachers = event.links.teachers, students = event.links.students
            )
        }

        private fun hasEventChanged(event: Event): Boolean {
            return event.original_data.ends_at != null ||
                    event.original_data.starts_at != null ||
                    event.original_data.room_id != null
        }

        fun defaultColor(event_type: EventType): Int {
            return when (event_type) {
                EventType.LECTURE -> R.color.eventLecture
                EventType.EXAM -> R.color.eventExam
                EventType.OTHER -> R.color.eventOther
                EventType.TUTORIAL ->
                    R.color.eventTutorial
                else -> {
                    R.color.eventDefault
                }
            }
        }
    }

    fun overlapsWith(timetableEvent: TimetableEvent): Boolean {
        val interval1 = Interval(this.starts_at.millis, this.ends_at.millis)
        val interval2 = Interval(timetableEvent.starts_at.millis, timetableEvent.ends_at.millis)

        return interval1.overlaps(interval2)
    }

    fun compare(timetableEvent: TimetableEvent): Int {
        return when {
            starts_at.isBefore(timetableEvent.starts_at) -> -1
            starts_at.isAfter(timetableEvent.starts_at) -> 1
            else -> 0
        }
    }
}