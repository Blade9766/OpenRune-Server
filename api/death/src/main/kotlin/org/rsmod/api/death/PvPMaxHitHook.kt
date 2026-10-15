package org.rsmod.api.death

import org.rsmod.game.entity.Player

/** Lets an activity raise the max hit of [attacker] against [target] by a percentage. */
public fun interface PvPMaxHitHook {
    public fun maxHitBonusPercent(attacker: Player, target: Player): Int
}
