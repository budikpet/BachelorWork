package cz.budikpet.bachelorwork.mvp.main

import cz.budikpet.bachelorwork.dataModel.ItemType
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

interface MainContract {
    interface View {
        fun showString(string: String)
    }

    interface Presenter {
        fun signOut()
        fun getSiriusApiEvents(itemType: ItemType, id: String)
        fun onDestroy()
        fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?)
    }
}