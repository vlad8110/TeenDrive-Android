package com.vlad8110.teendrive.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vlad8110.teendrive.data.AccountState
import com.vlad8110.teendrive.data.TripRepository
import kotlinx.coroutines.tasks.await

data class TripSyncResult(
    val uploadedTrips: Int,
    val syncedDeletes: Int,
    val skippedTrips: Int,
) {
    val message: String
        get() = "Uploaded $uploadedTrips • Deleted $syncedDeletes • Skipped $skippedTrips"
}

class FirebaseTripSyncRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun syncLocalTrips(
        accountState: AccountState,
        tripRepository: TripRepository,
    ): TripSyncResult {
        val teenId = accountState.teenProfileId
        val familyGroupId = accountState.familyGroupId
        if (teenId.isBlank() || familyGroupId.isBlank()) {
            return TripSyncResult(uploadedTrips = 0, syncedDeletes = 0, skippedTrips = 0)
        }

        val tombstones = tripRepository.unsyncedTombstones()
        val tombstonedIds = tombstones.map { it.tripId }.toSet()
        var syncedDeletes = 0
        for (tombstone in tombstones) {
            tripDocument(familyGroupId, teenId, tombstone.tripId)
                .delete()
                .await()
            tripRepository.markTombstoneSynced(tombstone.tripId)
            syncedDeletes += 1
        }

        var uploadedTrips = 0
        var skippedTrips = 0
        for (trip in tripRepository.unsyncedTrips()) {
            if (trip.id in tombstonedIds) {
                skippedTrips += 1
                continue
            }
            tripDocument(familyGroupId, teenId, trip.id)
                .set(trip.toFirestoreMap(teenId = teenId, familyGroupId = familyGroupId), SetOptions.merge())
                .await()
            tripRepository.markTripSynced(trip.id)
            uploadedTrips += 1
        }

        return TripSyncResult(
            uploadedTrips = uploadedTrips,
            syncedDeletes = syncedDeletes,
            skippedTrips = skippedTrips,
        )
    }

    private fun tripDocument(familyGroupId: String, teenId: String, tripId: String) =
        firestore.collection("familyGroups")
            .document(familyGroupId)
            .collection("teens")
            .document(teenId)
            .collection("trips")
            .document(tripId)
}
