package org.rsmod.api.combat.commons.hook

import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.game.entity.Player

/**
 * Told whenever [player] picks [stance] on the combat tab, after it has been applied, with the
 * weapon they are holding. Lets a weapon react to its own styles, such as a style that opens an
 * interface instead of attacking.
 */
public fun interface CombatStanceSelectHook {
    public fun onSelect(player: Player, weapon: ItemServerType?, stance: CombatStance)
}
