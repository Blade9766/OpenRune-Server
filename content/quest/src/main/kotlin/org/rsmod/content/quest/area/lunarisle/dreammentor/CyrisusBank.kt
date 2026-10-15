package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cyrisus's bank account at the Lunar Isle bank (`interface.dream_armour`), which 'Birds-Eye' Jack
 * opens while the player carries Cyrisus's chest. Withdrawing a piece of armour packs it into the
 * chest and depositing it puts it back; nothing ever reaches the player's own inventory. The two
 * inventories on screen are drawn fresh from [CyrisusChest] each time.
 */
class CyrisusBank @Inject constructor(private val dreamMentor: DreamMentorQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton(BANK_LAYER) { if (it.op == IfButtonOp.Op1) withdraw(it.comsub) }
        onIfModalButton(CHEST_LAYER) { if (it.op == IfButtonOp.Op1) deposit(it.comsub) }
        onIfModalButton(CLOSE_BUTTON) { ifClose() }
        onIfClose(INTERFACE) { stopShowing(player) }
        onOpHeld1(CHEST) { lookInChest() }
    }

    fun ProtectedAccess.openBank() {
        ifOpenMainModal(INTERFACE)
        ifSetEvents(BANK_LAYER, 0 until CyrisusChest.BANK_SIZE, IfEvent.Op1)
        ifSetEvents(CHEST_LAYER, 0 until CyrisusChest.SLOTS, IfEvent.Op1)
        ifSetEvents(CLOSE_BUTTON, -1..-1, IfEvent.Op1)
        refresh()
    }

    private fun ProtectedAccess.withdraw(bankSlot: Int) {
        if (!canChoose()) {
            return
        }
        when (CyrisusChest.withdraw(player, bankSlot)) {
            CyrisusChest.Withdrawal.NotWearable -> mes("Cyrisus won't want to wear that!")
            is CyrisusChest.Withdrawal.Packed -> refresh()
            CyrisusChest.Withdrawal.Nothing -> Unit
        }
    }

    private fun ProtectedAccess.deposit(chestSlot: Int) {
        if (!canChoose()) {
            return
        }
        if (CyrisusChest.deposit(player, chestSlot) != CyrisusChest.EMPTY) {
            refresh()
        }
    }

    /** Re-checked on every click: the chest could have been dropped with the bank still open. */
    private fun ProtectedAccess.canChoose(): Boolean {
        if (dreamMentor.stage(player) != DreamMentorQuest.STAGE_GOT_CHEST || CHEST !in inv) {
            ifClose()
            return false
        }
        return true
    }

    private fun ProtectedAccess.refresh() {
        val bank = player.invMap.getOrPut(BANK_INV)
        for (slot in 0 until CyrisusChest.BANK_SIZE) {
            val obj = CyrisusChest.bankObj(slot)
            bank[slot] =
                if (obj == null || CyrisusChest.isPacked(player, slot)) {
                    null
                } else {
                    InvObj(obj.internalName, CyrisusChest.bankCount(slot))
                }
        }
        val chest = player.invMap.getOrPut(CHEST_INV)
        for ((slot, bankSlot) in CyrisusChest.contents(player).withIndex()) {
            chest[slot] = CyrisusChest.bankObj(bankSlot)?.takeIf { bankSlot != CyrisusChest.EMPTY }?.let { InvObj(it.internalName, 1) }
        }
        invTransmit(bank)
        invTransmit(chest)
        runClientScript(BANK_INIT.asRSCM(RSCMType.CLIENTSCRIPT))
        runClientScript(CHEST_INIT.asRSCM(RSCMType.CLIENTSCRIPT))
    }

    private fun stopShowing(player: Player) {
        for (name in listOf(BANK_INV, CHEST_INV)) {
            val inv: Inventory = player.invMap[name] ?: continue
            player.stopInvTransmit(inv)
            player.invMap.remove(name)
        }
    }

    private suspend fun ProtectedAccess.lookInChest() {
        if (CyrisusChest.isEmpty(player)) {
            mesbox(
                "The chest is completely empty. 'Birds-Eye' Jack at the Lunar Isle bank can let " +
                    "me fill it from Cyrisus's account.",
            )
            return
        }
        val names =
            CyrisusChest.contents(player)
                .filter { it != CyrisusChest.EMPTY }
                .mapNotNull { CyrisusChest.bankObj(it)?.name }
        mesbox("The chest holds: ${names.joinToString(", ")}.")
    }

    companion object {
        const val INTERFACE = "interface.dream_armour"
        const val BANK_LAYER = "component.dream_armour:bank_layer"
        const val CHEST_LAYER = "component.dream_armour:chest_layer"
        const val CLOSE_BUTTON = "component.dream_armour:close_button"
        const val BANK_INV = "inv.dream_bank_inventory"
        const val CHEST_INV = "inv.dream_crate_inventory"
        const val BANK_INIT = "clientscript.[clientscript,dream_bank_init]"
        const val CHEST_INIT = "clientscript.[clientscript,dream_chest_init]"
        const val CHEST = "obj.dream_chest"
    }
}
