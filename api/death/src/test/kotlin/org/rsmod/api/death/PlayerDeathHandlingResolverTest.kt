package org.rsmod.api.death

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.game.entity.Player

class PlayerDeathHandlingResolverTest {
    @Test
    fun `a safe activity beats the ultimate ironman rules`() {
        val safe = FixedHook(PlayerDeathHook.PRIORITY_SAFE_ACTIVITY, SAFE)
        val uim = FixedHook(PlayerDeathHook.PRIORITY_GAMEMODE, UIM)

        for (hooks in listOf(linkedSetOf(uim, safe), linkedSetOf(safe, uim))) {
            val handling = PlayerDeathHandlingResolver(hooks).resolve(uimContext())
            assertSame(SAFE, handling)
        }
    }

    @Test
    fun `the ultimate ironman rules beat default priority hooks`() {
        val default = FixedHook(PlayerDeathHook.PRIORITY_DEFAULT, SAFE)
        val uim = FixedHook(PlayerDeathHook.PRIORITY_GAMEMODE, UIM)

        val handling = PlayerDeathHandlingResolver(linkedSetOf(default, uim)).resolve(uimContext())

        assertSame(UIM, handling)
        assertEquals(0, default.calls)
    }

    private class FixedHook(override val priority: Int, private val handling: PlayerDeathHandling) :
        PlayerDeathHook {
        var calls = 0

        override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling {
            calls++
            return handling
        }
    }

    private companion object {
        val SAFE = handling(keepCount = Int.MAX_VALUE, untradeables = UntradeableHandling.KEEP)
        val UIM = handling(keepCount = 0, untradeables = UntradeableHandling.DROP)

        fun handling(keepCount: Int, untradeables: UntradeableHandling) =
            PlayerDeathHandling(
                keepCount = keepCount,
                dropReceiver = null,
                dropDuration = 0,
                revealDelay = 0,
                supplyPile = false,
                untradeableHandling = untradeables,
            )

        fun uimContext() =
            PlayerDeathPreviewContext.create(
                player = Player(),
                protectItem = false,
                skulled = false,
                playerKill = false,
                wildernessLevel = 0,
                inInstance = false,
                inRevenantCaves = false,
                gamemode = PlayerGamemode.ULTIMATE_IRONMAN,
            )
    }
}
