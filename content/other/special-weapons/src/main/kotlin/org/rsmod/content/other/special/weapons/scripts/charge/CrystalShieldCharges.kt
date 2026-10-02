package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The crystal shield's charges, held in the same crystal weapon charge varobj as the bow. A worn
 * shield loses one charge for every hit of at least 1 damage its wearer takes, and turns into
 * the inactive shield (with no bonuses) when the last charge goes. "Check" reports the charges
 * and "Revert" turns any form of the shield back into a crystal weapon seed, as the bow does.
 */
class CrystalShieldCharges @Inject constructor(private val charges: ObjChargeManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerHitEvents.Impact> { degrade(player, hit) }
        for (shield in CHECKABLE) {
            onOpHeld3(shield) { mes(message(charges.getCharges(it.inventory[it.slot], CRYSTAL_CHARGES))) }
            onOpWorn2(shield) { mes(message(charges.getCharges(player.lefthand, CRYSTAL_CHARGES))) }
        }
        for (shield in CHECKABLE + INACTIVE) {
            onOpHeld4(shield) { revert(it.slot, shield) }
        }
    }

    fun degrade(player: Player, hit: Hit) {
        if (hit.damage <= 0 || player.lefthand?.id != activeShield) {
            return
        }
        val result = charges.reduceWornCharges(player, Wearpos.LeftHand, CRYSTAL_CHARGES, 1)
        if (!result.isFailure() && result.fullyUncharged) {
            player.mes("Your crystal shield has run out of charges and become inactive.")
        }
    }

    private suspend fun ProtectedAccess.revert(slot: Int, shield: String) {
        val confirmed =
            choice2("Proceed.", true, "Cancel.", false, title = "Revert the shield into a crystal weapon seed? Any charges will be lost.")
        if (!confirmed || invDel(inv, shield, count = 1, slot = slot).failure) {
            return
        }
        invAdd(inv, CRYSTAL_WEAPON_SEED, count = 1, slot = slot)
        objbox(CRYSTAL_WEAPON_SEED, 400, "You revert the crystal shield into a crystal weapon seed.")
    }

    private fun message(remaining: Int): String = "Your crystal shield has ${remaining.formatAmount} charges remaining."

    private val activeShield by lazy { ACTIVE.asRSCM(RSCMType.OBJ) }

    private companion object {
        const val CRYSTAL_CHARGES = "varobj.crystal_weapon_charges"
        const val CRYSTAL_WEAPON_SEED = "obj.crystal_seed_old"
        const val ACTIVE = "obj.crystal_shield"
        const val INACTIVE = "obj.crystal_shield_inactive"
        val CHECKABLE = listOf(ACTIVE, "obj.crystal_shield_2500")
    }
}
