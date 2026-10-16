package org.rsmod.content.travel.wayfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WayfinderPlayerStateTest {
    @Test
    fun `recent destinations keep the newest first without duplicates`() {
        var packed = 0
        for (slot in listOf(5, 9, 5, 127, 1, 30)) {
            packed = WayfinderPlayerState.pushRecent(packed, slot)
        }
        assertEquals(listOf(30, 1, 127, 5), WayfinderPlayerState.unpackRecent(packed))
    }

    @Test
    fun `an empty recent list unpacks to nothing`() {
        assertEquals(emptyList<Int>(), WayfinderPlayerState.unpackRecent(0))
    }

    @Test
    fun `slots outside the saved range are refused`() {
        assertThrows<IllegalArgumentException> { WayfinderPlayerState.pushRecent(0, 0) }
        assertThrows<IllegalArgumentException> { WayfinderPlayerState.pushRecent(0, WayfinderRegistry.MAX_SLOT + 1) }
    }
}
