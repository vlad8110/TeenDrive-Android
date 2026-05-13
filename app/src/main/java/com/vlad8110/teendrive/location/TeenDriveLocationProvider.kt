package com.vlad8110.teendrive.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlin.math.max

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Double,
    val accuracyMeters: Float?,
    val provider: String?,
    val speedLimitMetersPerSecond: Double? = null,
    val phoneUnlockedWhileMoving: Boolean = false,
) {
    val speedMph: Double
        get() = max(speedMetersPerSecond, 0.0) * 2.2369362921

    val speedLimitMph: Double?
        get() = speedLimitMetersPerSecond?.let { max(it, 0.0) * 2.2369362921 }
}

class TeenDriveLocationProvider(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun currentSnapshot(): LocationSnapshot? {
        val tokenSource = CancellationTokenSource()
        val currentLocation = runCatching {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                tokenSource.token,
            ).await()
        }.getOrNull()

        val location = currentLocation ?: runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()
        return location?.let {
            LocationSnapshot(
                latitude = it.latitude,
                longitude = it.longitude,
                speedMetersPerSecond = if (it.hasSpeed()) it.speed.toDouble() else 0.0,
                accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                provider = it.provider,
                speedLimitMetersPerSecond = it.speedLimitMetersPerSecondFromExtras(),
            )
        }
    }
}

fun android.location.Location.speedLimitMetersPerSecondFromExtras(): Double? {
    val extras = extras ?: return null
    val metersPerSecond = listOf("speed_limit_mps", "speedLimitMps", "speed_limit_meters_per_second")
        .firstNotNullOfOrNull { key ->
            if (extras.containsKey(key)) extras.getDouble(key).takeIf { it > 0.0 } else null
        }
    if (metersPerSecond != null) return metersPerSecond

    val milesPerHour = listOf("speed_limit_mph", "speedLimitMph", "speed_limit", "speedLimit")
        .firstNotNullOfOrNull { key ->
            if (extras.containsKey(key)) extras.getDouble(key).takeIf { it > 0.0 } else null
        }
    return milesPerHour?.let { it / 2.2369362921 }
}
