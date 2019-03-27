package cz.budikpet.bachelorwork.api

import cz.budikpet.bachelorwork.data.enums.EventType
import cz.budikpet.bachelorwork.data.models.EventsResult
import cz.budikpet.bachelorwork.data.models.SearchResult
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
        @Query("limit") limit: Int? = 100,    // <1; 1000>
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<EventsResult>

    /**
     * Get events of a specific person.
     */
    @GET("people/{username}/events")        // username = budikpet (case-sensitive)
    fun getPersonEvents(
        @Path("username") id: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 100,    // <1; 1000>
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<EventsResult>

    /**
     * Get events of a specific room.
     */
    @GET("rooms/{kosId}/events")    // kosId = "TH:A-1231" (case-sensitive)
    fun getRoomEvents(
        @Path("kosId") id: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 100,    // <1; 1000>
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<EventsResult>

    /**
     * Get events of a specific course.
     */
    @GET("courses/{courseCode}/events")   // courseCode = BI-PA1 (case-sensitive)
    fun getCourseEvents(
        @Path("courseCode") id: String,
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 100,    // <1; 1000>
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: EventType? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,        // ex. 2018-3-13
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<EventsResult>

    /**
     * Get results of a search from a query.
     *
     * Used for autosuggestion when searching for timetables of classrooms, teachers, courses and other students.
     */
    @GET("search")
    fun search(
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 100,    // <1; 1000>
        @Query("offset") offset: Int? = 0,
        @Query("q") query: String
    ): Observable<SearchResult>

    companion object {
        fun create(): SiriusApiService {
            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://sirius.fit.cvut.cz/api/v1/")
                .build()

            return retrofit.create(SiriusApiService::class.java)
        }
    }

}