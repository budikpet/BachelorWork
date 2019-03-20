package cz.budikpet.bachelorwork.data.models

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