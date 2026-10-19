package org.rsmod.api.stats.plugin.levelup

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.NpcDropReceiveHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathDropCoordsHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPCombatXpHook
import org.rsmod.api.death.PvPMaxHitHook
import org.rsmod.api.death.PvPPlayerHitHook
import org.rsmod.api.death.PvPSkullHook
import org.rsmod.api.death.PvPSpecialAttackHook
import org.rsmod.api.death.RangedAmmoSaveHook
import org.rsmod.api.death.plugin.PlayerDeathScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType

class LevelUpDeathTest {
    @Test
    fun GameTestState.`hit closes a level-up opened on its cycle and death starts next cycle`() =
        runInjectedGameTest(GameTestScope::class, DeathHooks(), *SCRIPTS) {
            player.setCurrentLevel("stat.hitpoints", HITPOINTS)
            player.statAdvance("stat.attack", XP_TO_LEVEL_2, rate = 1.0, globalRate = 1.0)
            player.queueHit(1, HitType.Typeless, HITPOINTS, NoopPlayerHitModifier)

            advance()
            check(player.hitpoints == 0)
            assertFalse(player.isDelayed)
            assertFalse(player.ui.containsModal(LEVELUP_DISPLAY))
            assertEquals(0, player.vars["varbit.busy"])

            advance()
            assertTrue(player.isDelayed)
        }

    @Test
    fun GameTestState.`level-up gained on the killing cycle does not hold off death`() =
        runInjectedGameTest(GameTestScope::class, DeathHooks(), *SCRIPTS) {
            player.setCurrentLevel("stat.hitpoints", HITPOINTS)
            player.queueHit(1, HitType.Typeless, HITPOINTS, NoopPlayerHitModifier)

            advance()
            check(player.hitpoints == 0)
            player.statAdvance("stat.attack", XP_TO_LEVEL_2, rate = 1.0, globalRate = 1.0)

            advance()
            assertTrue(player.isDelayed)
        }

    @Test
    fun GameTestState.`hit clears busy while interrupting an open level-up`() =
        runInjectedGameTest(GameTestScope::class, DeathHooks(), *SCRIPTS) {
            player.setCurrentLevel("stat.hitpoints", HITPOINTS)
            player.statAdvance("stat.attack", XP_TO_LEVEL_2, rate = 1.0, globalRate = 1.0)
            advance()
            assertTrue(player.ui.containsModal(LEVELUP_DISPLAY))
            assertEquals(1, player.vars["varbit.busy"])

            player.queueHit(1, HitType.Typeless, 1, NoopPlayerHitModifier)
            advance()
            assertFalse(player.ui.containsModal(LEVELUP_DISPLAY))
            assertEquals(0, player.vars["varbit.busy"])
        }

    private class DeathHooks : AbstractModule() {
        override fun configure() {
            Multibinder.newSetBinder(binder(), NpcDeathDropHook::class.java)
            Multibinder.newSetBinder(binder(), NpcDeathKillHook::class.java)
            Multibinder.newSetBinder(binder(), NpcDropReceiveHook::class.java)
            Multibinder.newSetBinder(binder(), PlayerDeathCleanupHook::class.java)
            Multibinder.newSetBinder(binder(), PlayerDeathDropCoordsHook::class.java)
            Multibinder.newSetBinder(binder(), PlayerDeathHook::class.java)
            Multibinder.newSetBinder(binder(), PlayerDeathItemHook::class.java)
            Multibinder.newSetBinder(binder(), PlayerRespawnHook::class.java)
            Multibinder.newSetBinder(binder(), PvPAttackValidateHook::class.java)
            Multibinder.newSetBinder(binder(), PvPSkullHook::class.java)
            Multibinder.newSetBinder(binder(), PvPPlayerHitHook::class.java)
            Multibinder.newSetBinder(binder(), PvPCombatXpHook::class.java)
            Multibinder.newSetBinder(binder(), PvPMaxHitHook::class.java)
            Multibinder.newSetBinder(binder(), PvPSpecialAttackHook::class.java)
            Multibinder.newSetBinder(binder(), RangedAmmoSaveHook::class.java)
        }
    }

    private companion object {
        const val HITPOINTS = 5
        const val XP_TO_LEVEL_2 = 83.0
        const val LEVELUP_DISPLAY = "interface.levelup_display"
        val SCRIPTS =
            arrayOf(PlayerHitScript::class, PlayerDeathScript::class, LevelUpScript::class)
    }
}
