package org.rsmod.api.stats.plugin.levelup

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class LevelUpStatCacheTest {
    private fun stat(name: String): LevelUpStat =
        LevelUpStat.all.single { it.stat.internalName == "stat.$name" }

    private fun jingle(
        name: String,
        level: Int,
        unlocks: Boolean = false,
        guideList: Boolean = true,
    ): Int = stat(name).jingle.select(level, maxLevel = 99, unlocks, guideList)

    @Test
    fun `every skill has a distinct stat and levelup_display layer`() {
        val interfaceId = "interface.levelup_display".asRSCM(RSCMType.INTERFACE)
        assertEquals(24, LevelUpStat.all.size)
        assertEquals(24, LevelUpStat.all.map { it.stat.id }.toSet().size)
        assertEquals(24, LevelUpStat.all.map { it.layer.packed }.toSet().size)
        assertTrue(LevelUpStat.all.all { it.layer.interfaceId == interfaceId })
    }

    @Test
    fun `labels and level wording`() {
        assertEquals("Runecraft", stat("runecrafting").label)
        assertEquals("Your Runecraft level is now", stat("runecrafting").levelPrefix)
        assertEquals("Your Hitpoints are now", stat("hitpoints").levelPrefix)
        assertEquals("Your Woodcutting level is now", stat("woodcutting").levelPrefix)
    }

    @Test
    fun `unlock levels play the second jingle only with the guide list enabled`() {
        assertEquals(69, jingle("woodcutting", 14))
        assertEquals(70, jingle("woodcutting", 15, unlocks = true))
        assertEquals(
            69,
            jingle("woodcutting", 15, unlocks = true, guideList = false),
        )
        assertEquals(11, jingle("farming", 15, unlocks = true))
    }

    @Test
    fun `smithing follows the guide list setting alone`() {
        assertEquals(64, jingle("smithing", 2))
        assertEquals(63, jingle("smithing", 2, guideList = false))
    }

    @Test
    fun `special jingle rules`() {
        assertEquals(28, jingle("agility", 62, unlocks = true))
        assertEquals(47, jingle("hitpoints", 49))
        assertEquals(48, jingle("hitpoints", 50))
        assertEquals(65, jingle("strength", 2))
        assertEquals(66, jingle("strength", 99))
        assertEquals(49, jingle("hunter", 2))
        assertEquals(50, jingle("hunter", 3))
        assertEquals(31, jingle("construction", 11))
        assertEquals(32, jingle("construction", 20))
        assertEquals(830, jingle("sailing", 98))
        assertEquals(831, jingle("sailing", 99))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
