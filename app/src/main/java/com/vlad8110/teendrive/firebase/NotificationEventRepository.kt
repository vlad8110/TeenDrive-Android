package com.vlad8110.teendrive.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vlad8110.teendrive.model.SafetyAlert
import kotlinx.coroutines.tasks.await

class NotificationEventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun writeParentSafetyAlertEvent(
        alert: SafetyAlert,
        teenId: String,
        familyGroupId: String,
    ) {
        if (!alert.kind.countsAsSafetyAlert || teenId.isBlank() || familyGroupId.isBlank()) return

        firestore.collection("notificationEvents")
            .document(alert.id)
            .set(
                alert.toNotificationEventFirestoreMap(
                    teenId = teenId,
                    familyGroupId = familyGroupId,
                ),
                SetOptions.merge(),
            )
            .await()
    }
}

fun SafetyAlert.toNotificationEventFirestoreMap(
    teenId: String,
    familyGroupId: String,
): Map<String, Any?> =
    mapOf(
        "kind" to kind.firestoreValue,
        "title" to kind.title,
        "body" to displayText,
        "teenID" to teenId,
        "familyGroupID" to familyGroupId,
        "timestamp" to timestamp.toFirebaseTimestamp(),
        "speedMetersPerSecond" to speedMetersPerSecond,
        "latitude" to latitude,
        "longitude" to longitude,
        "note" to note,
        "createdAt" to FieldValue.serverTimestamp(),
    )
