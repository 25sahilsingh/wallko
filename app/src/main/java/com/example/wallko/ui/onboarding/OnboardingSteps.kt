package com.example.wallko.ui.onboarding

import android.Manifest
import com.example.wallko.walk.HealthConnectStatus

/** One "may I?" card in the onboarding permission flow. Order of the entries is the order they are asked. */
enum class PermissionStep {
    CAMERA,
    ACTIVITY_RECOGNITION,

    /** Fine + coarse, while in use only. */
    LOCATION,

    /** POST_NOTIFICATIONS, API 33+ only. */
    NOTIFICATIONS,

    /** Health Connect is installed and ready: ask for step-read permission. */
    HEALTH_CONNECT,

    /** Health Connect missing or too old: offer the Play Store page instead of a permission dialog. */
    HEALTH_CONNECT_INSTALL,
}

/** Pure rules for which permission cards to show. No Android calls, so it runs in plain JVM tests. */
object OnboardingSteps {
    const val API_TIRAMISU = 33

    /** Runtime permissions launched for [step]; empty for the Health Connect steps (they use their own contract). */
    fun runtimePermissions(step: PermissionStep): List<String> = when (step) {
        PermissionStep.CAMERA -> listOf(Manifest.permission.CAMERA)
        PermissionStep.ACTIVITY_RECOGNITION -> listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        PermissionStep.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        PermissionStep.NOTIFICATIONS -> listOf(Manifest.permission.POST_NOTIFICATIONS)
        PermissionStep.HEALTH_CONNECT, PermissionStep.HEALTH_CONNECT_INSTALL -> emptyList()
    }

    /** Every runtime permission onboarding might ask for on [sdkInt] (handy for building the granted set). */
    fun candidatePermissions(sdkInt: Int): List<String> =
        PermissionStep.entries.filter { applies(it, sdkInt) }.flatMap(::runtimePermissions)

    /**
     * Ordered permission cards still worth showing.
     *
     * @param sdkInt Build.VERSION.SDK_INT.
     * @param granted runtime permissions already granted.
     * @param healthConnect Health Connect SDK status.
     * @param healthGranted true when Health Connect step-read permission is already granted.
     */
    fun permissionSteps(
        sdkInt: Int,
        granted: Set<String>,
        healthConnect: HealthConnectStatus,
        healthGranted: Boolean,
    ): List<PermissionStep> = buildList {
        if (Manifest.permission.CAMERA !in granted) add(PermissionStep.CAMERA)
        if (Manifest.permission.ACTIVITY_RECOGNITION !in granted) add(PermissionStep.ACTIVITY_RECOGNITION)
        // Walks need precise location; coarse alone still gets the card so the user can upgrade.
        if (Manifest.permission.ACCESS_FINE_LOCATION !in granted) add(PermissionStep.LOCATION)
        if (applies(PermissionStep.NOTIFICATIONS, sdkInt) && Manifest.permission.POST_NOTIFICATIONS !in granted) {
            add(PermissionStep.NOTIFICATIONS)
        }
        when (healthConnect) {
            HealthConnectStatus.AVAILABLE -> if (!healthGranted) add(PermissionStep.HEALTH_CONNECT)
            HealthConnectStatus.UPDATE_REQUIRED -> add(PermissionStep.HEALTH_CONNECT_INSTALL)
            HealthConnectStatus.UNAVAILABLE -> Unit
        }
    }

    private fun applies(step: PermissionStep, sdkInt: Int): Boolean =
        step != PermissionStep.NOTIFICATIONS || sdkInt >= API_TIRAMISU
}
