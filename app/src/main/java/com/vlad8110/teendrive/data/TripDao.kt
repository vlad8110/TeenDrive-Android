package com.vlad8110.teendrive.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TripWithDetails(
    @androidx.room.Embedded val trip: TripEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "tripId")
    val route: List<RoutePointEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "tripId")
    val speedAlerts: List<SpeedAlertEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "tripId")
    val safetyAlerts: List<SafetyAlertEntity>,
)

@Dao
interface TripDao {
    @Transaction
    @Query("SELECT * FROM completed_trips ORDER BY startedAtEpochMillis DESC")
    fun observeTrips(): Flow<List<TripWithDetails>>

    @Transaction
    @Query("SELECT * FROM completed_trips WHERE syncedAtEpochMillis IS NULL ORDER BY startedAtEpochMillis ASC")
    suspend fun unsyncedTrips(): List<TripWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutePoints(points: List<RoutePointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpeedAlerts(alerts: List<SpeedAlertEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafetyAlerts(alerts: List<SafetyAlertEntity>)

    @Transaction
    suspend fun upsertTripWithDetails(
        trip: TripEntity,
        route: List<RoutePointEntity>,
        speedAlerts: List<SpeedAlertEntity>,
        safetyAlerts: List<SafetyAlertEntity>,
    ) {
        upsertTrip(trip)
        upsertRoutePoints(route)
        upsertSpeedAlerts(speedAlerts)
        upsertSafetyAlerts(safetyAlerts)
    }

    @Query("SELECT * FROM deleted_trip_tombstones WHERE syncedAtEpochMillis IS NULL")
    suspend fun unsyncedTombstones(): List<DeletedTripTombstoneEntity>

    @Query("UPDATE completed_trips SET syncedAtEpochMillis = :syncedAtEpochMillis WHERE id = :tripId")
    suspend fun markTripSynced(tripId: String, syncedAtEpochMillis: Long)

    @Query("UPDATE deleted_trip_tombstones SET syncedAtEpochMillis = :syncedAtEpochMillis WHERE tripId = :tripId")
    suspend fun markTombstoneSynced(tripId: String, syncedAtEpochMillis: Long)
}
