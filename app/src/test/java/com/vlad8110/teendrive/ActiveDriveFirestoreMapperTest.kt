package com.vlad8110.teendrive

import com.vlad8110.teendrive.firebase.toActiveDriveFirestoreMap
import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.model.RoutePoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveDriveFirestoreMapperTest {
    @Test
    fun activeDrivePayloadMatchesParentReadableShape() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        val snapshot = ActiveDriveSnapshot(
            id = "drive-1",
            startedAt = startedAt,
            updatedAt = startedAt.plusSeconds(30),
            route = listOf(
                RoutePoint(latitude = 25.0, longitude = -80.0, timestamp = startedAt),
            ),
            distanceMeters = 100.0,
            topSpeedMetersPerSecond = 15.0,
            currentSpeedMetersPerSecond = 12.0,
            speedLimitMetersPerSecond = 13.4,
            speedAlerts = emptyList(),
            safetyAlerts = emptyList(),
            alertReady = true,
        )

        val map = snapshot.toActiveDriveFirestoreMap(
            teenId = "teen-1",
            familyGroupId = "family-1",
            teenName = "Alex",
        )

        assertEquals(true, map["isActive"])
        assertEquals("teen-1", map["teenID"])
        assertEquals("family-1", map["familyGroupID"])
        assertEquals("Alex", map["teenName"])
        assertEquals(12.0, map["speedMetersPerSecond"])
        assertEquals(13.4, map["speedLimitMetersPerSecond"])
        assertEquals(15.0, map["topSpeedMetersPerSecond"])
        assertTrue(map["lastKnownLocation"] is Map<*, *>)
    }
}
