package com.elyeproj.wikisearchcount

import io.reactivex.Observable
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient
import java.io.IOException


interface SiriusApiService {

    @GET("events")
    fun getEvents(
        @Query("access_token") accessToken: String,
        @Query("limit") limit: Int? = 10,
        @Query("offset") offset: Int? = 0,
        @Query("include") include: String? = null,
        @Query("event_type") event_type: String? = null,
        @Query("deleted") deleted: Boolean = false,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("with_original_date") withOriginalDate: Boolean = false
    ): Observable<Model.Result>

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