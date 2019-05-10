package cz.budikpet.bachelorwork.util

import java.net.ConnectException

class GoogleAccountNotFoundException(message: String = "") : Throwable(message)

class NoInternetConnectionException(message: String = "") : ConnectException(message)

class CTUUserNotAuthenticatedException(message: String = "") : Throwable(message)