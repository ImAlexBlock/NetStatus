package com.alexblock.netstatus.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

val REQUIRED_WIFI = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.NEARBY_WIFI_DEVICES,
)

val REQUIRED_WWAN = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
)

fun Context.hasNearbyWifiDevices(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasAll(perms: Array<String>): Boolean =
    perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

fun Context.missingOf(perms: Array<String>): List<String> =
    perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.ACCESS_FINE_LOCATION -> "精确位置"
    Manifest.permission.ACCESS_COARSE_LOCATION -> "大致位置"
    Manifest.permission.NEARBY_WIFI_DEVICES -> "附近的设备"
    Manifest.permission.READ_PHONE_STATE -> "电话状态"
    else -> permission.substringAfterLast('.')
}

fun Context.locationEnabled(): Boolean = try {
    val lm = getSystemService(LocationManager::class.java)
    lm?.isLocationEnabled == true
} catch (e: Exception) {
    false
}

fun Context.openAppSettings(): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null),
).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Context.openLocationSettings(): Intent =
    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
