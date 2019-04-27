package cz.budikpet.bachelorwork.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.support.v4.app.Fragment
import android.util.Log
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest

// TODO: Isn't it possible to use a normal kotlin class which somehow implements needed methods of activities?
/**
 * A simple [Fragment] subclass.
 * It is used to display permission dialogs until the user agrees to all the required permissions.
 *
 * Activities that contain this fragment must implement the
 * [PermissionsCheckerFragment.Callback] interface to handle interaction events.
 * Use the [PermissionsCheckerFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class PermissionsCheckerFragment : Fragment(), EasyPermissions.RationaleCallbacks, EasyPermissions.PermissionCallbacks {
    private val TAG = "AMY_${this.javaClass.simpleName}"

    private var callback: Callback? = null

    // TODO: Use LiveData to pass information about granted permissions?
    interface Callback {
        fun onAllPermissionsGranted()

        fun quitApplication()
    }

    // TODO: Create withRequiredPermissions method?
    companion object {
        private const val BASE_PERMISSIONS_REQUEST = 1

        const val BASE_TAG = "permissionsCheckerFragment"
        val requiredPerms: Array<String> = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_SYNC_STATS,
            Manifest.permission.GET_ACCOUNTS
        )

        @JvmStatic
        fun newInstance() = PermissionsCheckerFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
//        Log.i(TAG, "OnAttach")
        if (context is Callback) {
            callback = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    // MARK: Permissions checking

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // The user was asked to go to settings to grant permissions
            Log.i(TAG, "The user returned from settings dialog.")
            if (EasyPermissions.hasPermissions(this.context!!, *requiredPerms)) {
                callback?.onAllPermissionsGranted()
            } else {
                callback?.quitApplication()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Enables results of EasyPermissions interfaces and @AfterPermissionGranted annotation in [PermissionsCheckerFragment]
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)

        // Enables results of EasyPermissions interfaces and @AfterPermissionGranted annotation in the activity
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, callback)
    }

    /**
     * Checks whether the app has all the required permissions.
     */
    @AfterPermissionGranted(BASE_PERMISSIONS_REQUEST)
    fun checkPermissions() {
        if (EasyPermissions.hasPermissions(this.context!!, *requiredPerms)) {
            Log.i(TAG, "Already has all the permissions needed.")
            callback?.onAllPermissionsGranted()
        } else {
            Log.i(TAG, "Asking for permissions.")
            requestDeniedPermissions()
        }

    }

    /**
     * Requests only denied permissions.
     */
    private fun requestDeniedPermissions() {
        val perms: MutableList<String> = mutableListOf()

        for (perm in requiredPerms) {
            if (!EasyPermissions.hasPermissions(this.context!!, perm)) {
                perms.add(perm)
            }
        }
        Log.i(TAG, "Asking for these permissions: $perms")

        EasyPermissions.requestPermissions(
            PermissionRequest.Builder(this, BASE_PERMISSIONS_REQUEST, *perms.toTypedArray())
                .setRationale("PermsCheck. We need them.")
                .setNegativeButtonText("Quit")
                .build()
        )
    }

    /**
     * The application needs all the permissions to function.
     */
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "Denied permissions: $perms")

        // Ask for the permissions again
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            Log.i(TAG, "Some permissions permanently denied.")
            AppSettingsDialog.Builder(this)
                .setNegativeButton("Quit")
                .build()
                .show()
        } else {
            requestDeniedPermissions()
        }
    }

    /**
     * User didn't want to grant permissions so he quit the application.
     */
    override fun onRationaleDenied(requestCode: Int) {
        Log.i(TAG, "Rationale denied: $requestCode")

        callback?.quitApplication()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "Granted permissions: $perms")
    }

    override fun onRationaleAccepted(requestCode: Int) {
        Log.i(TAG, "Rationale accepted: $requestCode")
    }
}