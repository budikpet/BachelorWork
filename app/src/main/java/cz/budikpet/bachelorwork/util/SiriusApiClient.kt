package cz.budikpet.bachelorwork.util

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class SiriusApiClient @Inject constructor() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val events = MutableLiveData<List<Model.Event>>()

    @Inject
    internal lateinit var siriusApiService: SiriusApiService
    private var disposable: Disposable? = null

    init {
        MyApplication.appComponent.inject(this)
    }

    /**
     * @return The observable variable which holds events to be currently displayed.
     */
    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return events
    }

    /**
     * Calls events endpoint using a non-expired accessToken.
     */
    fun searchSiriusApiEvents(accessToken: String, itemType: ItemType, id: String) {
        // Prepare the endpoint call
        disposable = getEventsObservable(accessToken, itemType, id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    //                    Log.i(TAG, "Events_AccessToken: $result")
                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error}") }
            )

    }

    /**
     * AccessToken expired. Refreshes the accessToken and then calls the events endpoint using a new accessToken.
     *
     * @param refreshObservable an observable which holds the refreshAccessToken operation. Returns a new accessToken.
     */
    fun searchSiriusApiEvents(refreshObservable: Single<String>, itemType: ItemType, id: String) {
        // Prepare the endpoint call
        disposable = refreshObservable.toObservable()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io()) // Observe the refreshAccessToken operation on a non-main thread.
            .flatMap { accessToken ->
                // Create the eventsObservable using the received accessToken
                getEventsObservable(accessToken, itemType, id)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    //                    Log.i(TAG, "Events_Observable: $result")
                    events.postValue(result.events)
                },
                { error ->
                    Log.e(TAG, "Error: ${error}")
                }
            )

    }

    /**
     * Picks which events endpoint to call.
     *
     * @param itemType the type of endpoint we need to call.
     * @param id either persons name (budikpet), course ID (BI-AND) or room ID (T9-350)
     *
     * @return Observable SiriusApi endpoint data.
     */
    private fun getEventsObservable(
        accessToken: String,
        itemType: ItemType,
        id: String
    ): Observable<Model.EventsResult> {
        return when (itemType) {
            ItemType.COURSE -> siriusApiService.getCourseEvents(accessToken = accessToken, id = id)
            ItemType.PERSON -> siriusApiService.getPersonEvents(accessToken = accessToken, id = id, from = "2019-3-2")
            ItemType.ROOM -> siriusApiService.getRoomEvents(accessToken = accessToken, id = id)
        }
    }
}