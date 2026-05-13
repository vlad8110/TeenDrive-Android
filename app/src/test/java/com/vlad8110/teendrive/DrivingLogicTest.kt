package com.vlad8110.teendrive

import com.vlad8110.teendrive.model.PairingPayload
import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlert
import com.vlad8110.teendrive.model.SafetyAlertKind
import com.vlad8110.teendrive.model.SpeedAlert
import com.vlad8110.teendrive.model.TeenTrip
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingLogicTest {
    @Test
    fun lifecycleAlertsDoNotCountAsSafetyAlerts() {
        val trip = makeTrip(
            safetyAlerts = listOf(
                makeAlert(SafetyAlertKind.TRIP_STARTED),
                makeAlert(SafetyAlertKind.TRIP_ENDED),
                makeAlert(SafetyAlertKind.RAPID_ACCELERATION),
            ),
        )

        assertEquals(1, trip.safetyAlertCount)
        assertEquals(1, trip.rapidAccelerationAlertCount)
        assertEquals(listOf(SafetyAlertKind.RAPID_ACCELERATION), trip.displaySafetyAlerts.map { it.kind })
    }

    @Test
    fun legacySpeedAlertsBackfillDisplaySafetyAlerts() {
        val trip = makeTrip(
            speedAlerts = listOf(
                SpeedAlert(
                    id = UUID.randomUUID().toString(),
                    timestamp = Instant.now(),
                    speedMetersPerSecond = 24.0,
                    latitude = 25.76,
                    longitude = -80.19,
                ),
            ),
            safetyAlerts = emptyList(),
        )

        assertEquals(1, trip.safetyAlertCount)
        assertEquals(1, trip.speedLimitAlertCount)
        assertEquals(SafetyAlertKind.SPEED_LIMIT, trip.displaySafetyAlerts.first().kind)
    }

    @Test
    fun behaviorScorePenalizesRiskyDrivingEvents() {
        val trip = makeTrip(
            duration = Duration.ofMinutes(30),
            topSpeedMetersPerSecond = 38.0,
            safetyAlerts = listOf(
                makeAlert(SafetyAlertKind.SPEED_LIMIT),
                makeAlert(SafetyAlertKind.SPEED_LIMIT),
                makeAlert(SafetyAlertKind.HARSH_STOP),
                makeAlert(SafetyAlertKind.HARSH_CORNERING),
                makeAlert(SafetyAlertKind.PHONE_USE),
            ),
        )

        val breakdown = trip.behaviorScoreBreakdown

        assertTrue(breakdown.score < 100)
        assertEquals(10.0, breakdown.speedingPenalty, 0.001)
        assertEquals(14.0, breakdown.drivingEventPenalty, 0.001)
        assertEquals(4.0, breakdown.harshStopPenalty, 0.001)
        assertTrue(breakdown.alertRatePenalty > 0)
    }

    @Test
    fun mapBoundsIncludeSafetyAlertLocations() {
        val trip = makeTrip(
            safetyAlerts = listOf(makeAlert(SafetyAlertKind.SPEED_LIMIT, latitude = 26.0, longitude = -81.0)),
            route = listOf(RoutePoint(latitude = 25.0, longitude = -80.0, timestamp = Instant.now())),
        )

        assertEquals(25.5, trip.mapBounds.center.latitude, 0.001)
        assertEquals(-80.5, trip.mapBounds.center.longitude, 0.001)
        assertTrue(trip.mapBounds.latitudeDelta >= 1.5)
        assertTrue(trip.mapBounds.longitudeDelta >= 1.5)
    }

    @Test
    fun pairingPayloadMatchesIosUriShape() {
        val payload = PairingPayload(
            code = "123456",
            token = "abcdef",
            teenName = "Alex Teen",
            teenProfileId = "teen-1",
            familyGroupId = "family-1",
        )

        val parsed = PairingPayload.parse(payload.toUriString())

        assertNotNull(parsed)
        assertEquals(payload, parsed)
        assertFalse(PairingPayload.parse("https://example.com").let { it != null })
    }

    private fun makeTrip(
        duration: Duration = Duration.ofMinutes(15),
        topSpeedMetersPerSecond: Double = 20.0,
        speedAlerts: List<SpeedAlert> = emptyList(),
        safetyAlerts: List<SafetyAlert> = emptyList(),
        route: List<RoutePoint> = emptyList(),
    ): TeenTrip {
        val startedAt = Instant.ofEpochSecond(1_700_000_000)
        return TeenTrip(
            id = UUID.randomUUID().toString(),
            startedAt = startedAt,
            endedAt = startedAt.plus(duration),
            distanceMeters = 2_000.0,
            topSpeedMetersPerSecond = topSpeedMetersPerSecond,
            speedAlerts = speedAlerts,
            safetyAlerts = safetyAlerts,
            route = route,
        )
    }

    private fun makeAlert(
        kind: SafetyAlertKind,
        latitude: Double? = null,
        longitude: Double? = null,
    ): SafetyAlert =
        SafetyAlert(
            kind = kind,
            timestamp = Instant.ofEpochSecond(1_700_000_000),
            speedMetersPerSecond = 20.0,
            latitude = latitude,
            longitude = longitude,
            note = kind.title,
        )
}
