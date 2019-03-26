package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.ItemType

// SearchItem type
data class SearchItem(
    val id: String,         // acronym
    val title: String?,
    val type: ItemType
)