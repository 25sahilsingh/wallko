package com.example.wallko.ui.walk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Where the location-permission conversation with the player currently is. */
enum class LocationAsk {
    NONE,

    /** Drip explains why before the system dialog. */
    RATIONALE,

    /** Denied, system will still show the dialog again. */
    DENIED,

    /** Denied with "don't ask again" (or dialog suppressed): only Settings can fix it. */
    DENIED_FOREVER,

    /** Only approximate location granted; walks need precise. */
    NEED_PRECISE,
}

internal object WalkPermissions {
    const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION

    /** Location (required) + activity recognition and notifications (nice to have), asked in one go. */
    fun walkPermissions(): Array<String> = buildList {
        add(FINE)
        add(COARSE)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFine(context: Context) = granted(context, FINE)

    fun missingOptional(context: Context): List<String> =
        walkPermissions().filter { it != FINE && it != COARSE && !granted(context, it) }

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
