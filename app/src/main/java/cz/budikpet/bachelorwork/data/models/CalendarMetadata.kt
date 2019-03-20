package cz.budikpet.bachelorwork.data.models

import kotlin.collections.ArrayList

data class CalendarMetadata(val id: Int,
                            val teachers: ArrayList<String>,
                            val students: ArrayList<String>? = null)