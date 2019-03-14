package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

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