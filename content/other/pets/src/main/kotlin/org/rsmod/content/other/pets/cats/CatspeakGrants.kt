package org.rsmod.content.other.pets.cats

import org.rsmod.game.entity.Player

/**
 * Sources of permanent catspeak other than the amulet. Quest content registers its own checks at
 * startup, so the pets module does not have to depend on the quest module to know about them.
 */
object CatspeakGrants {
    private val grants = mutableListOf<(Player) -> Boolean>()

    fun grant(check: (Player) -> Boolean) {
        grants += check
    }

    fun granted(player: Player): Boolean = grants.any { it(player) }
}
