package com.vlad8110.teendrive

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.vlad8110.teendrive.firebase.safetyAlertFromFirestore
import com.vlad8110.teendrive.firebase.toNotificationEventFirestoreMap
import com.vlad8110.teendrive.firebase.toFirestoreMap
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SafetyAlertKind
import com.vlad8110.teendrive.model.TeenTrip
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreMapperTest {
    @Test
    fun safetyAlertUsesIosFirestoreKindValues() {
        val alert = SafetyAlert(
            id = "alert-1",
            kind = SafetyAlertKind.HARSH_CORNERING,
            timestamp = Instant.ofEpochSecond(1_700_000_000),
            speedMetersPerSecond = 20.0,
            latitude = 25.0,
            longitude = -80.0,
            note = "Hard turn",
        )

        val map = alert.toFirestoreMap()
        val decoded = safetyAlertFromFirestore(map)

        assertEquals("harshCornering", map["kind"])
        assertEquals(alert, decoded)
    }

    @Test
    fun tripFirestorePayloadMatchesIosPathFields() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        val trip = TeenTrip(
            id = UUID.randomUUID().toString(),
            startedAt = startedAt,
            endedAt = startedAt.plusSeconds(600),
            distanceMeters = 1_000.0,
            topSpeedMetersPerSecond = 22.0,
        )

        val map = trip.toFirestoreMap(teenId = "teen-1", familyGroupId = "family-1")

        assertEquals("teen-1", map["teenID"])
        assertEquals("family-1", map["familyGroupID"])
        assertEquals(600.0, map["durationSeconds"])
        assertTrue(map["syncedAt"] is FieldValue)
        assertEquals(startedAt, (map["startedAt"] as Timestamp).toInstant())
        assertNotNull(Timestamp(Date()).toInstant())
    }

    @Test
    fun notificationEventPayloadMatchesCloudFunctionFields() {
        val timestamp = Instant.ofEpochSecond(1_700_000_123)
        val alert = SafetyAlert(
            id = "alert-1",
            kind = SafetyAlertKind.SPEED_LIMIT,
            timestamp = timestamp,
            speedMetersPerSecond = 20.0,
            latitude = 26.0,
            longitude = -81.0,
            note = "Fallback limit exceeded",
        )

        val map = alert.toNotificationEventFirestoreMap(
            teenId = "teen-1",
            familyGroupId = "family-1",
        )

        assertEquals("speedLimit", map["kind"])
        assertEquals("Speed over limit", map["title"])
        assertEquals("45 mph", map["body"])
        assertEquals("teen-1", map["teenID"])
        assertEquals("family-1", map["familyGroupID"])
        assertEquals(timestamp, (map["timestamp"] as Timestamp).toInstant())
        assertEquals(20.0, map["speedMetersPerSecond"])
        assertEquals(26.0, map["latitude"])
        assertEquals(-81.0, map["longitude"])
        assertEquals("Fallback limit exceeded", map["note"])
        assertTrue(map["createdAt"] is FieldValue)
    }
}
