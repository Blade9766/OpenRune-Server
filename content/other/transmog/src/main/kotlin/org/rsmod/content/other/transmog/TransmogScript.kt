package org.rsmod.content.other.transmog

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import java.text.NumberFormat
import java.util.WeakHashMap
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val INTERFACE = "interface.transmog"
private const val RESULT_INV = "inv.transmog_results"
private const val SLOT_INV = "inv.transmog_slots"

private const val MAX_RESULTS = 480
private const val COMPONENTS_PER_CELL = 2
private const val NO_SLOT = -1

private val SLOT_NAMES =
    mapOf(
        0 to "Head",
        1 to "Cape",
        2 to "Neck",
        3 to "Weapon",
        4 to "Body",
        5 to "Shield",
        7 to "Legs",
        9 to "Hands",
        10 to "Feet",
    )

private class TransmogState {
    var slot: Int = NO_SLOT
    var query: String = ""
}

class TransmogScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private val states = WeakHashMap<Player, TransmogState>()

    private fun state(player: Player): TransmogState = states.getOrPut(player) { TransmogState() }

    private val Player.transmogResults: Inventory
        get() = invMap.getOrPut(RESULT_INV)

    private val Player.transmogSlots: Inventory
        get() = invMap.getOrPut(SLOT_INV)

    internal data class SearchArgs(val comsub: Int, val op: Int, val text: String) : IfScriptArgs

    override fun ScriptContext.startup() {
        onGameStartup { TransmogIndex.build() }

        onPlayerLogin {
            val wardrobe = player.loadWardrobe()
            if (wardrobe.hasActive()) {
                player.applyWardrobe(wardrobe)
            }
        }

        onCommand("transmog") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open the transmog wardrobe to restyle worn equipment and save outfit presets"
            cheat { openMenu() }
        }

        onIfClose(INTERFACE) {
            states.remove(player)
            player.transmogResults.fillNulls()
            player.transmogSlots.fillNulls()
            player.stopInvTransmit(player.transmogResults)
            player.stopInvTransmit(player.transmogSlots)
            ClientScripts.chatDefaultRestoreInput(player)
        }

        onIfScriptTrigger<SearchArgs>("component.transmog:searchtext") {
            if (isAdmin()) search(it.text)
        }

        onIfModalButton("component.transmog:grid") {
            if (isAdmin()) wear(it.comsub)
        }

        for (slot in SLOT_NAMES.keys) {
            onIfModalButton("component.transmog:slot$slot") {
                if (isAdmin()) slotOp(slot, it.op)
            }
        }

        for (preset in 0 until PRESET_COUNT) {
            onIfModalButton("component.transmog:preset$preset") {
                if (isAdmin()) presetOp(preset, it.op)
            }
        }

        onIfModalButton("component.transmog:clear_all") { if (isAdmin()) clearAll() }

        onIfModalButton("component.transmog:all_slots") { if (isAdmin()) selectSlot(NO_SLOT) }
    }

    private fun ProtectedAccess.isAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        ifClose()
        return false
    }

    private fun Cheat.openMenu() {
        protectedAccess.launch(player) { open() }
    }

    private fun ProtectedAccess.open() {
        states[player] = TransmogState()
        ifOpenMainModal(INTERFACE)
        ifSetEvents("component.transmog:grid", 0 until MAX_RESULTS * COMPONENTS_PER_CELL, IfEvent.Op1)
        ifSetEvents(
            "component.transmog:searchtext",
            -1..-1,
            IfEvent.DeprecatedOp1,
            IfEvent.ScriptTrigger,
        )
        for (slot in SLOT_NAMES.keys) {
            ifSetEvents("component.transmog:slot$slot", -1..-1, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3)
        }
        for (preset in 0 until PRESET_COUNT) {
            ifSetEvents(
                "component.transmog:preset$preset",
                -1..-1,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
            )
        }
        ifSetEvents("component.transmog:clear_all", -1..-1, IfEvent.Op1)
        ifSetEvents("component.transmog:all_slots", -1..-1, IfEvent.Op1)
        invTransmit(player.transmogResults)
        invTransmit(player.transmogSlots)

        val wardrobe = player.loadWardrobe()
        renderSlots(wardrobe)
        renderPresets(wardrobe)
        renderResults()
    }

    private fun ProtectedAccess.search(query: String) {
        state(player).query = query.trim().lowercase()
        renderResults()
    }

    private fun ProtectedAccess.selectSlot(slot: Int) {
        val state = state(player)
        state.slot = if (state.slot == slot) NO_SLOT else slot
        renderSlots(player.loadWardrobe())
        renderResults()
    }

    private fun ProtectedAccess.wear(comsub: Int) {
        val obj = player.transmogResults[comsub / COMPONENTS_PER_CELL] ?: return
        val slot = TransmogIndex.slotOf(obj.id) ?: return
        updateActive { it.setActive(slot, obj.id) }
    }

    private fun ProtectedAccess.slotOp(slot: Int, op: IfButtonOp) {
        when (op) {
            IfButtonOp.Op1 -> selectSlot(slot)
            IfButtonOp.Op2 -> updateActive { it.setActive(slot, NO_OVERRIDE) }
            IfButtonOp.Op3 -> updateActive { it.setActive(slot, HIDDEN) }
            else -> Unit
        }
    }

    private fun ProtectedAccess.clearAll() {
        updateActive { it.clearActive() }
    }

    private fun ProtectedAccess.updateActive(change: (TransmogWardrobe) -> Unit) {
        val wardrobe = player.loadWardrobe()
        change(wardrobe)
        player.saveWardrobe(wardrobe)
        player.applyWardrobe(wardrobe)
        renderSlots(wardrobe)
    }

    private fun ProtectedAccess.presetOp(preset: Int, op: IfButtonOp) {
        val wardrobe = player.loadWardrobe()
        val saved = wardrobe.hasPreset(preset)
        when {
            op == IfButtonOp.Op1 && saved -> loadPreset(wardrobe, preset)
            op == IfButtonOp.Op1 || op == IfButtonOp.Op2 -> savePreset(wardrobe, preset)
            op == IfButtonOp.Op3 && saved -> deletePreset(wardrobe, preset)
        }
    }

    private fun ProtectedAccess.loadPreset(wardrobe: TransmogWardrobe, preset: Int) {
        wardrobe.loadPreset(preset)
        player.saveWardrobe(wardrobe)
        player.applyWardrobe(wardrobe)
        renderSlots(wardrobe)
        player.mes("Loaded outfit preset ${preset + 1}.")
    }

    private fun ProtectedAccess.savePreset(wardrobe: TransmogWardrobe, preset: Int) {
        if (!wardrobe.hasActive()) {
            player.mes("You have no transmog applied to save.")
            return
        }
        wardrobe.savePreset(preset)
        player.saveWardrobe(wardrobe)
        renderPresets(wardrobe)
        player.mes("Saved your current outfit to preset ${preset + 1}.")
    }

    private fun ProtectedAccess.deletePreset(wardrobe: TransmogWardrobe, preset: Int) {
        wardrobe.deletePreset(preset)
        player.saveWardrobe(wardrobe)
        renderPresets(wardrobe)
        player.mes("Deleted outfit preset ${preset + 1}.")
    }

    private fun ProtectedAccess.renderSlots(wardrobe: TransmogWardrobe) {
        val slots = player.transmogSlots
        for (slot in SLOT_NAMES.keys) {
            val value = wardrobe.active(slot)
            val obj = if (value > NO_OVERRIDE) ServerCacheManager.getItem(value) else null
            slots[slot] = obj?.let { InvObj(it, 1) }
        }
        player.runClientScript(
            "clientscript.transmog_slots".asRSCM(RSCMType.CLIENTSCRIPT),
            state(player).slot,
            wardrobe.hiddenMask(),
        )
    }

    private fun ProtectedAccess.renderPresets(wardrobe: TransmogWardrobe) {
        player.runClientScript(
            "clientscript.transmog_presets".asRSCM(RSCMType.CLIENTSCRIPT),
            wardrobe.savedPresetMask(),
        )
    }

    private fun ProtectedAccess.renderResults() {
        val state = state(player)
        val results = player.transmogResults
        results.fillNulls()

        val slot = state.slot.takeUnless { it == NO_SLOT }
        if (slot == null && state.query.isEmpty()) {
            ifSetText("component.transmog:status", "")
            ifSetText("component.transmog:message", "Select a slot or search for an item")
            return
        }

        val found = TransmogIndex.search(slot, state.query, MAX_RESULTS)
        for (index in found.items.indices) {
            results[index] = InvObj(found.items[index], 1)
        }

        val scope = slot?.let(SLOT_NAMES::get) ?: "All slots"
        val shown = NumberFormat.getIntegerInstance().format(found.items.size)
        val total = NumberFormat.getIntegerInstance().format(found.total)
        val status = if (found.total > found.items.size) "$shown of $total" else total
        ifSetText("component.transmog:status", "$scope: $status items")
        ifSetText("component.transmog:message", if (found.total == 0) "No items found" else "")
    }
}
