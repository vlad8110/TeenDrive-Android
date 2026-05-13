package com.vlad8110.teendrive

import com.vlad8110.teendrive.location.SafetyDetectionInput
import com.vlad8110.teendrive.location.SafetyDetector
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
}
