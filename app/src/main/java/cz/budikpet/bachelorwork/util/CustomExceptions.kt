package cz.budikpet.bachelorwork.util

import android.security.keystore.UserNotAuthenticatedException
import java.net.ConnectException

class GoogleAccountNotFoundException() : UserNotAuthenticatedException()

class NoInternetConnectionException() : ConnectException()