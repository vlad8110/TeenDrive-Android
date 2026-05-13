package com.vlad8110.teendrive.data

import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SafetyAlertKind
import com.vlad8110.teendrive.model.SpeedAlert
import com.vlad8110.teendrive.model.TeenTrip
import java.time.Instant

fun ActiveDriveSnapshot.toTeenTrip(): TeenTrip =
    TeenTrip(
        id = id,
        startedAt = startedAt,
        endedAt = updatedAt,
        distanceMeters = distanceMeters,
        topSpeedMetersPerSecond = topSpeedMetersPerSecond,
        speedAlerts = speedAlerts,
        safetyAlerts = safetyAlerts,
        route = route,
    )

fun TeenTrip.toEntity(): TripEntity =
    TripEntity(
        id = id,
        startedAtEpochMillis = startedAt.toEpochMilli(),
        endedAtEpochMillis = endedAt.toEpochMilli(),
        distanceMeters = distanceMeters,
        topSpeedMetersPerSecond = topSpeedMetersPerSecond,
        syncedAtEpochMillis = null,
    )

fun RoutePoint.toEntity(tripId: String): RoutePointEntity =
    RoutePointEntity(
        id = id,
        tripId = tripId,
        latitude = latitude,
        longitude = longitude,
        timestampEpochMillis = timestamp.toEpochMilli(),
    )

fun SpeedAlert.toEntity(tripId: String): SpeedAlertEntity =
    SpeedAlertEntity(
        id = id,
        tripId = tripId,
        timestampEpochMillis = timestamp.toEpochMilli(),
        speedMetersPerSecond = speedMetersPerSecond,
        latitude = latitude,
        longitude = longitude,
    )

fun SafetyAlert.toEntity(tripId: String): SafetyAlertEntity =
    SafetyAlertEntity(
        id = id,
        tripId = tripId,
        kind = kind.firestoreValue,
        timestampEpochMillis = timestamp.toEpochMilli(),
        speedMetersPerSecond = speedMetersPerSecond,
        latitude = latitude,
        longitude = longitude,
        note = note,
    )

fun TripWithDetails.toModel(): TeenTrip =
    TeenTrip(
        id = trip.id,
        startedAt = Instant.ofEpochMilli(trip.startedAtEpochMillis),
        endedAt = Instant.ofEpochMilli(trip.endedAtEpochMillis),
        distanceMeters = trip.distanceMeters,
        topSpeedMetersPerSecond = trip.topSpeedMetersPerSecond,
        speedAlerts = speedAlerts.map {
            SpeedAlert(
                id = it.id,
                timestamp = Instant.ofEpochMilli(it.timestampEpochMillis),
                speedMetersPerSecond = it.speedMetersPerSecond,
                latitude = it.latitude,
                longitude = it.longitude,
            )
        },
        safetyAlerts = safetyAlerts.mapNotNull {
            SafetyAlertKind.fromFirestoreValue(it.kind)?.let { kind ->
                SafetyAlert(
                    id = it.id,
                    kind = kind,
                    timestamp = Instant.ofEpochMilli(it.timestampEpochMillis),
                    speedMetersPerSecond = it.speedMetersPerSecond,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    note = it.note,
                )
            }
        },
        route = route.map {
            RoutePoint(
                id = it.id,
                latitude = it.latitude,
                longitude = it.longitude,
                timestamp = Instant.ofEpochMilli(it.timestampEpochMillis),
            )
        },
    )
