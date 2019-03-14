package cz.budikpet.bachelorwork.util

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
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
     * Return currently selected events
     */
    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return events
    }

    fun searchSiriusApiEvents(accessToken: String, itemType: ItemType, id: String) {
        // Prepare the endpoint call
        var endpoint = when (itemType) {
            ItemType.COURSE -> siriusApiService.getCourseEvents(accessToken = accessToken, id = id)
            ItemType.PERSON -> siriusApiService.getPersonEvents(accessToken = accessToken, id = id, from = "2019-3-2")
            ItemType.ROOM -> siriusApiService.getRoomEvents(accessToken = accessToken, id = id)
        }

        disposable = endpoint
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
//            .map { t -> t.events }
            .subscribe(
                { result ->
                    Log.i(TAG, "Events: $result")
                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error.message}") }
            )

    }
}