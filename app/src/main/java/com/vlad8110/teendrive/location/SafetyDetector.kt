package com.vlad8110.teendrive.location

import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SafetyAlertKind
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

data class SafetyDetectionInput(
    val pointBeforePrevious: RoutePoint?,
    val previousPoint: RoutePoint?,
    val previousSpeedMetersPerSecond: Double,
    val currentPoint: RoutePoint,
    val currentSpeedMetersPerSecond: Double,
    val speedLimitMetersPerSecond: Double? = null,
    val phoneUnlockedWhileMoving: Boolean = false,
    val savedPlaces: List<SavedPlace> = emptyList(),
    val arrivedPlaceIds: Set<String> = emptySet(),
)

data class SavedPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 100.0,
)

object SafetyDetector {
    const val FALLBACK_SPEED_LIMIT_MPH = 75.0
    private const val RAPID_ACCELERATION_MPS2 = 3.2
    private const val HARSH_STOP_MPS2 = -3.4
    private const val HARSH_CORNERING_DEGREES = 55.0
    private const val MIN_CORNERING_SPEED_MPS = 8.9
    private const val MOVING_PHONE_USE_SPEED_MPS = 2.2
    private const val ARRIVAL_MAX_SPEED_MPS = 4.5

    fun detect(input: SafetyDetectionInput): List<SafetyAlert> {
        val alerts = mutableListOf<SafetyAlert>()
        val current = input.currentPoint
        val previous = input.previousPoint
        val currentSpeedMph = max(input.currentSpeedMetersPerSecond, 0.0) * 2.2369362921
        val speedLimitMph = input.speedLimitMetersPerSecond?.let { max(it, 0.0) * 2.2369362921 }
            ?: FALLBACK_SPEED_LIMIT_MPH

        if (currentSpeedMph > speedLimitMph) {
            alerts += alert(
                kind = SafetyAlertKind.SPEED_LIMIT,
                point = current,
                speedMetersPerSecond = input.currentSpeedMetersPerSecond,
                note = if (input.speedLimitMetersPerSecond == null) {
                    "Over fallback speed limit"
                } else {
                    "Over ${speedLimitMph.toInt()} mph speed limit"
                },
            )
        }

        if (previous != null) {
            val seconds = max(java.time.Duration.between(previous.timestamp, current.timestamp).toMillis() / 1000.0, 1.0)
            val acceleration = (input.currentSpeedMetersPerSecond - input.previousSpeedMetersPerSecond) / seconds
            if (acceleration >= RAPID_ACCELERATION_MPS2) {
                alerts += alert(SafetyAlertKind.RAPID_ACCELERATION, current, input.currentSpeedMetersPerSecond, "Rapid acceleration")
            }
            if (acceleration <= HARSH_STOP_MPS2) {
                alerts += alert(SafetyAlertKind.HARSH_STOP, current, input.currentSpeedMetersPerSecond, "Harsh stop")
            }

            val headingDelta = input.pointBeforePrevious?.let { headingDeltaDegrees(it, previous, current) } ?: 0.0
            if (input.currentSpeedMetersPerSecond >= MIN_CORNERING_SPEED_MPS && headingDelta >= HARSH_CORNERING_DEGREES) {
                alerts += alert(SafetyAlertKind.HARSH_CORNERING, current, input.currentSpeedMetersPerSecond, "Harsh cornering")
            }
        }

        if (isNightDriving(current.timestamp)) {
            alerts += alert(SafetyAlertKind.NIGHT_DRIVING, current, input.currentSpeedMetersPerSecond, "Night driving")
        }

        if (input.phoneUnlockedWhileMoving && input.currentSpeedMetersPerSecond >= MOVING_PHONE_USE_SPEED_MPS) {
            alerts += alert(SafetyAlertKind.PHONE_USE, current, input.currentSpeedMetersPerSecond, "Phone unlocked while moving")
        }

        newlyArrivedPlaceIds(input).forEach { placeId ->
            val place = input.savedPlaces.first { it.id == placeId }
            alerts += alert(SafetyAlertKind.PLACE_ARRIVAL, current, input.currentSpeedMetersPerSecond, "Arrived at ${place.name}")
        }

        return alerts
    }

    fun newlyArrivedPlaceIds(input: SafetyDetectionInput): Set<String> {
        val previous = input.previousPoint ?: return emptySet()
        val current = input.currentPoint
        if (input.currentSpeedMetersPerSecond > ARRIVAL_MAX_SPEED_MPS) return emptySet()

        return input.savedPlaces
            .filterNot { it.id in input.arrivedPlaceIds }
            .filter { place ->
                val previousDistance = distanceMeters(previous.latitude, previous.longitude, place.latitude, place.longitude)
                val currentDistance = distanceMeters(current.latitude, current.longitude, place.latitude, place.longitude)
                previousDistance > place.radiusMeters && currentDistance <= place.radiusMeters
            }
            .map { it.id }
            .toSet()
    }

    private fun alert(
        kind: SafetyAlertKind,
        point: RoutePoint,
        speedMetersPerSecond: Double,
        note: String,
    ): SafetyAlert =
        SafetyAlert(
            kind = kind,
            timestamp = point.timestamp,
            speedMetersPerSecond = speedMetersPerSecond,
            latitude = point.latitude,
            longitude = point.longitude,
            note = note,
        )

    private fun isNightDriving(timestamp: Instant): Boolean {
        val hour = timestamp.atZone(ZoneId.systemDefault()).hour
        return hour >= 22 || hour < 5
    }

    private fun headingDeltaDegrees(beforePrevious: RoutePoint, previous: RoutePoint, current: RoutePoint): Double {
        val firstBearing = bearingDegrees(beforePrevious.latitude, beforePrevious.longitude, previous.latitude, previous.longitude)
        val secondBearing = bearingDegrees(previous.latitude, previous.longitude, current.latitude, current.longitude)
        val delta = abs(secondBearing - firstBearing) % 360.0
        return if (delta > 180) 360.0 - delta else delta
    }

    private fun bearingDegrees(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        val startLat = Math.toRadians(startLatitude)
        val endLat = Math.toRadians(endLatitude)
        val deltaLon = Math.toRadians(endLongitude - startLongitude)
        val y = kotlin.math.sin(deltaLon) * kotlin.math.cos(endLat)
        val x = kotlin.math.cos(startLat) * kotlin.math.sin(endLat) -
            kotlin.math.sin(startLat) * kotlin.math.cos(endLat) * kotlin.math.cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
