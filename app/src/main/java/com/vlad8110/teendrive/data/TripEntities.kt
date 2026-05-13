package com.vlad8110.teendrive.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "completed_trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val distanceMeters: Double,
    val topSpeedMetersPerSecond: Double,
    val syncedAtEpochMillis: Long?,
)

@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class RoutePointEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMillis: Long,
)

@Entity(
    tableName = "speed_alerts",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class SpeedAlertEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val timestampEpochMillis: Long,
    val speedMetersPerSecond: Double,
    val latitude: Double,
    val longitude: Double,
)

@Entity(
    tableName = "safety_alerts",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class SafetyAlertEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val kind: String,
    val timestampEpochMillis: Long,
    val speedMetersPerSecond: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val note: String?,
)

@Entity(tableName = "deleted_trip_tombstones")
data class DeletedTripTombstoneEntity(
    @PrimaryKey val tripId: String,
    val deletedAtEpochMillis: Long,
    val syncedAtEpochMillis: Long?,
)
