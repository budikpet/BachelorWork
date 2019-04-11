package cz.budikpet.bachelorwork.util

import android.Manifest
import android.app.Activity
import android.support.v4.app.ActivityCompat
import android.util.Log
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest

class PermissionsHandler(private val callback: Callback) : EasyPermissions.RationaleCallbacks,
    EasyPermissions.PermissionCallbacks {
    private val TAG = "AMY_${this.javaClass.simpleName}"

    interface Callback {
        fun onAllPermissionsGranted()

        fun getActivity(): Activity
    }

    companion object {
        const val CODE_REQUEST_PERMISSIONS = 1

        val requiredPerms: Array<String> = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_SYNC_STATS,
            Manifest.permission.GET_ACCOUNTS
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.i(TAG, "PermsHandler permsresult")
    }

    @AfterPermissionGranted(CODE_REQUEST_PERMISSIONS)
    fun checkPermissions() {
        Log.i(TAG, "Checking permissions")
        if (EasyPermissions.hasPermissions(callback.getActivity(), *requiredPerms)) {
            Log.i(TAG, "Already has all the permissions needed.")
            callback.onAllPermissionsGranted()
        } else {
            Log.i(TAG, "Asking for permissions.")
            permsCheck()
        }

    }

    private fun permsCheck() {
        val perms: MutableList<String> = mutableListOf()

        for (perm in requiredPerms) {
            if (!EasyPermissions.hasPermissions(callback.getActivity(), perm)) {
                perms.add(perm)
            }
        }
        Log.i(TAG, "Asking for these permissions: $perms")

        EasyPermissions.requestPermissions(
            PermissionRequest.Builder(callback.getActivity(), CODE_REQUEST_PERMISSIONS, *perms.toTypedArray())
                .setRationale("PermsCheck. We need them.")
                .setNegativeButtonText("Quit")
                .build()
        )
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "Denied permissions: $perms")

        if (EasyPermissions.somePermissionPermanentlyDenied(callback.getActivity(), perms)) {
            Log.i(TAG, "Some permissions permanently denied.")
            AppSettingsDialog.Builder(callback.getActivity())
                .setNegativeButton("Quit")
                .build()
                .show()
        } else {
            permsCheck()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.i(TAG, "Granted permissions: $perms")
    }

    override fun onRationaleDenied(requestCode: Int) {
        Log.i(TAG, "Rationale denied: $requestCode")

        myFinish()
    }

    override fun onRationaleAccepted(requestCode: Int) {
        Log.i(TAG, "Rationale accepted: $requestCode")
    }

    private fun myFinish() {
        callback.getActivity().finishAffinity()
    }
}