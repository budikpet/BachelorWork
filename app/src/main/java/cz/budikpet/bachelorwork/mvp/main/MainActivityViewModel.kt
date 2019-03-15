package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class MainActivityViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val events = MutableLiveData<List<Model.Event>>()

    @Inject
    internal lateinit var repository: Repository

    private var disposable: Disposable? = null

    init {
        MyApplication.appComponent.inject(this)
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        repository.checkAuthorization(response, exception)
    }

    fun signOut() {
        repository.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return events
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        disposable = repository.searchSiriusApiEvents(itemType, id)
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
}