package com.soundist.feature.listening

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomRadioImportTest {
    @Test fun extension_is_preserved_and_normalized() {
        assertEquals("mp3", audioFileExtension("My Song.MP3"))
        assertEquals("flac", audioFileExtension("track.FLAC"))
        assertEquals("wav", audioFileExtension("field-recording.wav"))
        assertEquals("ogg", audioFileExtension("no-extension"))
        assertEquals("ogg", audioFileExtension("weird.name with spaces"))
        assertEquals("ogg", audioFileExtension("hidden."))
    }

    @Test fun sha256_hex_is_lowercase_and_64_chars() {
        // SHA-256("hello") == 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val digest = sha256Hex("hello".toByteArray(Charsets.UTF_8))
        assertEquals(64, digest.length)
        assertEquals(digest.lowercase(), digest)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", digest)
    }
}
