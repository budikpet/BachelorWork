package cz.budikpet.bachelorwork.data.enums

import com.google.gson.annotations.SerializedName

enum class EventType(val nameString: String) {
    @SerializedName("assessment")   // Makes it possible to use the enum with Retrofit
    ASSESSMENT("Assessment"),
    @SerializedName("course_event")
    COURSE_EVENT("Course"),
    @SerializedName("exam")
    EXAM("Exam"),
    @SerializedName("laboratory")
    LABORATORY("Laboratory"),
    @SerializedName("lecture")
    LECTURE("Lecture"),
    @SerializedName("tutorial")
    TUTORIAL("Tutorial"),
    @SerializedName("teacher_timetable_slot")
    TEACHER_TIMETABLE_SLOT("Teacher time slot"),
    @SerializedName("other")
    OTHER("Other");

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

}