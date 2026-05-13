package com.vlad8110.teendrive.model

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val METERS_PER_SECOND_TO_MPH = 2.2369362921
private const val METERS_PER_MILE = 1609.344

data class RoutePoint(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
)

data class SpeedAlert(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Instant,
    val speedMetersPerSecond: Double,
    val latitude: Double,
    val longitude: Double,
) {
    val speedMph: Double
        get() = max(speedMetersPerSecond, 0.0) * METERS_PER_SECOND_TO_MPH
}

enum class SafetyAlertKind(val title: String, val countsAsSafetyAlert: Boolean) {
    SPEED_LIMIT("Speed over limit", true),
    RAPID_ACCELERATION("Rapid acceleration", true),
    HARSH_STOP("Harsh stop", true),
    HARSH_CORNERING("Harsh cornering", true),
    NIGHT_DRIVING("Night driving", true),
    PHONE_USE("Phone use", true),
    TRIP_STARTED("Trip started", false),
    TRIP_ENDED("Trip ended", false),
    PLACE_ARRIVAL("Arrived", true);

    val firestoreValue: String
        get() = when (this) {
            SPEED_LIMIT -> "speedLimit"
            RAPID_ACCELERATION -> "rapidAcceleration"
            HARSH_STOP -> "harshStop"
            HARSH_CORNERING -> "harshCornering"
            NIGHT_DRIVING -> "nightDriving"
            PHONE_USE -> "phoneUse"
            TRIP_STARTED -> "tripStarted"
            TRIP_ENDED -> "tripEnded"
            PLACE_ARRIVAL -> "placeArrival"
        }

    companion object {
        fun fromFirestoreValue(value: String): SafetyAlertKind? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

data class SafetyAlert(
    val id: String = UUID.randomUUID().toString(),
    val kind: SafetyAlertKind,
    val timestamp: Instant,
    val speedMetersPerSecond: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String? = null,
) {
    val speedMph: Double?
        get() = speedMetersPerSecond?.let { max(it, 0.0) * METERS_PER_SECOND_TO_MPH }

    val displayText: String
        get() = speedMph?.let { "%.0f mph".format(it) } ?: note ?: kind.title
}

data class TeenTrip(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val distanceMeters: Double,
    val topSpeedMetersPerSecond: Double,
    val speedAlerts: List<SpeedAlert> = emptyList(),
    val safetyAlerts: List<SafetyAlert> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
) {
    val duration: Duration
        get() = Duration.between(startedAt, endedAt)

    val distanceMiles: Double
        get() = distanceMeters / METERS_PER_MILE

    val topSpeedMph: Double
        get() = max(topSpeedMetersPerSecond, 0.0) * METERS_PER_SECOND_TO_MPH

    val displaySafetyAlerts: List<SafetyAlert>
        get() {
            val safetyEvents = safetyAlerts.filter { it.kind.countsAsSafetyAlert }
            if (safetyEvents.isNotEmpty()) return safetyEvents

            return speedAlerts.map { alert ->
                SafetyAlert(
                    id = alert.id,
                    kind = SafetyAlertKind.SPEED_LIMIT,
                    timestamp = alert.timestamp,
                    speedMetersPerSecond = alert.speedMetersPerSecond,
                    latitude = alert.latitude,
                    longitude = alert.longitude,
                    note = "Speed alert",
                )
            }
        }

    val safetyAlertCount: Int
        get() = displaySafetyAlerts.size

    val speedLimitAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.SPEED_LIMIT }

    val rapidAccelerationAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.RAPID_ACCELERATION }

    val harshStopAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.HARSH_STOP }

    val harshCorneringAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.HARSH_CORNERING }

    val nightDrivingAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.NIGHT_DRIVING }

    val phoneUseAlertCount: Int
        get() = displaySafetyAlerts.count { it.kind == SafetyAlertKind.PHONE_USE }

    val drivingEventAlertCount: Int
        get() = rapidAccelerationAlertCount + harshStopAlertCount + harshCorneringAlertCount

    val behaviorScoreBreakdown: TripBehaviorScoreBreakdown
        get() = TripBehaviorScoreBreakdown.compute(this)

    val mapBounds: MapBounds
        get() {
            val points = route.map { Coordinate(it.latitude, it.longitude) } +
                displaySafetyAlerts.mapNotNull { alert ->
                    val latitude = alert.latitude
                    val longitude = alert.longitude
                    if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null
                }
            return MapBounds.from(points)
        }
}

data class TripBehaviorScoreBreakdown(
    val score: Int,
    val topSpeedPenalty: Double,
    val speedingPenalty: Double,
    val drivingEventPenalty: Double,
    val harshStopPenalty: Double,
    val alertRatePenalty: Double,
) {
    val totalPenalty: Double
        get() = topSpeedPenalty + speedingPenalty + drivingEventPenalty + harshStopPenalty + alertRatePenalty

    companion object {
        fun compute(trip: TeenTrip): TripBehaviorScoreBreakdown {
            val durationHours = max(trip.duration.seconds / 3600.0, 0.1667)
            val totalAlerts = trip.safetyAlertCount.toDouble()
            val topSpeedPenalty = min(15.0, max(0.0, (trip.topSpeedMph - 75.0) * 0.8))
            val speedingPenalty = min(30.0, trip.speedLimitAlertCount * 5.0)
            val drivingEventPenalty = min(28.0, trip.drivingEventAlertCount * 7.0)
            val harshStopPenalty = min(12.0, trip.harshStopAlertCount * 4.0)
            val alertRatePenalty = min(15.0, (totalAlerts / durationHours) * 1.8)
            val penalty = topSpeedPenalty + speedingPenalty + drivingEventPenalty + harshStopPenalty + alertRatePenalty
            val score = (100.0 - penalty).roundToInt().coerceIn(0, 100)
            return TripBehaviorScoreBreakdown(
                score = score,
                topSpeedPenalty = topSpeedPenalty,
                speedingPenalty = speedingPenalty,
                drivingEventPenalty = drivingEventPenalty,
                harshStopPenalty = harshStopPenalty,
                alertRatePenalty = alertRatePenalty,
            )
        }
    }
}

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

data class MapBounds(
    val center: Coordinate,
    val latitudeDelta: Double,
    val longitudeDelta: Double,
) {
    companion object {
        fun from(points: List<Coordinate>): MapBounds {
            val first = points.firstOrNull()
                ?: return MapBounds(Coordinate(37.3349, -122.0090), 0.02, 0.02)
            val minLat = points.minOfOrNull { it.latitude } ?: first.latitude
            val maxLat = points.maxOfOrNull { it.latitude } ?: first.latitude
            val minLon = points.minOfOrNull { it.longitude } ?: first.longitude
            val maxLon = points.maxOfOrNull { it.longitude } ?: first.longitude
            return MapBounds(
                center = Coordinate((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0),
                latitudeDelta = max((maxLat - minLat) * 1.5, 0.01),
                longitudeDelta = max((maxLon - minLon) * 1.5, 0.01),
            )
        }
    }
}
