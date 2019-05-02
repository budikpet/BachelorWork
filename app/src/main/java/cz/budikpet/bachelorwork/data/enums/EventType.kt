package cz.budikpet.bachelorwork.data.enums

import android.content.Context
import com.google.gson.annotations.SerializedName

enum class EventType() {
    @SerializedName("assessment")   // Makes it possible to use the enum with Retrofit
    ASSESSMENT(),
    @SerializedName("course_event")
    COURSE_EVENT(),
    @SerializedName("exam")
    EXAM(),
    @SerializedName("laboratory")
    LABORATORY(),
    @SerializedName("lecture")
    LECTURE(),
    @SerializedName("tutorial")
    TUTORIAL(),
    @SerializedName("teacher_timetable_slot")
    TEACHER_TIMETABLE_SLOT(),
    @SerializedName("other")
    OTHER();

    /**
     * Returns a localized label used to represent this enumeration value.  If no label
     * has been defined, then this defaults to the result of [Enum.name].
     *
     *
     * The name of the string resource for the label must match the name of the enumeration
     * value.  For example, for enum value 'ENUM1' the resource would be defined as 'R.string.eventType_Enum1'.
     *
     * @param context   the context that the string resource of the label is in.
     * @return      a localized label for the enum value or the result of name()
     */
    fun getLabel(context: Context): String {
        val res = context.resources
        val resId =
            res.getIdentifier("eventType_${this.name.toLowerCase().capitalize()}", "string", context.packageName)
        return when {
            0 != resId -> res.getString(resId)
            else -> name
        }
    }

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

}