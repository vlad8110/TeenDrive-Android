package com.vlad8110.teendrive.location

import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SpeedAlert
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ActiveDriveSnapshot(
    val id: String,
    val startedAt: Instant,
    val updatedAt: Instant,
    val route: List<RoutePoint>,
    val distanceMeters: Double,
    val topSpeedMetersPerSecond: Double,
    val currentSpeedMetersPerSecond: Double,
    val speedLimitMetersPerSecond: Double?,
    val speedAlerts: List<SpeedAlert>,
    val safetyAlerts: List<SafetyAlert>,
    val alertReady: Boolean,
    val arrivedPlaceIds: Set<String> = emptySet(),
) {
    val duration: Duration
        get() = Duration.between(startedAt, updatedAt)

    val distanceMiles: Double
        get() = distanceMeters / 1609.344

    val topSpeedMph: Double
        get() = max(topSpeedMetersPerSecond, 0.0) * 2.2369362921

    val currentSpeedMph: Double
        get() = max(currentSpeedMetersPerSecond, 0.0) * 2.2369362921

    val speedLimitMph: Double?
        get() = speedLimitMetersPerSecond?.let { max(it, 0.0) * 2.2369362921 }
}

object ActiveDriveSessionStore {
    private const val ALERT_READY_ROUTE_POINTS = 2

    @Volatile
    private var snapshot: ActiveDriveSnapshot? = null

    fun current(): ActiveDriveSnapshot? = snapshot

    fun start(now: Instant = Instant.now()): ActiveDriveSnapshot {
        val existing = snapshot
        if (existing != null) return existing

        return ActiveDriveSnapshot(
            id = UUID.randomUUID().toString(),
            startedAt = now,
            updatedAt = now,
            route = emptyList(),
            distanceMeters = 0.0,
            topSpeedMetersPerSecond = 0.0,
            currentSpeedMetersPerSecond = 0.0,
            speedLimitMetersPerSecond = null,
            speedAlerts = emptyList(),
            safetyAlerts = emptyList(),
            alertReady = false,
            arrivedPlaceIds = emptySet(),
        ).also { snapshot = it }
    }

    fun append(location: LocationSnapshot, timestamp: Instant = Instant.now()): ActiveDriveSnapshot {
        val current = snapshot ?: start(timestamp)
        val point = RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = timestamp,
        )
        val previousPoint = current.route.lastOrNull()
        val pointBeforePrevious = current.route.dropLast(1).lastOrNull()
        val segmentMeters = previousPoint?.let { distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude) } ?: 0.0
        val updatedRoute = current.route + point
        val detectionInput = SafetyDetectionInput(
            pointBeforePrevious = pointBeforePrevious,
            previousPoint = previousPoint,
            previousSpeedMetersPerSecond = current.currentSpeedMetersPerSecond,
            currentPoint = point,
            currentSpeedMetersPerSecond = location.speedMetersPerSecond,
            speedLimitMetersPerSecond = location.speedLimitMetersPerSecond ?: current.speedLimitMetersPerSecond,
            phoneUnlockedWhileMoving = location.phoneUnlockedWhileMoving,
            savedPlaces = emptyList(),
            arrivedPlaceIds = current.arrivedPlaceIds,
        )
        val rawDetectedAlerts = SafetyDetector.detect(detectionInput)
        val newlyArrivedPlaceIds = SafetyDetector.newlyArrivedPlaceIds(
            detectionInput,
        )
        val detectedAlerts = rawDetectedAlerts.filterNot { alert -> current.hasRecentAlert(alert) }
        val speedAlerts = detectedAlerts
            .filter { it.kind == com.vlad8110.teendrive.model.SafetyAlertKind.SPEED_LIMIT }
            .mapNotNull { alert ->
                val latitude = alert.latitude
                val longitude = alert.longitude
                if (latitude != null && longitude != null) {
                    SpeedAlert(
                        id = alert.id,
                        timestamp = alert.timestamp,
                        speedMetersPerSecond = alert.speedMetersPerSecond ?: 0.0,
                        latitude = latitude,
                        longitude = longitude,
                    )
                } else {
                    null
                }
            }
        val updated = current.copy(
            updatedAt = timestamp,
            route = updatedRoute,
            distanceMeters = current.distanceMeters + segmentMeters,
            topSpeedMetersPerSecond = max(current.topSpeedMetersPerSecond, location.speedMetersPerSecond),
            currentSpeedMetersPerSecond = location.speedMetersPerSecond,
            speedLimitMetersPerSecond = location.speedLimitMetersPerSecond ?: current.speedLimitMetersPerSecond,
            speedAlerts = current.speedAlerts + speedAlerts,
            safetyAlerts = current.safetyAlerts + detectedAlerts,
            alertReady = updatedRoute.size >= ALERT_READY_ROUTE_POINTS,
            arrivedPlaceIds = current.arrivedPlaceIds + newlyArrivedPlaceIds,
        )
        snapshot = updated
        return updated
    }

    fun stop(now: Instant = Instant.now()): ActiveDriveSnapshot? {
        val completed = snapshot?.copy(updatedAt = now)
        snapshot = null
        return completed
    }

}

private fun ActiveDriveSnapshot.hasRecentAlert(alert: SafetyAlert): Boolean =
    safetyAlerts.any { existing ->
        existing.kind == alert.kind &&
            Duration.between(existing.timestamp, alert.timestamp).abs().seconds < 60
    }

fun distanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val startLatRadians = Math.toRadians(startLatitude)
    val endLatRadians = Math.toRadians(endLatitude)
    val deltaLat = Math.toRadians(endLatitude - startLatitude)
    val deltaLon = Math.toRadians(endLongitude - startLongitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(startLatRadians) * cos(endLatRadians) *
        sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

fun Duration.driveDurationText(): String {
    val totalSeconds = max(seconds, 0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secondsRemainder = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secondsRemainder)
    } else {
        "%d:%02d".format(minutes, secondsRemainder)
    }
}

fun Double.oneDecimal(): String = (this * 10.0).roundToInt().let { "%.1f".format(it / 10.0) }
