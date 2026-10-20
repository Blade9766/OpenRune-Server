package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable

@ResourceLock("ServerCacheManager")
class StatSetLevelTest {
    @Test
    fun `raising lands on the exact level xp despite xp rates`() {
        val player = player(AGILITY, level = 70)
        player.statSetLevel(AGILITY, 90)
        assertLevel(player, 90)
        assertEquals(5_346_332, player.statMap.getXP(AGILITY))
    }

    @Test
    fun `lowering lands on the exact level xp`() {
        val player = player(AGILITY, level = 90)
        player.statSetLevel(AGILITY, 70)
        assertLevel(player, 70)
    }

    @Test
    fun `raising from mid-level xp and back again stays exact`() {
        val player = player(AGILITY, level = 70)
        player.statMap.setXP(AGILITY, PlayerSkillXPTable.getXPFromLevel(70) + 1234)
        player.statSetLevel(AGILITY, 79)
        assertLevel(player, 79)
        player.statSetLevel(AGILITY, 1)
        assertLevel(player, 1)
        player.statSetLevel(AGILITY, 99)
        assertLevel(player, 99)
    }

    @Test
    fun `a boosted current level is reset to the target`() {
        val player = player(AGILITY, level = 70)
        player.statMap.setCurrentLevel(AGILITY, 75.toByte())
        player.statSetLevel(AGILITY, 80)
        assertLevel(player, 80)
    }

    private fun player(stat: String, level: Int): Player =
        Player().apply {
            xpRate = 5.0
            globalXpRate = 5.0
            statMap.setXP(stat, PlayerSkillXPTable.getXPFromLevel(level))
            statMap.setBaseLevel(stat, level.toByte())
            statMap.setCurrentLevel(stat, level.toByte())
        }

    private fun assertLevel(player: Player, level: Int) {
        assertEquals(PlayerSkillXPTable.getXPFromLevel(level), player.statMap.getXP(AGILITY))
        assertEquals(level, player.statBase(AGILITY))
        assertEquals(level, player.stat(AGILITY))
    }

    private companion object {
        const val AGILITY = "stat.agility"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
