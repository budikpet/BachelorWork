package com.elyeproj.wikisearchcount

object Model {
    data class Result(val meta: Meta, val events: List<Event>)
    data class Meta(val count: Int, val offset: Int, val limit: Int)

    // TODO: Add remaining values
    data class Event(val id: Int)
}