package com.vlad8110.teendrive

import com.vlad8110.teendrive.firebase.TripSyncResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TripSyncTest {
    @Test
    fun syncResultSummarizesWork() {
        val result = TripSyncResult(
            uploadedTrips = 2,
            syncedDeletes = 1,
            skippedTrips = 3,
        )

        assertEquals("Uploaded 2 • Deleted 1 • Skipped 3", result.message)
    }
}
