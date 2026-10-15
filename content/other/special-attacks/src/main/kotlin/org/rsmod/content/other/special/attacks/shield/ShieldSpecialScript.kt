package org.rsmod.content.other.special.attacks.shield

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.content.other.special.attacks.shield.ShieldSpecialAttacks.Companion.SPEC_SHIELDS
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Arms a shield special from the shield's own "Activate" option (worn op 2, the `wear_op1`
 * param; worn op 1 is `Remove`).
 *
 * Unlike a weapon special this never goes through the special attack orb, so the cooldown is
 * checked here and only spent once the blast actually fires - arming one and then walking away
 * costs nothing.
 */
class ShieldSpecialScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (shield in SPEC_SHIELDS) {
            onOpWorn2(shield) { armShieldSpecial() }
        }
    }

    private fun ProtectedAccess.armShieldSpecial() {
        val remaining = player.shieldSpecialReadyAt - mapClock
        if (remaining > 0) {
            mes("Your shield needs ${secondsText(remaining)} to recharge.")
            return
        }

        if (player.specialType == SpecialAttackType.Shield) {
            player.specialType = SpecialAttackType.None
            return
        }

        player.specialType = SpecialAttackType.Shield
        mes("Your shield is ready to unleash its power at your next target.")
    }

    private fun secondsText(ticks: Int): String {
        val seconds = (ticks * MILLIS_PER_TICK + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        return if (seconds == 1) "1 more second" else "$seconds more seconds"
    }

    private companion object {
        const val MILLIS_PER_TICK = 600
        const val MILLIS_PER_SECOND = 1000
    }
}

private val shieldSpecialCooldown = AttributeKey<Int>(resetOnDeath = false, temp = true)

internal var Player.shieldSpecialReadyAt: Int
    get() = attr[shieldSpecialCooldown] ?: 0
    set(value) {
        attr[shieldSpecialCooldown] = value
    }

internal var Player.specialType by enumVarp<SpecialAttackType>("varp.sa_attack")
