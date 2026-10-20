package org.rsmod.content.skills.magic.spell.teleports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTeleportCooldownTest {
    @Test
    fun `never cast is ready`() {
        assertEquals(0, HomeTeleportCooldown.minutesLeft(lastCastMinute = 0, nowMinute = NOW))
    }

    @Test
    fun `just cast waits the full thirty minutes`() {
        assertEquals(30, HomeTeleportCooldown.minutesLeft(lastCastMinute = NOW, nowMinute = NOW))
    }

    @Test
    fun `counts down a minute at a time`() {
        assertEquals(29, HomeTeleportCooldown.minutesLeft(NOW - 1, NOW))
        assertEquals(1, HomeTeleportCooldown.minutesLeft(NOW - 29, NOW))
    }

    @Test
    fun `ready again at exactly thirty minutes`() {
        assertEquals(0, HomeTeleportCooldown.minutesLeft(NOW - 30, NOW))
        assertEquals(0, HomeTeleportCooldown.minutesLeft(NOW - 1000, NOW))
    }

    @Test
    fun `a cast stamped in the future never waits more than thirty minutes`() {
        assertEquals(30, HomeTeleportCooldown.minutesLeft(NOW + 500, NOW))
    }

    @Test
    fun `message uses singular for one minute`() {
        assertEquals("You need to wait another 1 minute to cast this spell.", HomeTeleportCooldown.message(1))
        assertEquals("You need to wait another 12 minutes to cast this spell.", HomeTeleportCooldown.message(12))
    }

    private companion object {
        const val NOW = 29_500_000
    }
}
