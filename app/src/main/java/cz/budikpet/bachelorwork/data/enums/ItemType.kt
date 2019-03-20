package cz.budikpet.bachelorwork.data.enums

import com.google.gson.annotations.SerializedName

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