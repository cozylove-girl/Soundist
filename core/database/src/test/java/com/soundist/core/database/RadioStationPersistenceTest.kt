package com.soundist.core.database

import com.soundist.core.model.RadioSourceKind
import com.soundist.core.model.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioStationPersistenceTest {
    @Test fun opaquePayloadRoundTripsWithoutInterpretation() {
        val payload = """{"version":1,"generator":{"bpm":72,"seed":"night"},"tracks":[{"uri":"content://track/1","gain":0.65}],"localAudio":{"uri":"content://audio/42","persisted":true}}"""
        val source = RadioStation(
            id = "generated-night",
            name = "Night generator",
            streamUri = "soundist://generator/generated-night",
            artworkUri = null,
            isFavorite = true,
            sourceKind = RadioSourceKind.GENERATED,
            updatedAt = 42L,
            payloadJson = payload,
        )

        assertEquals(source, radioStation(source.entity()))
        assertEquals(payload, source.entity().payloadJson)
    }

    @Test fun legacyStationWithoutPayloadRemainsCompatible() {
        val legacy = RadioEntity("legacy", "Legacy", "https://example.test/radio", null, false, "", "CUSTOM", "", "", 0.0, true, 1L)
        assertNull(radioStation(legacy).payloadJson)
    }
}
