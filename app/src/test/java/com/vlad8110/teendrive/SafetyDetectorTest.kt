package com.vlad8110.teendrive

import com.vlad8110.teendrive.location.SafetyDetectionInput
import com.vlad8110.teendrive.location.SafetyDetector
import com.vlad8110.teendrive.location.SavedPlace
import com.vlad8110.teendrive.model.RoutePoint
import com.vlad8110.teendrive.model.SafetyAlertKind
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyDetectorTest {
    @Test
    fun detectsRapidAccelerationAndSpeeding() {
        val now = Instant.parse("2026-05-12T16:00:00Z")
        val previous = RoutePoint(latitude = 25.0, longitude = -80.0, timestamp = now)
        val current = RoutePoint(latitude = 25.001, longitude = -80.0, timestamp = now.plusSeconds(3))

        val alerts = SafetyDetector.detect(
            SafetyDetectionInput(
                pointBeforePrevious = null,
                previousPoint = previous,
                previousSpeedMetersPerSecond = 5.0,
                currentPoint = current,
                currentSpeedMetersPerSecond = 36.0,
            ),
        )

        assertTrue(alerts.any { it.kind == SafetyAlertKind.RAPID_ACCELERATION })
        assertTrue(alerts.any { it.kind == SafetyAlertKind.SPEED_LIMIT })
    }

    @Test
    fun detectsHarshStop() {
        val now = Instant.parse("2026-05-12T16:00:00Z")
        val previous = RoutePoint(latitude = 25.0, longitude = -80.0, timestamp = now)
        val current = RoutePoint(latitude = 25.001, longitude = -80.0, timestamp = now.plusSeconds(3))

        val alerts = SafetyDetector.detect(
            SafetyDetectionInput(
                pointBeforePrevious = null,
                previousPoint = previous,
                previousSpeedMetersPerSecond = 25.0,
                currentPoint = current,
                currentSpeedMetersPerSecond = 5.0,
            ),
        )

        assertTrue(alerts.any { it.kind == SafetyAlertKind.HARSH_STOP })
    }

    @Test
    fun usesAvailableSpeedLimitForSpeedingAlert() {
        val now = Instant.parse("2026-05-12T16:00:00Z")
        val current = RoutePoint(latitude = 25.001, longitude = -80.0, timestamp = now)

        val alerts = SafetyDetector.detect(
            SafetyDetectionInput(
                pointBeforePrevious = null,
                previousPoint = null,
                previousSpeedMetersPerSecond = 0.0,
                currentPoint = current,
                currentSpeedMetersPerSecond = 18.0,
                speedLimitMetersPerSecond = 13.4,
            ),
        )

        assertTrue(alerts.any { it.kind == SafetyAlertKind.SPEED_LIMIT && it.note == "Over 29 mph speed limit" })
    }

    @Test
    fun detectsPhoneUnlockedWhileMoving() {
        val now = Instant.parse("2026-05-12T16:00:00Z")
        val current = RoutePoint(latitude = 25.001, longitude = -80.0, timestamp = now)

        val alerts = SafetyDetector.detect(
            SafetyDetectionInput(
                pointBeforePrevious = null,
                previousPoint = null,
                previousSpeedMetersPerSecond = 0.0,
                currentPoint = current,
                currentSpeedMetersPerSecond = 8.0,
                phoneUnlockedWhileMoving = true,
            ),
        )

        assertTrue(alerts.any { it.kind == SafetyAlertKind.PHONE_USE && it.note == "Phone unlocked while moving" })
    }

    @Test
    fun detectsArrivalAtSavedPlace() {
        val now = Instant.parse("2026-05-12T16:00:00Z")
        val previous = RoutePoint(latitude = 25.0000, longitude = -80.0000, timestamp = now)
        val current = RoutePoint(latitude = 25.0010, longitude = -80.0000, timestamp = now.plusSeconds(30))
        val school = SavedPlace(
            id = "school",
            name = "School",
            latitude = 25.0010,
            longitude = -80.0000,
            radiusMeters = 50.0,
        )

        val alerts = SafetyDetector.detect(
            SafetyDetectionInput(
                pointBeforePrevious = null,
                previousPoint = previous,
                previousSpeedMetersPerSecond = 8.0,
                currentPoint = current,
                currentSpeedMetersPerSecond = 2.0,
                savedPlaces = listOf(school),
            ),
        )

        assertTrue(alerts.any { it.kind == SafetyAlertKind.PLACE_ARRIVAL && it.note == "Arrived at School" })
    }
}
