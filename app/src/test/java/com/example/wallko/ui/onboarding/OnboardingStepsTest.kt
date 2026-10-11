package com.example.wallko.ui.onboarding

import android.Manifest
import com.example.wallko.walk.HealthConnectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {
    private val all = listOf(
        PermissionStep.CAMERA,
        PermissionStep.ACTIVITY_RECOGNITION,
        PermissionStep.LOCATION,
        PermissionStep.NOTIFICATIONS,
        PermissionStep.HEALTH_CONNECT,
    )

    @Test
    fun freshInstallOnApi34AsksEverythingInOrder() {
        val steps = OnboardingSteps.permissionSteps(34, emptySet(), HealthConnectStatus.AVAILABLE, healthGranted = false)
        assertEquals(all, steps)
    }

    @Test
    fun notificationsSkippedBelowApi33() {
        val steps = OnboardingSteps.permissionSteps(32, emptySet(), HealthConnectStatus.AVAILABLE, healthGranted = false)
        assertFalse(PermissionStep.NOTIFICATIONS in steps)
        assertEquals(4, steps.size)
    }

    @Test
    fun notificationsAskedOnExactlyApi33() {
        val steps = OnboardingSteps.permissionSteps(33, emptySet(), HealthConnectStatus.UNAVAILABLE, healthGranted = false)
        assertTrue(PermissionStep.NOTIFICATIONS in steps)
    }

    @Test
    fun alreadyGrantedPermissionsAreSkipped() {
        val granted = setOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
        val steps = OnboardingSteps.permissionSteps(34, granted, HealthConnectStatus.AVAILABLE, healthGranted = true)
        assertEquals(listOf(PermissionStep.ACTIVITY_RECOGNITION, PermissionStep.LOCATION), steps)
    }

    @Test
    fun coarseOnlyStillAsksLocation() {
        val steps = OnboardingSteps.permissionSteps(
            34,
            setOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            HealthConnectStatus.UNAVAILABLE,
            healthGranted = false,
        )
        assertTrue(PermissionStep.LOCATION in steps)
    }

    @Test
    fun fineLocationGrantedSkipsLocation() {
        val steps = OnboardingSteps.permissionSteps(
            34,
            setOf(Manifest.permission.ACCESS_FINE_LOCATION),
            HealthConnectStatus.UNAVAILABLE,
            healthGranted = false,
        )
        assertFalse(PermissionStep.LOCATION in steps)
    }

    @Test
    fun healthConnectUpdateRequiredOffersInstall() {
        val steps = OnboardingSteps.permissionSteps(30, emptySet(), HealthConnectStatus.UPDATE_REQUIRED, healthGranted = false)
        assertEquals(PermissionStep.HEALTH_CONNECT_INSTALL, steps.last())
        assertFalse(PermissionStep.HEALTH_CONNECT in steps)
    }

    @Test
    fun healthConnectUnavailableIsSkipped() {
        val steps = OnboardingSteps.permissionSteps(34, emptySet(), HealthConnectStatus.UNAVAILABLE, healthGranted = false)
        assertFalse(PermissionStep.HEALTH_CONNECT in steps)
        assertFalse(PermissionStep.HEALTH_CONNECT_INSTALL in steps)
    }

    @Test
    fun healthConnectAlreadyGrantedIsSkipped() {
        val steps = OnboardingSteps.permissionSteps(34, emptySet(), HealthConnectStatus.AVAILABLE, healthGranted = true)
        assertFalse(PermissionStep.HEALTH_CONNECT in steps)
    }

    @Test
    fun everythingGrantedMeansNoSteps() {
        val granted = OnboardingSteps.candidatePermissions(34).toSet()
        val steps = OnboardingSteps.permissionSteps(34, granted, HealthConnectStatus.AVAILABLE, healthGranted = true)
        assertTrue(steps.isEmpty())
    }

    @Test
    fun candidatePermissionsRespectApiLevel() {
        assertFalse(Manifest.permission.POST_NOTIFICATIONS in OnboardingSteps.candidatePermissions(29))
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in OnboardingSteps.candidatePermissions(33))
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in OnboardingSteps.candidatePermissions(29))
    }

    @Test
    fun locationAsksFineAndCoarseTogether() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            OnboardingSteps.runtimePermissions(PermissionStep.LOCATION),
        )
        assertTrue(OnboardingSteps.runtimePermissions(PermissionStep.HEALTH_CONNECT).isEmpty())
    }
}
