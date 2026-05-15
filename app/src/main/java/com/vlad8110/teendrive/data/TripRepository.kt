package com.vlad8110.teendrive.data

import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.model.TeenTrip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class TripRepository(
    private val tripDao: TripDao,
) {
    fun observeTrips(): Flow<List<TeenTrip>> =
        tripDao.observeTrips().map { trips -> trips.map { it.toModel() } }

    suspend fun unsyncedTrips(): List<TeenTrip> {
        val tombstonedIds = tripDao.unsyncedTombstones().map { it.tripId }.toSet()
        return tripDao.unsyncedTrips()
            .filterNot { it.trip.id in tombstonedIds }
            .map { it.toModel() }
    }

    suspend fun unsyncedTombstones(): List<DeletedTripTombstoneEntity> =
        tripDao.unsyncedTombstones()

    suspend fun saveCompletedDrive(snapshot: ActiveDriveSnapshot): TeenTrip {
        val trip = snapshot.toTeenTrip()
        tripDao.upsertTripWithDetails(
            trip = trip.toEntity(),
            route = trip.route.map { it.toEntity(trip.id) },
            speedAlerts = trip.speedAlerts.map { it.toEntity(trip.id) },
            safetyAlerts = trip.safetyAlerts.map { it.toEntity(trip.id) },
        )
        return trip
    }

    suspend fun markTripSynced(tripId: String, syncedAt: Instant = Instant.now()) {
        tripDao.markTripSynced(tripId, syncedAt.toEpochMilli())
    }

    suspend fun markTombstoneSynced(tripId: String, syncedAt: Instant = Instant.now()) {
        tripDao.markTombstoneSynced(tripId, syncedAt.toEpochMilli())
    }
}
