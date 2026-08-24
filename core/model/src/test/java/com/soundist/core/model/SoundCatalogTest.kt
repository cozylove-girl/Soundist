package com.soundist.core.model
import org.junit.Assert.assertEquals
import org.junit.Test
class SoundCatalogTest { @Test fun catalogueIsCompleteAndUnique(){ assertEquals(84,SoundCatalog.sounds.size);assertEquals(84,SoundCatalog.sounds.map{it.id}.distinct().size) } }
