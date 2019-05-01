package cz.budikpet.bachelorwork.util

import android.accounts.NetworkErrorException
import android.security.keystore.UserNotAuthenticatedException

class GoogleAccountNotFoundException(message: String = "Specified Google account not found.") :
    UserNotAuthenticatedException(message)

class NoInternetConnectionException(message: String = "Could not connect to the internet.") :
    NetworkErrorException(message)