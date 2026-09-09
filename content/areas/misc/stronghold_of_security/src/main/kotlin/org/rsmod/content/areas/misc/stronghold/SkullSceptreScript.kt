package org.rsmod.content.areas.misc.stronghold

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The skull sceptre: joining the four pieces dropped in the Stronghold, invoking it to teleport to
 * the entrance, divining its charges and topping them up with spare pieces or bone fragments.
 *
 * Charges are kept on the player (`varbit.sos_sceptre_charges`), as in OSRS where every unimbued
 * sceptre shares one pool and the next one is "magically back at full charge" once one crumbles.
 * An imbued sceptre (see [SolztunScript]) never crumbles. Piece values are the base OSRS ones
 * without the Varrock diary bonus.
 */
class SkullSceptreScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(RIGHT_SKULL, LEFT_SKULL) { join(RIGHT_SKULL, LEFT_SKULL, STRANGE_SKULL, "You join the two halves of the skull together.") }
        onOpHeldU(TOP_OF_SCEPTRE, BOTTOM_OF_SCEPTRE) { join(TOP_OF_SCEPTRE, BOTTOM_OF_SCEPTRE, RUNED_SCEPTRE, "You join the two halves of the sceptre together.") }
        onOpHeldU(STRANGE_SKULL, RUNED_SCEPTRE) { join(STRANGE_SKULL, RUNED_SCEPTRE, SKULL_SCEPTRE, "You place the skull on top of the sceptre. It hums with a strange power.") }

        for (sceptre in listOf(SKULL_SCEPTRE, IMBUED_SCEPTRE)) {
            onOpHeld3(sceptre) { invoke(inv, it.slot, sceptre) }
            onOpHeld4(sceptre) { divine() }
            onOpWorn2(sceptre) { invoke(worn, it.slot, sceptre) }
            onOpWorn3(sceptre) { divine() }
            for ((piece, value) in PIECE_CHARGES) {
                onOpHeldU(piece, sceptre) { recharge(piece, value) }
            }
            onOpHeldU(BONE_FRAGMENTS, sceptre) { rechargeWithFragments() }
        }
    }

    private fun ProtectedAccess.join(first: String, second: String, result: String, message: String) {
        if (invTotal(inv, first) == 0 || invTotal(inv, second) == 0) {
            return
        }
        invDel(inv, first, 1)
        invDel(inv, second, 1)
        invAdd(inv, result, 1)
        if (result == SKULL_SCEPTRE && charges() <= 0) {
            setCharges(MAX_CHARGES)
        }
        mes(message)
    }

    private suspend fun ProtectedAccess.invoke(inventory: Inventory, slot: Int, sceptre: String) {
        val charges = charges()
        if (charges <= 0) {
            mes("Your sceptre has no charges left. Use sceptre pieces or bone fragments on it to recharge it.")
            return
        }
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        telejump(Stronghold.SURFACE)
        anim(TELEPORT_END_ANIM)
        val left = charges - 1
        setCharges(left)
        if (left == 0 && sceptre == SKULL_SCEPTRE) {
            invDel(inventory, sceptre, 1, slot = slot)
            if (inventory === worn) {
                rebuildAppearance()
            }
            mes("Your skull sceptre crumbles to dust as its last charge is spent.")
            // The next sceptre a player assembles starts full again.
            setCharges(MAX_CHARGES)
        } else {
            mes("Your sceptre has ${left.chargeWord()} left.")
        }
    }

    private fun ProtectedAccess.divine() {
        mes("Your sceptre has ${charges().chargeWord()} left.")
    }

    private fun ProtectedAccess.recharge(piece: String, value: Int) {
        if (invTotal(inv, piece) == 0) {
            return
        }
        val current = charges()
        if (current >= MAX_CHARGES) {
            mes("Your sceptre is already fully charged.")
            return
        }
        invDel(inv, piece, 1)
        val total = current + value
        val kept = minOf(total, MAX_CHARGES)
        setCharges(kept)
        val surplus = total - kept
        if (surplus > 0) {
            invAddOrDrop(objRepo, BONE_FRAGMENTS, surplus)
            mes("You grind the piece into your sceptre. It now has ${kept.chargeWord()}; the leftover bone crumbles into fragments.")
        } else {
            mes("You grind the piece into your sceptre. It now has ${kept.chargeWord()}.")
        }
    }

    private fun ProtectedAccess.rechargeWithFragments() {
        val current = charges()
        if (current >= MAX_CHARGES) {
            mes("Your sceptre is already fully charged.")
            return
        }
        val fragments = invTotal(inv, BONE_FRAGMENTS)
        val used = minOf(fragments, MAX_CHARGES - current)
        if (used <= 0) {
            return
        }
        invDel(inv, BONE_FRAGMENTS, used)
        setCharges(current + used)
        mes("You feed $used bone fragment${if (used == 1) "" else "s"} into your sceptre. It now has ${(current + used).chargeWord()}.")
    }

    private fun ProtectedAccess.charges(): Int = vars[Stronghold.SCEPTRE_CHARGES]

    private fun ProtectedAccess.setCharges(value: Int) {
        vars[Stronghold.SCEPTRE_CHARGES] = value.coerceIn(0, MAX_CHARGES)
    }

    private fun Int.chargeWord(): String = if (this == 1) "1 charge" else "$this charges"

    companion object {
        const val RIGHT_SKULL = "obj.sos_half_skull1"
        const val LEFT_SKULL = "obj.sos_half_skull2"
        const val STRANGE_SKULL = "obj.sos_skull"
        const val TOP_OF_SCEPTRE = "obj.sos_half_sceptre1"
        const val BOTTOM_OF_SCEPTRE = "obj.sos_half_sceptre2"
        const val RUNED_SCEPTRE = "obj.sos_sceptre"
        const val SKULL_SCEPTRE = "obj.sos_skull_sceptre"
        const val IMBUED_SCEPTRE = "obj.sos_skull_sceptre_imbued"
        const val BONE_FRAGMENTS = "obj.sos_skull_sceptre_fragments"

        const val MAX_CHARGES = 10

        /** Charges a spare piece adds when used on a sceptre. */
        val PIECE_CHARGES: Map<String, Int> =
            mapOf(RIGHT_SKULL to 3, LEFT_SKULL to 5, TOP_OF_SCEPTRE to 3, BOTTOM_OF_SCEPTRE to 3)

        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
        private const val TELEPORT_DELAY = 3
    }
}
