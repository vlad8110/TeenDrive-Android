package com.vlad8110.teendrive

import com.vlad8110.teendrive.data.toTeenTrip
import com.vlad8110.teendrive.location.ActiveDriveSnapshot
import com.vlad8110.teendrive.location.ActiveDriveSessionStore
import com.vlad8110.teendrive.location.LocationSnapshot
import com.vlad8110.teendrive.location.distanceMeters
import com.vlad8110.teendrive.model.RoutePoint
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveDriveSessionTest {
    @After
    fun tearDown() {
        ActiveDriveSessionStore.stop()
    }

    @Test
    fun distanceMetersCalculatesExpectedCityBlockScale() {
        val meters = distanceMeters(
            startLatitude = 25.7617,
            startLongitude = -80.1918,
            endLatitude = 25.7627,
            endLongitude = -80.1918,
        )

        assertEquals(111.0, meters, 2.0)
    }

    @Test
    fun sessionAccumulatesRouteDistanceAndTopSpeed() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        ActiveDriveSessionStore.start(startedAt)

        val first = ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.7617, -80.1918, 10.0, 5f, "test"),
            timestamp = startedAt.plusSeconds(1),
        )
        val second = ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.7627, -80.1918, 15.0, 5f, "test"),
            timestamp = startedAt.plusSeconds(11),
        )

        assertFalse(first.alertReady)
        assertTrue(second.alertReady)
        assertEquals(2, second.route.size)
        assertEquals(15.0, second.topSpeedMetersPerSecond, 0.001)
        assertTrue(second.distanceMeters > 100.0)
        assertEquals(11, second.duration.seconds)
    }

    @Test
    fun stopUsesActualStopTimeForCompletedDuration() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        ActiveDriveSessionStore.start(startedAt)
        ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.7617, -80.1918, 0.0, 5f, "test"),
            timestamp = startedAt.plusSeconds(4),
        )

        val completed = ActiveDriveSessionStore.stop(startedAt.plusSeconds(90))

        assertEquals(90L, completed?.duration?.seconds)
    }

    @Test
    fun sessionKeepsLatestAvailableSpeedLimit() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        ActiveDriveSessionStore.start(startedAt)

        val first = ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.7617, -80.1918, 10.0, 5f, "test", speedLimitMetersPerSecond = 13.4),
            timestamp = startedAt.plusSeconds(1),
        )
        val second = ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.7627, -80.1918, 10.0, 5f, "test"),
            timestamp = startedAt.plusSeconds(5),
        )

        assertEquals(13.4, first.speedLimitMetersPerSecond!!, 0.001)
        assertEquals(13.4, second.speedLimitMetersPerSecond!!, 0.001)
    }

    @Test
    fun completedSnapshotMapsToTeenTrip() {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        val endedAt = startedAt.plusSeconds(60)
        val route = listOf(
            RoutePoint(latitude = 25.7617, longitude = -80.1918, timestamp = startedAt),
            RoutePoint(latitude = 25.7627, longitude = -80.1918, timestamp = endedAt),
        )
        val snapshot = ActiveDriveSnapshot(
            id = "trip-1",
            startedAt = startedAt,
            updatedAt = endedAt,
            route = route,
            distanceMeters = 111.0,
            topSpeedMetersPerSecond = 20.0,
            currentSpeedMetersPerSecond = 0.0,
            speedLimitMetersPerSecond = null,
            speedAlerts = emptyList(),
            safetyAlerts = emptyList(),
            alertReady = true,
        )

        val trip = snapshot.toTeenTrip()

        assertEquals("trip-1", trip.id)
        assertEquals(startedAt, trip.startedAt)
        assertEquals(endedAt, trip.endedAt)
        assertEquals(route, trip.route)
        assertEquals(111.0, trip.distanceMeters, 0.001)
        assertEquals(20.0, trip.topSpeedMetersPerSecond, 0.001)
    }

    @Test
    fun completedTripIncludesDetectedAlerts() {
        val startedAt = Instant.parse("2026-05-12T16:00:00Z")
        ActiveDriveSessionStore.start(startedAt)

        ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.0, -80.0, 5.0, 5f, "test"),
            timestamp = startedAt,
        )
        val snapshot = ActiveDriveSessionStore.append(
            location = LocationSnapshot(25.001, -80.0, 36.0, 5f, "test"),
            timestamp = startedAt.plusSeconds(3),
        )

        val trip = snapshot.toTeenTrip()

        assertTrue(trip.safetyAlerts.isNotEmpty())
        assertTrue(trip.speedAlerts.isNotEmpty())
    }
}
