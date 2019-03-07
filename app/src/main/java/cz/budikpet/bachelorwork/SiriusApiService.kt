package com.elyeproj.wikisearchcount

import cz.budikpet.bachelorwork.EventType
import cz.budikpet.bachelorwork.Model
import io.reactivex.Observable
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


interface SiriusApiService {

    /***
     * Get all events.
     */
    @GET("events")
    fun getEvents(
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 10,
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<Model.EventsResult>

    /**
     * Get events of a specific person.
     */
    @GET("people/{username}/events")
    fun getPersonEvents(
        @Path("username") username: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 10,
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<Model.EventsResult>

    /**
     * Get events of a specific room.
     */
    @GET("rooms/{kosId}/events")    // kosId = "TH:A-1231"
    fun getRoomEvents(
        @Path("kosId") roomKosId: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 10,
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<Model.EventsResult>

    /**
     * Get events of a specific course.
     */
    @GET("courses/{courseCode}/events")   // courseCode = BI-PA1
    fun getCourseEvents(
        @Path("courseCode") courseCode: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 10,
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<Model.EventsResult>

    /**
     * Get results of a search from a query.
     *
     * Used for autosuggestion when searching for timetables of classrooms, teachers, courses and other students.
     */
    @GET("search")
    fun search(@Query("access_token") accessToken: String,
               @Query("limit") limit: Int? = 10,    // <1; 1000>
               @Query("offset") offset: Int? = 0,
               @Query("q") query: String): Observable<Model.SearchResult>

    companion object {
        fun create(): SiriusApiService {

            // TODO: Add the header here using Interceptor from OkHttpClient
            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://sirius.fit.cvut.cz/api/v1/")
                .build()

            return retrofit.create(SiriusApiService::class.java)
        }
    }

}