package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.ObjStackability
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.skills.farming.data.ToolSlot
import org.rsmod.content.skills.farming.state.FarmingToolStore
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tool leprechaun's store.
 *
 * The client owns the whole display: opening `farming_tools` runs its own onload, and every slot
 * redraws itself from the `farming_tools_*` varbits, so this script only moves items and writes
 * counters. The one thing it has to mirror is the client's option ordering - the four
 * "Store-1/5/X/All" labels are shuffled around depending on the quantity button the player picked,
 * so [amountForOp] has to shuffle the same way or an option will do the wrong thing.
 */
class FarmingToolStoreScript
@Inject
constructor(private val store: FarmingToolStore) : PluginScript() {
    override fun ScriptContext.startup() {
        for (leprechaun in LEPRECHAUNS) {
            onOpNpc3(leprechaun) { openStore() }
            onOpNpc4(leprechaun) { depositInventory() }
        }

        for (slot in ToolSlot.entries) {
            onIfModalButton(slot.sideComponent) { storeOp(slot, it.op.slot) }
            onIfModalButton(slot.mainComponent) { removeOp(slot, it.op.slot) }
        }

        onIfModalButton("component.farming_tools:deposit_all") { depositInventory() }

        for ((component, quantity) in QUANTITY_BUTTONS) {
            onIfModalButton(component) { selectedQuantity = quantity }
        }
    }

    private fun ProtectedAccess.openStore() {
        ifOpenMainSidePair(
            main = "interface.farming_tools",
            side = "interface.farming_tools_side",
            transparency = -2,
        )
    }

    private var ProtectedAccess.selectedQuantity: Int
        get() = player.vars[SELECTED_QUANTITY]
        set(value) = VarPlayerIntMapSetter.set(player, SELECTED_QUANTITY, value)

    // ------------------------------------------------------------------ storing

    private suspend fun ProtectedAccess.storeOp(slot: ToolSlot, op: Int) {
        if (op == EXAMINE_OP) {
            examine(slot)
            return
        }
        val carried = carriedFor(slot)
        if (carried.isEmpty()) {
            mes("You aren't carrying any ${slotName(slot)}.")
            return
        }
        val available = carried.sumOf { invTotal(inv, it.obj) }
        val amount = if (slot.singleItem) 1 else amountForOp(op, available) ?: return
        storeItems(slot, carried, amount)
    }

    /**
     * Takes up to [amount] from the player, draining each form the slot accepts in turn. A note and
     * a loose item are the same thing to the leprechaun, so a player carrying both hands over
     * whichever comes first and then the rest.
     */
    private suspend fun ProtectedAccess.storeItems(
        slot: ToolSlot,
        carried: List<Carried>,
        amount: Int,
    ) {
        var remaining = minOf(amount, store.freeSpace(player, slot))
        if (remaining <= 0) {
            mes("The leprechaun can't hold any more ${slotName(slot)}.")
            return
        }
        var moved = 0
        var movedObj = carried.first().stored
        for (entry in carried) {
            if (remaining <= 0) {
                break
            }
            if (slot == ToolSlot.SECATEURS && !secateursMatch(entry.stored)) {
                mes("The leprechaun is already holding a different pair of secateurs for you.")
                return
            }
            val take = minOf(remaining, invTotal(inv, entry.obj))
            if (take <= 0 || invDel(inv, entry.obj, take).failure) {
                continue
            }
            if (slot.singleItem) {
                store.setRawValue(player, slot, store.singleItemValue(slot, entry.stored))
            } else {
                store.setRawValue(player, slot, store.rawValue(player, slot) + take)
                if (slot == ToolSlot.SECATEURS) {
                    store.setMagicSecateurs(player, entry.stored == MAGIC_SECATEURS)
                }
            }
            if (moved == 0) {
                movedObj = entry.stored
            }
            moved += take
            remaining -= take
        }
        if (moved > 0) {
            mes("You store $moved x ${objName(movedObj)} with the leprechaun.")
        }
    }

    // --------------------------------------------------------------- withdrawing

    private suspend fun ProtectedAccess.removeOp(slot: ToolSlot, op: Int) {
        if (op == EXAMINE_OP) {
            examine(slot)
            return
        }
        val held = store.storedObj(player, slot)
        if (held == null) {
            mes("The leprechaun isn't holding any ${slotName(slot)} for you.")
            return
        }
        if (op == BANKNOTE_OP) {
            remove(slot, held, store.count(player, slot), noted = true)
            return
        }
        val amount =
            if (slot.singleItem) 1 else amountForOp(op, store.count(player, slot)) ?: return
        remove(slot, held, amount, noted = false)
    }

    private suspend fun ProtectedAccess.remove(
        slot: ToolSlot,
        obj: String,
        amount: Int,
        noted: Boolean,
    ) {
        val stored = store.count(player, slot)
        val given =
            if (noted) {
                runCatching { ocCert(obj).internalName }.getOrNull()
            } else {
                obj
            }
        if (given == null) {
            mes("The leprechaun can't hand that back as a note.")
            return
        }
        val stacks = noted || ServerCacheManager.getItem(given.asRSCM(RSCMType.OBJ))?.stacks == ObjStackability.Always
        val space = if (stacks) stored else inv.freeSpace()
        val moved = minOf(amount, stored, space)
        if (moved <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        if (invAdd(inv, given, moved).failure) {
            return
        }
        if (slot.singleItem) {
            store.setRawValue(player, slot, 0)
        } else {
            store.setRawValue(player, slot, stored - moved)
        }
        mes("You take $moved x ${objName(obj)} back from the leprechaun.")
    }

    // ------------------------------------------------------------------ deposit

    private suspend fun ProtectedAccess.depositInventory() {
        var moved = 0
        for (slot in ToolSlot.entries) {
            val carried = carriedFor(slot)
            if (carried.isEmpty()) {
                continue
            }
            val before = store.count(player, slot)
            storeItems(slot, carried, carried.sumOf { invTotal(inv, it.obj) })
            moved += store.count(player, slot) - before
        }
        if (moved == 0) {
            mes("You aren't carrying anything the leprechaun will look after.")
        }
    }

    // ------------------------------------------------------------------- shared

    /**
     * Every distinct thing in the player's inventory that belongs in [slot]. A leprechaun takes
     * notes as readily as the item itself, so a noted stack is listed under the obj it unnotes to.
     */
    private fun ProtectedAccess.carriedFor(slot: ToolSlot): List<Carried> {
        val found = LinkedHashMap<String, Carried>()
        for (index in inv.indices) {
            val held = inv[index] ?: continue
            val type = ServerCacheManager.getItem(held.id) ?: continue
            val unnoted =
                if (type.certtemplate > 0) {
                    ServerCacheManager.getItem(type.certlink)?.internalName ?: continue
                } else {
                    type.internalName
                }
            if (store.slotFor(unnoted) != slot) {
                continue
            }
            found.getOrPut(type.internalName) { Carried(type.internalName, unnoted) }
        }
        return found.values.toList()
    }

    /** [obj] as the player is carrying it, alongside the unnoted obj the store records. */
    private class Carried(val obj: String, val stored: String)

    private fun ProtectedAccess.secateursMatch(obj: String): Boolean {
        if (store.count(player, ToolSlot.SECATEURS) == 0) {
            return true
        }
        return store.storedObj(player, ToolSlot.SECATEURS) == obj
    }

    private fun ProtectedAccess.examine(slot: ToolSlot) {
        val obj = store.storedObj(player, slot) ?: slot.obj
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
        mes(type?.examine ?: "It's ${objName(obj)}.")
    }

    private fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

    private fun slotName(slot: ToolSlot): String = objName(slot.obj).lowercase()

    /**
     * How many the player meant by the option they clicked. The client reorders "-1", "-5", "-X"
     * and "-All" so the quantity button they picked sits on op one, so the same order has to be
     * reproduced here to read the click back.
     */
    private suspend fun ProtectedAccess.amountForOp(op: Int, available: Int): Int? {
        val order = OP_ORDER[selectedQuantity] ?: OP_ORDER.getValue(0)
        val amount = order.getOrNull(op - 1) ?: return null
        return when (amount) {
            Amount.One -> 1
            Amount.Five -> 5
            Amount.All -> available.coerceAtLeast(1)
            Amount.X -> countDialog("Enter amount:").coerceAtLeast(0)
        }
    }

    private enum class Amount {
        One,
        Five,
        X,
        All,
    }

    private companion object {
        const val SELECTED_QUANTITY = "varbit.farming_tools_selectedquantity"
        const val MAGIC_SECATEURS = "obj.fairy_enchanted_secateurs"
        const val BANKNOTE_OP = 9
        const val EXAMINE_OP = 10

        /** Every leprechaun that keeps a farmer's tools, including the quest and island ones. */
        val LEPRECHAUNS =
            listOf(
                "npc.farming_tools_leprechaun",
                "npc.farming_tools_leprechaun_draynor",
                "npc.farming_tools_leprechaun_varlamore",
                "npc.myarm_leprechaun",
                "npc.fossil_leprechaun_underwater",
            )

        /**
         * Quantity button to the value the client stores for it. The farming pack gives these four
         * components a click event so the press reaches the server at all; without it the client
         * would change its own copy of the var and the two ends would read the options differently.
         */
        val QUANTITY_BUTTONS =
            listOf(
                "component.farming_tools:quantity_1" to 0,
                "component.farming_tools:quantity_5" to 1,
                "component.farming_tools:quantity_all" to 2,
                "component.farming_tools:quantity_x" to 3,
            )

        /** Op one through four, per the quantity the player has selected. */
        val OP_ORDER =
            mapOf(
                0 to listOf(Amount.One, Amount.Five, Amount.X, Amount.All),
                1 to listOf(Amount.Five, Amount.One, Amount.X, Amount.All),
                2 to listOf(Amount.All, Amount.One, Amount.Five, Amount.X),
                3 to listOf(Amount.X, Amount.One, Amount.Five, Amount.All),
            )
    }
}
