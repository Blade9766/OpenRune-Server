package org.rsmod.content.skills.construction.house

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.skills.construction.data.Trophies.Kind
import org.rsmod.game.entity.Player

private var Player.headTrophies by intVarBit("varbit.poh_trophies_heads")
private var Player.fishTrophies by intVarBit("varbit.poh_trophies_fish")
private var Player.headShown by intVarBit("varbit.poh_trophy_head_shown")
private var Player.fishShown by intVarBit("varbit.poh_trophy_fish_shown")

/** Every trophy of [kind] the player has mounted, one bit each. */
fun Player.mountedTrophies(kind: Kind): Int =
    when (kind) {
        Kind.HEAD -> headTrophies
        Kind.FISH -> fishTrophies
    }

fun Player.mountTrophy(kind: Kind, bit: Int) {
    when (kind) {
        Kind.HEAD -> headTrophies = headTrophies or (1 shl bit)
        Kind.FISH -> fishTrophies = fishTrophies or (1 shl bit)
    }
}

/** The trophy of [kind] the player's displays show, as a 1-based index; 0 for none. */
fun Player.shownTrophy(kind: Kind): Int =
    when (kind) {
        Kind.HEAD -> headShown
        Kind.FISH -> fishShown
    }

fun Player.showTrophy(kind: Kind, index: Int) {
    when (kind) {
        Kind.HEAD -> headShown = index
        Kind.FISH -> fishShown = index
    }
}
