package com.vlad8110.teendrive.model

import java.time.Duration
import java.time.Instant
import kotlin.math.max

data class TeenProfile(
    val id: String,
    val familyGroupId: String,
    val displayName: String,
    val connectedParentIds: List<String>,
    val fcmToken: String?,
    val updatedAt: Instant,
)

data class ParentProfile(
    val id: String,
    val displayName: String,
    val familyGroupIds: List<String>,
    val connectedTeenIds: List<String>,
    val fcmToken: String?,
    val updatedAt: Instant,
)

data class ActiveTeenDrive(
    val teenProfileId: String,
    val familyGroupId: String,
    val teenName: String,
    val startedAt: Instant,
    val updatedAt: Instant,
    val speedMetersPerSecond: Double,
    val topSpeedMetersPerSecond: Double,
    val distanceMeters: Double,
    val alertCount: Int,
    val lastKnownLocation: RoutePoint?,
    val safetyAlerts: List<SafetyAlert>,
    val route: List<RoutePoint>,
) {
    val id: String
        get() = teenProfileId

    val speedMph: Double
        get() = max(speedMetersPerSecond, 0.0) * 2.2369362921

    val topSpeedMph: Double
        get() = max(topSpeedMetersPerSecond, 0.0) * 2.2369362921

    val distanceMiles: Double
        get() = distanceMeters / 1609.344

    val duration: Duration
        get() = Duration.between(startedAt, Instant.now())
}
