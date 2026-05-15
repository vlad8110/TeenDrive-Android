package com.vlad8110.teendrive.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.model.ActiveTeenDrive
import com.vlad8110.teendrive.model.ParentProfile
import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SafetyAlertKind
import com.vlad8110.teendrive.model.SpeedAlert
import com.vlad8110.teendrive.model.TeenProfile
import com.vlad8110.teendrive.model.TeenTrip
import java.time.Instant
import java.util.Date

fun Instant.toFirebaseTimestamp(): Timestamp = Timestamp(Date.from(this))

fun RoutePoint.toFirestoreMap(): Map<String, Any> =
    mapOf(
        "id" to id,
        "latitude" to latitude,
        "longitude" to longitude,
        "timestamp" to timestamp.toFirebaseTimestamp(),
    )

fun routePointFromFirestore(data: Map<String, Any?>): RoutePoint? {
    val latitude = data["latitude"].asDoubleOrNull() ?: return null
    val longitude = data["longitude"].asDoubleOrNull() ?: return null
    val timestamp = (data["timestamp"] as? Timestamp)?.toInstant() ?: Instant.now()
    return RoutePoint(
        id = data["id"] as? String ?: java.util.UUID.randomUUID().toString(),
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
    )
}

fun SpeedAlert.toFirestoreMap(): Map<String, Any> =
    mapOf(
        "id" to id,
        "timestamp" to timestamp.toFirebaseTimestamp(),
        "speedMetersPerSecond" to speedMetersPerSecond,
        "latitude" to latitude,
        "longitude" to longitude,
    )

fun speedAlertFromFirestore(data: Map<String, Any?>): SpeedAlert? {
    val speedMetersPerSecond = data["speedMetersPerSecond"].asDoubleOrNull() ?: return null
    val latitude = data["latitude"].asDoubleOrNull() ?: return null
    val longitude = data["longitude"].asDoubleOrNull() ?: return null
    val timestamp = (data["timestamp"] as? Timestamp)?.toInstant() ?: Instant.now()
    return SpeedAlert(
        id = data["id"] as? String ?: java.util.UUID.randomUUID().toString(),
        timestamp = timestamp,
        speedMetersPerSecond = speedMetersPerSecond,
        latitude = latitude,
        longitude = longitude,
    )
}

fun SafetyAlert.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "kind" to kind.firestoreValue,
        "timestamp" to timestamp.toFirebaseTimestamp(),
        "speedMetersPerSecond" to speedMetersPerSecond,
        "latitude" to latitude,
        "longitude" to longitude,
        "note" to note,
    )

fun safetyAlertFromFirestore(data: Map<String, Any?>): SafetyAlert? {
    val kindValue = data["kind"] as? String ?: return null
    val kind = SafetyAlertKind.fromFirestoreValue(kindValue) ?: return null
    val timestamp = (data["timestamp"] as? Timestamp)?.toInstant() ?: Instant.now()
    return SafetyAlert(
        id = data["id"] as? String ?: java.util.UUID.randomUUID().toString(),
        kind = kind,
        timestamp = timestamp,
        speedMetersPerSecond = data["speedMetersPerSecond"].asDoubleOrNull(),
        latitude = data["latitude"].asDoubleOrNull(),
        longitude = data["longitude"].asDoubleOrNull(),
        note = data["note"] as? String,
    )
}

fun TeenTrip.toFirestoreMap(teenId: String, familyGroupId: String): Map<String, Any?> =
    mapOf(
        "id" to id,
        "teenID" to teenId,
        "familyGroupID" to familyGroupId,
        "startedAt" to startedAt.toFirebaseTimestamp(),
        "endedAt" to endedAt.toFirebaseTimestamp(),
        "distanceMeters" to distanceMeters,
        "durationSeconds" to duration.seconds.toDouble(),
        "topSpeedMetersPerSecond" to topSpeedMetersPerSecond,
        "topSpeedMPH" to topSpeedMph,
        "speedAlerts" to speedAlerts.map { it.toFirestoreMap() },
        "safetyAlerts" to safetyAlerts.map { it.toFirestoreMap() },
        "route" to route.map { it.toFirestoreMap() },
        "syncedAt" to FieldValue.serverTimestamp(),
    )

fun teenTripFromFirestore(id: String, data: Map<String, Any?>): TeenTrip? {
    val startedAt = (data["startedAt"] as? Timestamp)?.toInstant() ?: return null
    val endedAt = (data["endedAt"] as? Timestamp)?.toInstant() ?: return null
    val distanceMeters = data["distanceMeters"].asDoubleOrNull() ?: return null
    val topSpeedMetersPerSecond = data["topSpeedMetersPerSecond"].asDoubleOrNull() ?: return null
    val speedAlerts = data.listOfMaps("speedAlerts").mapNotNull(::speedAlertFromFirestore)
    val safetyAlerts = data.listOfMaps("safetyAlerts").mapNotNull(::safetyAlertFromFirestore)
    val route = data.listOfMaps("route").mapNotNull(::routePointFromFirestore)
    return TeenTrip(
        id = data["id"] as? String ?: id,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceMeters = distanceMeters,
        topSpeedMetersPerSecond = topSpeedMetersPerSecond,
        speedAlerts = speedAlerts,
        safetyAlerts = safetyAlerts,
        route = route,
    )
}

fun TeenProfile.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "familyGroupID" to familyGroupId,
        "displayName" to displayName,
        "connectedParentIDs" to connectedParentIds,
        "fcmToken" to fcmToken,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

fun ParentProfile.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "displayName" to displayName,
        "familyGroupIDs" to familyGroupIds,
        "connectedTeenIDs" to connectedTeenIds,
        "fcmToken" to fcmToken,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

fun activeTeenDriveFromFirestore(
    teenProfileId: String,
    familyGroupId: String,
    teenName: String,
    data: Map<String, Any?>,
): ActiveTeenDrive? {
    if (data["isActive"] as? Boolean != true) return null
    val startedAt = (data["startedAt"] as? Timestamp)?.toInstant() ?: Instant.now()
    val updatedAt = (data["updatedAt"] as? Timestamp)?.toInstant() ?: startedAt
    val lastKnownLocation = (data["lastKnownLocation"] as? Map<*, *>)
        ?.entries
        ?.associate { it.key.toString() to it.value }
        ?.let(::routePointFromFirestore)
    return ActiveTeenDrive(
        teenProfileId = teenProfileId,
        familyGroupId = familyGroupId,
        teenName = teenName,
        startedAt = startedAt,
        updatedAt = updatedAt,
        speedMetersPerSecond = data["speedMetersPerSecond"].asDoubleOrNull() ?: 0.0,
        topSpeedMetersPerSecond = data["topSpeedMetersPerSecond"].asDoubleOrNull() ?: 0.0,
        distanceMeters = data["distanceMeters"].asDoubleOrNull() ?: 0.0,
        alertCount = (data["alertCount"] as? Number)?.toInt() ?: 0,
        lastKnownLocation = lastKnownLocation,
        safetyAlerts = data.listOfMaps("safetyAlerts").mapNotNull(::safetyAlertFromFirestore),
        route = data.listOfMaps("route").mapNotNull(::routePointFromFirestore),
    )
}

fun ActiveDriveSnapshot.toActiveDriveFirestoreMap(
    teenId: String,
    familyGroupId: String,
    teenName: String,
): Map<String, Any?> =
    mapOf(
        "isActive" to true,
        "teenID" to teenId,
        "familyGroupID" to familyGroupId,
        "teenName" to teenName,
        "startedAt" to startedAt.toFirebaseTimestamp(),
        "updatedAt" to updatedAt.toFirebaseTimestamp(),
        "speedMetersPerSecond" to currentSpeedMetersPerSecond,
        "speedLimitMetersPerSecond" to speedLimitMetersPerSecond,
        "topSpeedMetersPerSecond" to topSpeedMetersPerSecond,
        "distanceMeters" to distanceMeters,
        "alertCount" to safetyAlerts.count { it.kind.countsAsSafetyAlert },
        "lastKnownLocation" to route.lastOrNull()?.toFirestoreMap(),
        "safetyAlerts" to safetyAlerts.takeLast(60).map { it.toFirestoreMap() },
        "route" to route.takeLast(60).map { it.toFirestoreMap() },
    )

private fun Map<String, Any?>.listOfMaps(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)
        ?.mapNotNull { item ->
            (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        }
        ?: emptyList()

private fun Any?.asDoubleOrNull(): Double? =
    (this as? Number)?.toDouble()
