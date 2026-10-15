package org.rsmod.api.combat.commons.magic

import org.rsmod.game.entity.Player

/** Told about every spell whose requirements were met and consumed, after the runes are spent. */
public fun interface SpellCastHook {
    public fun onCast(player: Player, spell: MagicSpell)
}
