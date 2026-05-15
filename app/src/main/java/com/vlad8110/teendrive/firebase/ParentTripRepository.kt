package com.vlad8110.teendrive.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.vlad8110.teendrive.model.ActiveTeenDrive
import com.vlad8110.teendrive.model.ConnectedTeen
import com.vlad8110.teendrive.model.TeenTrip
import kotlinx.coroutines.tasks.await

data class ParentTeenTrip(
    val teen: ConnectedTeen,
    val trip: TeenTrip,
)

class ParentTripRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    fun listenToConnectedTeens(
        connectedTeens: List<ConnectedTeen>,
        limitPerTeen: Long = 20,
        onTripsChanged: (teenProfileId: String, trips: List<ParentTeenTrip>) -> Unit,
        onActiveDriveChanged: (teenProfileId: String, activeDrive: ActiveTeenDrive?) -> Unit,
        onError: (Throwable) -> Unit,
    ): List<ListenerRegistration> {
        return connectedTeens.flatMap { teen ->
            if (teen.familyGroupId.isBlank() || teen.teenProfileId.isBlank()) {
                emptyList()
            } else {
                val tripsListener = firestore.collection("familyGroups")
                    .document(teen.familyGroupId)
                    .collection("teens")
                    .document(teen.teenProfileId)
                    .collection("trips")
                    .orderBy("startedAt", Query.Direction.DESCENDING)
                    .limit(limitPerTeen)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            onError(error)
                        } else {
                            val trips = snapshot?.documents.orEmpty().mapNotNull { document ->
                                teenTripFromFirestore(document.id, document.data.orEmpty())
                                    ?.let { ParentTeenTrip(teen = teen, trip = it) }
                            }
                            onTripsChanged(teen.teenProfileId, trips)
                        }
                    }

                val activeDriveListener = firestore.collection("familyGroups")
                    .document(teen.familyGroupId)
                    .collection("teens")
                    .document(teen.teenProfileId)
                    .collection("activeDrive")
                    .document("current")
                    .addSnapshotListener { document, error ->
                        if (error != null) {
                            onError(error)
                        } else {
                            onActiveDriveChanged(
                                teen.teenProfileId,
                                activeTeenDriveFromFirestore(
                                    teenProfileId = teen.teenProfileId,
                                    familyGroupId = teen.familyGroupId,
                                    teenName = teen.name,
                                    data = document?.data.orEmpty(),
                                ),
                            )
                        }
                    }

                listOf(tripsListener, activeDriveListener)
            }
        }
    }

    suspend fun fetchTripsForConnectedTeens(
        connectedTeens: List<ConnectedTeen>,
        limitPerTeen: Long = 20,
    ): List<ParentTeenTrip> {
        return connectedTeens.flatMap { teen ->
            if (teen.familyGroupId.isBlank() || teen.teenProfileId.isBlank()) {
                emptyList()
            } else {
                firestore.collection("familyGroups")
                    .document(teen.familyGroupId)
                    .collection("teens")
                    .document(teen.teenProfileId)
                    .collection("trips")
                    .orderBy("startedAt", Query.Direction.DESCENDING)
                    .limit(limitPerTeen)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        teenTripFromFirestore(document.id, document.data.orEmpty())
                            ?.let { ParentTeenTrip(teen = teen, trip = it) }
                    }
            }
        }.sortedByDescending { it.trip.startedAt }
    }

    suspend fun fetchActiveDrivesForConnectedTeens(
        connectedTeens: List<ConnectedTeen>,
    ): List<ActiveTeenDrive> {
        return connectedTeens.mapNotNull { teen ->
            if (teen.familyGroupId.isBlank() || teen.teenProfileId.isBlank()) {
                null
            } else {
                val document = firestore.collection("familyGroups")
                    .document(teen.familyGroupId)
                    .collection("teens")
                    .document(teen.teenProfileId)
                    .collection("activeDrive")
                    .document("current")
                    .get()
                    .await()
                activeTeenDriveFromFirestore(
                    teenProfileId = teen.teenProfileId,
                    familyGroupId = teen.familyGroupId,
                    teenName = teen.name,
                    data = document.data.orEmpty(),
                )
            }
        }.sortedByDescending { it.updatedAt }
    }
}
