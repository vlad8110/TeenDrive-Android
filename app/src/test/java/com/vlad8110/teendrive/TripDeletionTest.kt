package com.vlad8110.teendrive

import com.vlad8110.teendrive.data.DeletedTripTombstoneEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripDeletionTest {
    @Test
    fun tombstoneStartsUnsynced() {
        val deletedAt = Instant.ofEpochSecond(1_700_000_000)
        val tombstone = DeletedTripTombstoneEntity(
            tripId = "trip-1",
            deletedAtEpochMillis = deletedAt.toEpochMilli(),
            syncedAtEpochMillis = null,
        )

        assertEquals("trip-1", tombstone.tripId)
        assertEquals(deletedAt.toEpochMilli(), tombstone.deletedAtEpochMillis)
        assertNull(tombstone.syncedAtEpochMillis)
    }
}
