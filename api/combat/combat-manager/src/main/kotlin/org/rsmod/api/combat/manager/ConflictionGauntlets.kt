package org.rsmod.api.combat.manager

import dev.openrune.util.Wearpos
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hands
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getInvObj

/**
 * Confliction gauntlets passive: after a missed magic attack, the next attack with the same spell
 * (or powered staff) against the same target rolls accuracy twice and lands if either roll does.
 * The passive stays armed through repeated misses and is disabled while wielding a two-handed
 * weapon.
 */
internal object ConflictionGauntlets {
    private data class Miss(val target: PathingEntity, val attack: Int)

    private val LAST_MISS = AttributeKey<Miss>(temp = true)

    inline fun roll(
        player: Player,
        target: PathingEntity,
        attack: Int,
        eligible: Boolean,
        roll: () -> Boolean,
    ): Boolean {
        if (!eligible || !isActive(player)) {
            return roll()
        }
        val doubled = isArmed(player, target, attack)
        val landed = roll() || (doubled && roll())
        record(player, target, attack, landed)
        return landed
    }

    fun isActive(player: Player): Boolean {
        if (!player.hands.isType("obj.confliction_gauntlets")) {
            return false
        }
        val weapon = player.righthand ?: return true
        val type = getInvObj(weapon)
        return type.wearpos2 != Wearpos.LeftHand.slot && type.wearpos3 != Wearpos.LeftHand.slot
    }

    fun isArmed(player: Player, target: PathingEntity, attack: Int): Boolean {
        val miss = player.attr[LAST_MISS] ?: return false
        return miss.target === target && miss.attack == attack
    }

    fun record(player: Player, target: PathingEntity, attack: Int, landed: Boolean) {
        if (landed) {
            player.attr.remove(LAST_MISS)
        } else {
            player.attr[LAST_MISS] = Miss(target, attack)
        }
    }
}
