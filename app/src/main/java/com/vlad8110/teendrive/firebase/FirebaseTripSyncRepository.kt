package com.vlad8110.teendrive.firebase

import com.google.firebase.Timestamp
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

        val resolvedFamilyGroupId = resolveParentLinkedFamilyGroupId(
            teenId = teenId,
            fallbackFamilyGroupId = familyGroupId,
        )
        val tombstones = tripRepository.unsyncedTombstones()
        val tombstonedIds = tombstones.map { it.tripId }.toSet()
        var syncedDeletes = 0
        for (tombstone in tombstones) {
            tripDocument(resolvedFamilyGroupId, teenId, tombstone.tripId)
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
            tripDocument(resolvedFamilyGroupId, teenId, trip.id)
                .set(trip.toFirestoreMap(teenId = teenId, familyGroupId = resolvedFamilyGroupId), SetOptions.merge())
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

    suspend fun resolveParentLinkedFamilyGroupId(
        teenId: String,
        fallbackFamilyGroupId: String,
    ): String {
        val fallbackFamily = firestore.collection("familyGroups")
            .document(fallbackFamilyGroupId)
            .get()
            .await()
            .data.orEmpty()
        if (fallbackFamily.stringList("parentIDs").isNotEmpty()) {
            return fallbackFamilyGroupId
        }

        val teenProfile = firestore.collection("teenProfiles").document(teenId).get().await().data.orEmpty()
        val linkedFamilyIds = teenProfile.stringList("connectedParentIDs").flatMap { parentId ->
            firestore.collection("parentProfiles").document(parentId).get().await()
                .data.orEmpty()
                .stringList("familyGroupIDs")
        }

        linkedFamilyIds
            .map { familyGroupId ->
                familyGroupId to firestore.collection("familyGroups").document(familyGroupId).get().await().data.orEmpty()
            }
            .filter { (_, family) ->
                teenId in family.stringList("teenIDs") && family.stringList("parentIDs").isNotEmpty()
            }
            .maxByOrNull { (_, family) -> family["updatedAt"] as? Timestamp ?: Timestamp(0, 0) }
            ?.first
            ?.let { return it }

        return firestore.collection("familyGroups")
            .whereArrayContains("teenIDs", teenId)
            .get()
            .await()
            .documents
            .map { it.id to it.data.orEmpty() }
            .filter { (_, family) -> family.stringList("parentIDs").isNotEmpty() }
            .maxByOrNull { (_, family) -> family["updatedAt"] as? Timestamp ?: Timestamp(0, 0) }
            ?.first
            ?: fallbackFamilyGroupId
    }

    private fun tripDocument(familyGroupId: String, teenId: String, tripId: String) =
        firestore.collection("familyGroups")
            .document(familyGroupId)
            .collection("teens")
            .document(teenId)
            .collection("trips")
            .document(tripId)
}

private fun Map<String, Any?>.stringList(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().mapNotNull { it as? String }.filter { it.isNotBlank() }
