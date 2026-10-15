package org.rsmod.api.death

import org.rsmod.game.entity.Player

/**
 * Lets an activity protect the ranged ammunition [player] fires. A positive percentage replaces the
 * worn Ava's device roll and ignores metal armour; `0` leaves the normal rules in place.
 */
public fun interface RangedAmmoSaveHook {
    public fun ammoSavePercent(player: Player): Int
}
