package com.vlad8110.teendrive.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import kotlinx.coroutines.tasks.await

class ActiveDriveSyncRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun publishActiveDrive(
        snapshot: ActiveDriveSnapshot,
        teenId: String,
        familyGroupId: String,
        teenName: String,
    ) {
        activeDriveDocument(familyGroupId, teenId)
            .set(
                snapshot.toActiveDriveFirestoreMap(
                    teenId = teenId,
                    familyGroupId = familyGroupId,
                    teenName = teenName,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun clearActiveDrive(teenId: String, familyGroupId: String) {
        activeDriveDocument(familyGroupId, teenId)
            .set(mapOf("isActive" to false), SetOptions.merge())
            .await()
    }

    private fun activeDriveDocument(familyGroupId: String, teenId: String) =
        firestore.collection("familyGroups")
            .document(familyGroupId)
            .collection("teens")
            .document(teenId)
            .collection("activeDrive")
            .document("current")
}
