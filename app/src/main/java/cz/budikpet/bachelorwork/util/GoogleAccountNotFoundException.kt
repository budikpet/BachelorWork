package cz.budikpet.bachelorwork.util

import android.security.keystore.UserNotAuthenticatedException

class GoogleAccountNotFoundException(message: String? = "Specified Google account not found.") : UserNotAuthenticatedException(message) {
}