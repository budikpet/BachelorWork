package cz.budikpet.bachelorwork.dataModel

import com.google.gson.annotations.SerializedName
import java.util.*

enum class EventType() {
    @SerializedName("assessment")   // Makes it possible to use the enum with Retrofit
    ASSESSMENT,
    @SerializedName("course_event")
    COURSE_EVENT,
    @SerializedName("exam")
    EXAM,
    @SerializedName("laboratory")
    LABORATORY,
    @SerializedName("lecture")
    LECTURE,
    @SerializedName("tutorial")
    TUTORIAL,
    @SerializedName("teacher_timetable_slot")
    TEACHER_TIMETABLE_SLOT;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

}

enum class ItemType() {
    @SerializedName("course")
    COURSE,
    @SerializedName("room")
    ROOM,
    @SerializedName("person")
    PERSON;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

}

object Model {
    /**
     * Response of a Sirius API endpoints "*\events" which return a list of events and a meta header.
     */
    data class EventsResult(val meta: Meta, val events: List<Event>)

    /**
     * Response of a Sirius API endpoint "\search" which returns a list of items that match criteria of users search.
     */
    data class SearchResult(val meta: Meta, val results: List<SearchItem>)

    /**
     * A special item used by Sirius API endpoints which return a collection of data.
     * It holds information about this collection.
     */
    data class Meta(val count: Int, val offset: Int, val limit: Int)

    // SearchItem type
    data class SearchItem(
        val id: String,
        val title: String?,
        val type: ItemType
    )

    // TODO: Add remaining values
    // Event type
    data class Event(
        val id: Int,
        val name: EventName? = null,
        val starts_at: Date,
        val ends_at: Date,
        val deleted: Boolean = false,
        val capacity: Int,
        val occupied: Int = 0,
        val event_type: EventType?,
        val original_data: OriginalData,
        val links: Links
    )

    data class EventName(val cs: String? = null)
    data class OriginalData(
        val starts_at: Date,
        val ends_at: Date,
        val room_id: String
    )

    data class Links(
        val room: String,
        val course: String,
        val teachers: List<String>,
        val students: List<String>
    )
}