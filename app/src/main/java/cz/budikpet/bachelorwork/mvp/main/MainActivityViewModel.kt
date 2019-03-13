package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.api.SiriusApiService
import cz.budikpet.bachelorwork.dataModel.ItemType
import cz.budikpet.bachelorwork.dataModel.Model
import cz.budikpet.bachelorwork.util.AppAuthManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class MainActivityViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    internal lateinit var repository: Repository

    init {
        MyApplication.appComponent.inject(this)

        // TODO: Inject
        repository = Repository()
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        repository.checkAuthorization(response, exception)
    }

    fun signOut() {
        repository.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return repository.getSiriusApiEvents()
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        repository.searchSiriusApiEvents(itemType, id)
    }
}