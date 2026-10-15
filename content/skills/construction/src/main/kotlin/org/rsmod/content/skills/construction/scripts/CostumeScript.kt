package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.skills.construction.data.Costumes
import org.rsmod.content.skills.construction.data.Costumes.costumeStorage
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Searching the costume room's stores.
 *
 * A closed store opens in place and Search on the open one shows the real `poh_costumes` interface
 * for that store's enum, beside `poh_costumes_side` holding the inventory; the cape rack is searched
 * as it stands. The interface reads the store with `invother_total`, so the owner's
 * [Costumes.INV] is sent as the mirrored copy of `inv.poh_costumes` every time it changes. Which
 * store, build option and page are on show is kept in the `poh_costume_view` varbits.
 *
 * Anyone can look; only the owner can Take an item out or Store one from their inventory, and a
 * store only accepts pieces of its own sets, up to its tier's set limit. A treasure chest's tier
 * buttons page between the clue tiers its build option opens (`varp.if2` tells the client how
 * many). The cape rack's limits on capes of accomplishment, ultimate ironman rules and the bank PIN
 * are not modelled.
 */
class CostumeScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val locRepo: LocRepository,
) : PluginScript() {
    private var Player.viewStore by intVarBit("varbit.poh_costume_store")
    private var Player.viewOption by intVarBit("varbit.poh_costume_option")
    private var Player.viewPage by intVarBit("varbit.poh_costume_page")

    override fun ScriptContext.startup() {
        for (store in Costumes.Store.entries) {
            for ((option, closed) in store.closed.withIndex()) {
                val open = store.opened.getOrNull(option)
                if (open == null) {
                    onOpLoc1(closed) { search(store, option) }
                    continue
                }
                onOpLoc1(closed) { swap(it.loc, open) }
                onOpLoc1(open) { search(store, option) }
                onOpLoc2(open) { swap(it.loc, closed) }
            }
        }
        onIfModalButton(ITEMS) { take(it) }
        onIfModalButton(SIDE_ITEMS) { store(it) }
        onIfModalButton(DEPOSIT_ALL) { storeAll() }
        onIfModalButton(TIER) { turnPage(it.op) }
        onIfClose(MAIN) {
            player.viewStore = 0
            ClientScripts.chatDefaultRestoreInput(player)
        }
    }

    private fun swap(loc: BoundLocInfo, into: String) {
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(loc.coords, into, Int.MAX_VALUE, loc.angle, loc.shape)
    }

    private fun ProtectedAccess.owner(): Player? = registry.houseAt(player.coords)?.owner

    // --------------------------------------------------------------------------- showing

    private fun ProtectedAccess.search(store: Costumes.Store, option: Int) {
        val owner = owner() ?: return
        player.viewStore = store.ordinal + 1
        player.viewOption = option
        player.viewPage = 0
        ifOpenMainSidePair(MAIN, SIDE)
        val isOwner = owner === player
        if (isOwner) {
            ClientScripts.interfaceInvInit(player, inv, SIDE_ITEMS, 4, 7, op1 = "Store")
            ifSetEvents(SIDE_ITEMS, 0 until inv.size, IfEvent.Op1, IfEvent.Op10)
        }
        show(store, option, page = 0, isOwner, owner)
    }

    private fun ProtectedAccess.show(store: Costumes.Store, option: Int, page: Int, isOwner: Boolean, owner: Player) {
        val pages = store.pages.getOrElse(option) { 1 }
        VarPlayerIntMapSetter.set(player, TIER_VARP, if (store == Costumes.Store.TREASURE_CHEST) pages - 1 else -1)
        UpdateInventory.updateInvFullMirror(player, owner.costumeStorage)
        runClientScript(INIT_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), store.enums[page], if (isOwner) 1 else 0, 1)
        ifSetEvents(ITEMS, 0..MAX_COMPONENTS, IfEvent.Op1, IfEvent.Op10)
        ifSetEvents(TIER, 0..0, *TIER_OPS)
        ifSetEvents(DEPOSIT_ALL, 0..0, IfEvent.Op1)
    }

    private fun ProtectedAccess.turnPage(op: IfButtonOp) {
        val store = viewing() ?: return
        val owner = owner() ?: return
        val page = TIER_OPS.indexOfFirst { it.name == op.name } - 1
        if (page !in 0 until store.pages.getOrElse(player.viewOption) { 1 }) {
            return
        }
        player.viewPage = page
        show(store, player.viewOption, page, owner === player, owner)
    }

    private fun ProtectedAccess.viewing(): Costumes.Store? = Costumes.Store.entries.getOrNull(player.viewStore - 1)

    // ------------------------------------------------------------------- taking and storing

    private fun ProtectedAccess.take(event: IfModalButton) {
        val owner = owner() ?: return
        val obj = event.obj ?: return
        if (event.op != IfButtonOp.Op1) {
            return
        }
        if (owner !== player) {
            mes("Only the owner of this house can take things out of it.")
            return
        }
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        val storage = player.costumeStorage
        if (invTotal(storage, name) <= 0) {
            return
        }
        val slot = storage.indexOfFirst { it?.id == obj.id }
        if (slot < 0 || invMoveFromSlot(storage, inv, slot, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        UpdateInventory.updateInvFullMirror(player, storage)
    }

    private fun ProtectedAccess.store(event: IfModalButton) {
        if (event.op != IfButtonOp.Op1) {
            return
        }
        val obj = inv[event.comsub] ?: return
        if (!deposit(obj.id)) {
            return
        }
        UpdateInventory.updateInvFullMirror(player, player.costumeStorage)
    }

    private fun ProtectedAccess.storeAll() {
        val ids = inv.objs.filterNotNull().map { it.id }.distinct()
        var stored = false
        for (id in ids) {
            stored = deposit(id, quiet = true) || stored
        }
        if (!stored) {
            mes("You have nothing that can be stored here.")
        }
        UpdateInventory.updateInvFullMirror(player, player.costumeStorage)
    }

    /** Stores every [obj] in the inventory in the open store. Returns whether any were stored. */
    private fun ProtectedAccess.deposit(obj: Int, quiet: Boolean = false): Boolean {
        val store = viewing() ?: return false
        if (owner() !== player) {
            return false
        }
        val storage = player.costumeStorage
        val set = Costumes.setFor(store, player.viewOption, obj)
        if (set == null) {
            if (!quiet) {
                mes("That can't be stored here.")
            }
            return false
        }
        val limit = store.limits.getOrNull(player.viewOption)
        val alreadyStarted = set.objs.any { invTotal(storage, nameOf(it)) > 0 }
        if (limit != null && !alreadyStarted && Costumes.setsStored(store, storage) >= limit) {
            if (!quiet) {
                mes("There's no room for another set in here.")
            }
            return false
        }
        val slots = inv.indices.filter { inv[it]?.id == obj }
        if (slots.isEmpty()) {
            return false
        }
        var stored = false
        for (slot in slots) {
            val held = inv[slot] ?: continue
            if (invMoveFromSlot(inv, storage, slot, held.count).failure) {
                if (!quiet) {
                    mes("There's no room for that in here.")
                }
                return stored
            }
            stored = true
        }
        return stored
    }

    private fun nameOf(obj: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, obj)

    private companion object {
        const val MAIN = "interface.poh_costumes"
        const val SIDE = "interface.poh_costumes_side"
        const val ITEMS = "component.poh_costumes:items"
        const val TIER = "component.poh_costumes:tier"
        const val DEPOSIT_ALL = "component.poh_costumes:depositall"
        const val SIDE_ITEMS = "component.poh_costumes_side:items"
        const val INIT_SCRIPT = "clientscript.[clientscript,poh_costumes_init]"
        const val TIER_VARP = "varp.if2"

        /** The interface builds a few dynamic components per set and per stored piece. */
        const val MAX_COMPONENTS = 4095

        /** Tier op 1 is "Tiers"; ops 2 to 7 are beginner to master. */
        val TIER_OPS =
            arrayOf(
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op5,
                IfEvent.Op6,
                IfEvent.Op7,
            )
    }
}
