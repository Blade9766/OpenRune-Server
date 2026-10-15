package org.rsmod.api.player

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class JingleGroupCacheTest {
    @Test
    fun `named jingles resolve to their archive 11 group`() {
        assertEquals(76, jingleGroup("jingle.arena_wave_complete"))
        assertEquals(133, jingleGroup("jingle.mizgog_cutscene"))
        assertEquals(139, jingleGroup("jingle.open_light_chest"))
        assertEquals(103, jingleGroup("jingle.first_sunshine"))
        assertEquals(27, jingleGroup("jingle.zaniks_theme"))
        assertEquals(202, jingleGroup("jingle.zaniks_resurrection"))
        assertEquals(171, jingleGroup("jingle.sailing_journey_short"))
        assertEquals(249, jingleGroup("jingle.air_guitar_jingle"))
    }

    @Test
    fun `jingles without a known group resolve to nothing`() {
        assertNull(jingleGroup("jingle.grumpy"))
        assertNull(jingleGroup("jingle.quest_complete_1"))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
