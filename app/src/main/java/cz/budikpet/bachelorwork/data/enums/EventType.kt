package cz.budikpet.bachelorwork.data.enums

import com.google.gson.annotations.SerializedName

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
    TEACHER_TIMETABLE_SLOT,
    @SerializedName("other")
    OTHER;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

}