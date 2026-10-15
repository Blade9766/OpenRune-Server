package org.rsmod.api.combat.manager

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/**
 * Verac's set effect, Defiler: a quarter of melee attacks with the full set hit regardless of
 * accuracy, and the resulting hit passes through the target's protection prayer. The roll is made
 * with the damage roll and remembered until the hit is queued.
 */
internal object VeracDefiler {
    private val PENDING = AttributeKey<Boolean>(temp = true)

    fun roll(player: Player, random: GameRandom): Boolean {
        val active =
            EquipmentChecks.isVeracSet(player.hat, player.torso, player.legs, player.righthand) &&
                random.randomBoolean(4)
        if (active) {
            player.attr[PENDING] = true
        } else {
            player.attr.remove(PENDING)
        }
        return active
    }

    fun consume(player: Player): Boolean {
        val pending = player.attr[PENDING] == true
        player.attr.remove(PENDING)
        return pending
    }
}
