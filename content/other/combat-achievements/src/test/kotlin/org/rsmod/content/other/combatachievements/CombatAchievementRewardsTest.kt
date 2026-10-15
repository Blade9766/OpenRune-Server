package org.rsmod.content.other.combatachievements

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class CombatAchievementRewardsTest {
    @Test
    fun `every lamp skill button resolves to a stat`() {
        val stats = (0 until 24).map { lampStat(it) }
        assertTrue(stats.all { it != null && it.startsWith("stat.") }, stats.toString())
        assertEquals(24, stats.toSet().size)
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
