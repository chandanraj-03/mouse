package com.example.phonemouse

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

class PermissionManager(private val activity: Activity) {
    /** Android 12+ needs BLUETOOTH_CONNECT at runtime; older versions use install-time permissions. */
    private val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        else emptyArray()

    fun hasAll(): Boolean = required.all {
        activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun request(requestCode: Int) {
        if (required.isNotEmpty()) activity.requestPermissions(required, requestCode)
    }
}
