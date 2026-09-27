package org.rsmod.api.death

import org.rsmod.game.entity.Player

/** Lets an activity withhold the combat experience [attacker] would earn by hitting [target]. */
public fun interface PvPCombatXpHook {
    public fun blocksCombatXp(attacker: Player, target: Player): Boolean
}
