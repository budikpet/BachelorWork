package cz.budikpet.bachelorwork.data.models

import cz.budikpet.bachelorwork.data.enums.ItemType

// SearchItem type
data class SearchItem(
    /** Acronym - T9:350, balikm, BI-AG2 */
    val id: String,
    /** Name - null, Miroslav Balík, Algoritmy a grafy 2 */
    val title: String? = null,
    val type: ItemType
)