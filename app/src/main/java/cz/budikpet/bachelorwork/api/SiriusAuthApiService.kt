package cz.budikpet.bachelorwork.api

import cz.budikpet.bachelorwork.data.models.AuthUserInfo
import io.reactivex.Observable
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface SiriusAuthApiService {

    /***
     * Get info about currently logged in user.
     */
    @GET("userinfo")
    fun getUserInfo(@Query("access_token") accessToken: String): Observable<AuthUserInfo>

    companion object {
        fun create(): SiriusAuthApiService {
            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://auth.fit.cvut.cz/oauth/")
                .build()

            return retrofit.create(SiriusAuthApiService::class.java)
        }
    }

}