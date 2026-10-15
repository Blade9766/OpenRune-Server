package org.rsmod.content.other.combatachievements

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.enums.enum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class CombatAchievementRewardsTest {
    @Test
    fun `every lamp skill button resolves to a stat`() {
        val stats = (0 until SKILL_BUTTONS).map { lampStat(it) }
        assertTrue(stats.all { it != null && it.startsWith("stat.") }, stats.toString())
        assertEquals(SKILL_BUTTONS, stats.toSet().size)
    }

    /**
     * xpreward_confirm resumes on universe child `key - 1`, while xpreward_update draws the
     * button for `key` at enum 421's component with enum 681's stat. Each subcomponent must
     * therefore resolve to the skill named by the button the player clicked.
     */
    @Test
    fun `every lamp skill button grants the skill it shows`() {
        val buttons = enum<Int, ComponentType>(XPREWARD_BUTTONS_ENUM).backing
        for (sub in 0 until SKILL_BUTTONS) {
            val component = checkNotNull(buttons[sub + 1]) { "No button for subcomponent $sub" }
            val name = RSCM.getReverseMapping(RSCMType.COMPONENT, component.packed)
            val skill = name.substringAfter("component.xpreward:")
            val expected = "stat." + if (skill == "runecraft") "runecrafting" else skill
            assertEquals(expected, lampStat(sub), "subcomponent $sub ($name)")
        }
    }

    private companion object {
        const val SKILL_BUTTONS = 24
        const val XPREWARD_BUTTONS_ENUM = 421

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
