package com.example.wallko.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wallko.game.FoundPlace
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Best-effort "where was this found". Never asks for permission and never throws: if location isn't
 * already granted, or anything fails or is slow, the result is [FoundPlace.Unknown] or a place without a name.
 */
@Singleton
class FoundPlaceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun lookup(): FoundPlace {
        if (!hasLocationPermission()) return FoundPlace.Unknown
        val location = try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) { currentLocation() } ?: lastLocation()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "Location lookup failed", e)
            null
        } ?: return FoundPlace.Unknown
        val name = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { placeName(location.latitude, location.longitude) }
        return FoundPlace(location.latitude, location.longitude, name)
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission()
    private suspend fun currentLocation(): Location? {
        val cts = CancellationTokenSource()
        return try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .await()
        } finally {
            cts.cancel()
        }
    }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission()
    private suspend fun lastLocation(): Location? = try {
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        }
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        Log.w(TAG, "lastLocation failed", e)
        null
    }

    private suspend fun placeName(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed", e)
            null
        }
        return address?.let {
            ScanMath.shortPlaceName(it.subLocality, it.locality, it.subAdminArea, it.adminArea, it.countryName)
        }
    }

    private companion object {
        const val TAG = "FoundPlaceProvider"
        const val LOCATION_TIMEOUT_MS = 4_000L
        const val GEOCODE_TIMEOUT_MS = 4_000L
    }
}
