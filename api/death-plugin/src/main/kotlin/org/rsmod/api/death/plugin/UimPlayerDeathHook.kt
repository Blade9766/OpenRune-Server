package org.rsmod.api.death.plugin

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling

/**
 * An Ultimate Ironman keeps nothing on an unsafe death, Protect Item included. Everything stays on
 * the floor for them alone for an hour; Wilderness PvP deaths fall through to the regular PvP rules.
 */
public class UimPlayerDeathHook @Inject constructor() : PlayerDeathHook {
    override val priority: Int
        get() = PlayerDeathHook.PRIORITY_GAMEMODE

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!context.usesUimRules) return null
        return PlayerDeathHandling(
            keepCount = 0,
            dropReceiver = context.player,
            dropDuration = UIM_DROP_DURATION,
            revealDelay = UIM_DROP_DURATION,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.DROP,
        )
    }

    private companion object {
        private const val UIM_DROP_DURATION = 6000
    }
}
