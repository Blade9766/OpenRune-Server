package org.rsmod.content.other.dizanasquiver

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.objExamine
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.DizanasQuiver
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dizana's quiver: the cape-slot quiver from the Fortis Colosseum that carries a second stack of
 * arrows or bolts.
 *
 * The stored ammunition lives in the two `dizanas_quiver_temp_ammo` varps (see [DizanasQuiver]),
 * which the client's equipment tab reads to draw the extra slot next to the ammo slot. While the
 * quiver is worn that slot offers `Fill` (move the ammo slot's arrows or bolts into the quiver),
 * `Quiver-Remove`, `Swap` (exchange the two stacks) and `Examine`. The held quiver has `Open`, which
 * shows the same stack in its own interface with a `Remove` option, and `Empty`.
 *
 * Combat picks the stored ammunition automatically whenever the ammo slot cannot feed the weapon
 * (see `RangedAmmunition.activeAmmo`). Ammo saving is not built in: using an Ava's device on the
 * quiver copies that device's effect onto it without consuming the device.
 */
class DizanasQuiverScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (obj in DizanasQuiver.objs) {
            onOpHeld3(obj) { open(it.type) }
            onOpHeldU(obj) { imbue(it.first, it.second) }
        }
        for (obj in EMPTY_IS_OP4) {
            onOpHeld4(obj) { empty() }
        }
        for (obj in EMPTY_IS_OP5) {
            onOpHeld5(obj) { empty() }
        }

        // The equipment tab is a side overlay; the bank, deposit box and equipment stats screens
        // draw the same slot inside a modal.
        onIfOverlayButton("component.wornitems:extra_quiver_ammo") { slotOp(it.op) }
        onIfModalButton("component.bankmain:extra_quiver_ammo") { slotOp(it.op) }
        onIfModalButton("component.bank_depositbox:extra_quiver_ammo") { slotOp(it.op) }
        onIfModalButton("component.equipment:extra_quiver_ammo") { slotOp(it.op) }
        onIfModalButton("component.dizanas_quiver:ammo_obj") { removeToInventory() }

        onPlayerLogin { DizanasQuiver.ensureInitialised(player) }
        onWearposChange {
            if (wearpos == Wearpos.Back) {
                returnAmmoOnUnequip()
            }
        }
    }

    private fun ProtectedAccess.slotOp(op: IfButtonOp) {
        when (op.slot) {
            QUIVER_REMOVE_OP -> removeToInventory()
            FILL_OR_SWAP_OP -> if (DizanasQuiver.storedAmmo(player) == null) fill() else swap()
            EXAMINE_OP -> examineStored()
            else -> Unit
        }
    }

    private fun ProtectedAccess.open(quiver: ItemServerType) {
        ifOpenMainModal(INTERFACE)
        val charges =
            when {
                quiver.isType(INFINITE) || quiver.isType(INFINITE_TROUVER) -> "Charges: Unlimited"
                quiver.isType(UNCHARGED) || quiver.isType(UNCHARGED_TROUVER) -> "Charges: 0"
                else -> null
            }
        if (charges != null) {
            ifSetText(CHARGES_TEXT, charges)
        }
    }

    private fun ProtectedAccess.empty() {
        if (DizanasQuiver.storedAmmo(player) == null) {
            mes("Your Dizana's quiver is already empty.")
            return
        }
        removeToInventory()
    }

    /** Moves the ammo slot's arrows or bolts into the (empty) quiver. */
    private fun ProtectedAccess.fill() {
        val wornAmmo = player.worn[Wearpos.Quiver.slot]
        if (wornAmmo == null) {
            mes("You have nothing in your worn quiver to fill your Dizana's Quiver with.")
            return
        }
        val type = getInvObj(wornAmmo)
        if (!DizanasQuiver.canStore(type)) {
            mes("You can only store arrows or bolts in your Dizana's quiver.")
            return
        }
        val removed = invDel(worn, objName(wornAmmo), wornAmmo.count, slot = Wearpos.Quiver.slot)
        if (!removed.success) {
            return
        }
        DizanasQuiver.setStoredAmmo(player, wornAmmo)
    }

    /** Exchanges the stored stack with whatever is in the ammo slot (possibly nothing). */
    private fun ProtectedAccess.swap() {
        val stored = DizanasQuiver.storedAmmo(player) ?: return fill()
        val wornAmmo = player.worn[Wearpos.Quiver.slot]
        if (wornAmmo != null && !DizanasQuiver.canStore(getInvObj(wornAmmo))) {
            mes("You can only store arrows or bolts in your Dizana's quiver.")
            return
        }
        if (wornAmmo != null) {
            val removed = invDel(worn, objName(wornAmmo), wornAmmo.count, slot = Wearpos.Quiver.slot)
            if (!removed.success) {
                return
            }
        }
        val added = invAdd(worn, objName(stored), stored.count, slot = Wearpos.Quiver.slot)
        if (!added.success) {
            if (wornAmmo != null) {
                invAdd(worn, objName(wornAmmo), wornAmmo.count, slot = Wearpos.Quiver.slot)
            }
            return
        }
        DizanasQuiver.setStoredAmmo(player, wornAmmo)
    }

    private fun ProtectedAccess.removeToInventory() {
        val stored = DizanasQuiver.storedAmmo(player)
        if (stored == null) {
            mes("Your Dizana's quiver is empty.")
            return
        }
        val added = invAdd(inv, objName(stored), stored.count)
        if (!added.success) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        DizanasQuiver.setStoredAmmo(player, null)
    }

    private fun ProtectedAccess.examineStored() {
        val stored = DizanasQuiver.storedAmmo(player) ?: return
        val type = getOrNull(stored) ?: return
        player.objExamine(type, stored.count, 0)
    }

    /**
     * Using an Ava's device on the quiver gives the quiver that device's ammo-saving chance. The
     * device is kept.
     */
    private fun ProtectedAccess.imbue(first: ItemServerType, second: ItemServerType) {
        val device = if (DizanasQuiver.isQuiver(first)) second else first
        val rate = device.paramOrNull<Int>(params.ammo_recovery_rate)
        val applied = rate?.let { r -> DizanasQuiver.AMMO_SAVE_RATES.lastOrNull { it <= r } }
        if (applied == null) {
            mes("Nothing interesting happens.")
            return
        }
        val current = DizanasQuiver.ammoSaveRate(player) ?: 0
        if (current >= applied) {
            mes("Your quiver already benefits from an effect at least as good as that.")
            return
        }
        DizanasQuiver.setAmmoSaveRate(player, applied)
        mes(
            "You apply the ammo-saving effect of your ${device.name.lowercase()} to your quiver. " +
                "The device is not used up."
        )
    }

    /**
     * Taking the quiver off hands its ammunition back when there is room for it; otherwise the
     * stack stays inside the quiver until it is worn or emptied again.
     */
    private fun HeldEquipEvents.WearposChange.returnAmmoOnUnequip() {
        if (!DizanasQuiver.isQuiver(objType) || DizanasQuiver.isWearing(player)) {
            return
        }
        val stored = DizanasQuiver.storedAmmo(player) ?: return
        val added = player.invAdd(player.inv, stored.id, stored.count)
        if (added.success) {
            DizanasQuiver.setStoredAmmo(player, null)
        } else {
            player.mes("Your inventory is too full to take the ammunition out of your quiver.")
        }
    }

    private fun objName(obj: InvObj): String = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)

    private companion object {
        private const val INTERFACE = "interface.dizanas_quiver"
        private const val CHARGES_TEXT = "component.dizanas_quiver:charges_text"

        private const val UNCHARGED = "obj.dizanas_quiver_uncharged"
        private const val UNCHARGED_TROUVER = "obj.dizanas_quiver_uncharged_trouver"
        private const val CHARGED = "obj.dizanas_quiver_charged"
        private const val CHARGED_TROUVER = "obj.dizanas_quiver_charged_trouver"
        private const val INFINITE = "obj.dizanas_quiver_infinite"
        private const val INFINITE_TROUVER = "obj.dizanas_quiver_infinite_trouver"

        /** Held ops are `Wear, Open, Empty, Destroy`. */
        private val EMPTY_IS_OP4 = listOf(UNCHARGED, UNCHARGED_TROUVER, INFINITE, INFINITE_TROUVER)

        /** Held ops are `Wear, Open, Uncharge, Empty`. */
        private val EMPTY_IS_OP5 = listOf(CHARGED, CHARGED_TROUVER)

        /* Ops the equipment-tab clientscript (5026) sets on the extra quiver slot. */
        private const val QUIVER_REMOVE_OP = 1
        private const val FILL_OR_SWAP_OP = 2
        private const val EXAMINE_OP = 10
    }
}
