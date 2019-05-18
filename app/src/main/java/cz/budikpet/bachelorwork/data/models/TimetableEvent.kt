package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.data.enums.EventType
import org.joda.time.DateTime
import org.joda.time.Interval
import java.util.*

/**
 * This model is used to hold all information about an event. Events from Sirius API and Google Calendar are transformed into it.
 */
data class TimetableEvent(
    val siriusId: Int? = null,
    val room: String? = null,      // Can be null
    var acronym: String = "",
    var fullName: String = "",
    val event_type: EventType = EventType.OTHER,
    val starts_at: DateTime = DateTime(),
    val ends_at: DateTime = starts_at,
    var deleted: Boolean = false,
    var capacity: Int = 0,
    var occupied: Int = 0,
    val teacherIds: ArrayList<String> = arrayListOf(),
    var color: Int = defaultColor(event_type)
) {
    var googleId: Long? = null
    val teachersNames: ArrayList<String> = arrayListOf()
    var note: String = ""

    /**
     * Indication that this event has been changed by the user.
     * If it is an event from Sirius, all further updates from Sirius API will be ignored.
     *
     */
    var changed: Boolean = false

    fun addTeacher(teacher: SearchItem) {
        teacherIds.add(teacher.id)
        if (teacher.title != null) {
            teachersNames.add(teacher.title)
        }
    }

    fun removeTeacher(teacher: SearchItem) {
        teacherIds.remove(teacher.id)
        if (teacher.title != null) {
            teachersNames.remove(teacher.title)
        }
    }

    fun overlapsWith(timetableEvent: TimetableEvent): Boolean {
        val interval1 = Interval(this.starts_at.millis, this.ends_at.millis)
        val interval2 = Interval(timetableEvent.starts_at.millis, timetableEvent.ends_at.millis)

        return interval1.overlaps(interval2)
    }

    fun deepCopy(
        event_type: EventType = this.event_type,
        room: String? = this.room,
        starts_at: DateTime = this.starts_at,
        ends_at: DateTime = this.ends_at
    ): TimetableEvent {
        val deepEvent = this.copy(
            event_type = event_type,
            room = room,
            starts_at = starts_at,
            ends_at = ends_at,
            teacherIds = arrayListOf()
        )
        deepEvent.googleId = this.googleId
        deepEvent.changed = this.changed
        deepEvent.teacherIds.addAll(this.teacherIds)
        deepEvent.teachersNames.addAll(this.teachersNames)
        deepEvent.note = this.note

        return deepEvent
    }

    fun fullEqual(other: TimetableEvent): Boolean {
        return this == other && googleId == other.googleId && teachersNames == other.teachersNames && note == other.note && changed == other.changed
    }

    companion object {
        fun from(event: Event): TimetableEvent {
            val room = when {
                event.links.room == null -> ""
                else -> event.links.room
            }

            val timetableEvent = TimetableEvent(
                event.id, room = room, acronym = event.links.course, event_type = event.event_type,
                starts_at = DateTime(event.starts_at), ends_at = DateTime(event.ends_at), deleted = event.deleted,
                capacity = event.capacity, occupied = event.occupied,
                teacherIds = event.links.teachers, fullName = event.links.course
            )

            return timetableEvent
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
                EventType.TUTORIAL -> R.color.eventTutorial
                EventType.LABORATORY -> R.color.eventLab
                EventType.ASSESSMENT -> R.color.eventExam
                else -> {
                    R.color.eventDefault
                }
            }
        }
    }
}