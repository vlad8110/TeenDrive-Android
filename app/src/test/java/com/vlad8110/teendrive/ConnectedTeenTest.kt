package com.vlad8110.teendrive

import com.vlad8110.teendrive.model.ConnectedTeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedTeenTest {
    @Test
    fun connectedTeenRoundTripsThroughDataStoreEncoding() {
        val teen = ConnectedTeen(
            id = "local-1",
            name = "Alex",
            pairingCode = "123456",
            teenProfileId = "teen-1",
            familyGroupId = "family-1",
        )

        assertEquals(teen, ConnectedTeen.decode(teen.encode()))
    }

    @Test
    fun invalidConnectedTeenEncodingReturnsNull() {
        assertNull(ConnectedTeen.decode("too|short"))
    }
}
