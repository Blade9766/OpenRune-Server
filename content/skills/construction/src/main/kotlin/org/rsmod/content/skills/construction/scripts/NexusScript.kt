package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfModalPauseButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.NexusConfig
import org.rsmod.content.skills.construction.data.Portals
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The portal nexus and the amulets mounted beside it.
 *
 * Teleport goes to the owner's left-click destination; anyone in the house may use it, and it leaves
 * the house as the exit portal does. Teleport Menu opens the real `telenexus_teleport`, which lists
 * the nexus's teleports from the viewer's `_temp` varbits - so the owner's are copied there first,
 * which is how a guest sees them - and answers a click or a number key with a pause button. In
 * its Scry mode a row is scried rather than gone to, and the nexus icon scries the house's own
 * town portal (see [HouseAccess.scry]).
 *
 * The owner's Configuration opens the real `telenexus`, a drag-and-drop editor working on the
 * `_temp` varbits (see [NexusConfig]). Each drag arrives as a drag event: an available teleport onto
 * the slots adds it, a slot onto another swaps them, a slot back onto the list removes it, and a
 * slot onto the left-click box makes it the left-click, whose radio buttons pick a two-way
 * teleport's place. The server makes each change itself and sends the copy back. An added teleport
 * needs the spell's Magic level unboosted and its quest done, and its thousand casts of runes are
 * set aside in `inv.telenexus_cost` so the interface knows what is left. Save & Close asks to
 * confirm, and confirming takes the runes and saves the copy; removing one refunds nothing.
 * Destinations whose spell has no fixed landing spot are never offered.
 *
 * A mounted amulet teleports for free: its first op goes to its left-click place, Teleport menu
 * lists them all, and the owner's Configuration picks the left-click, which the amulet then shows.
 * The places' unlocks (Xeric's Honour, Fossil Island, Lithkren) are not checked.
 */
class NexusScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
    private val spells: MagicSpellRegistry,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in Nexus.Tier.entries) {
            onOpLoc1(tier.loc) { teleportLeftClick(tier) }
            onOpLoc2(tier.loc) { teleportMenu(tier) }
            onOpLoc3(tier.loc) { configure(tier) }
        }
        for (target in listOf(SCROLLING2, SLOTTED, LIST2)) {
            onIfModalDrag(AVAILABLE, target) { it.selectedSlot?.let { row -> addFromList(row) } }
        }
        for (target in listOf(SLOTTED, LIST2)) {
            onIfModalDrag(SLOTTED, target) { drag -> edit { swap(drag.selectedSlot ?: 0, drag.targetSlot ?: 0) } }
        }
        for (target in listOf(SCROLLING1, AVAILABLE)) {
            onIfModalDrag(SLOTTED, target) { drag -> edit { remove(drag.selectedSlot ?: 0) } }
        }
        onIfModalDrag(SLOTTED, CLICK_LAYER) { drag -> edit { leftClickSlot(drag.selectedSlot ?: 0) } }
        onIfModalButton(RADIO) { button -> pickPlace(second = button.comsub == SECOND_PLACE) }
        onIfModalButton(CLICK_TEXT) { edit { leftClick = 0 } }
        onIfModalButton(DONE) { if (working().sameAs(saved(player))) ifClose() }
        onIfModalButton(CONFIRM) { save() }
        onIfModalPauseButton(MENU_OPTIONS) { setScryMode(it.comsub == SCRY_MODE_ROW) }
        for (rows in listOf(MENU_ROWS, MENU_KEYS)) {
            onIfModalPauseButton(rows) { pickRow(it.comsub, secondPlace = false) }
        }
        for (rows in listOf(MENU_EXTRA_ROWS, MENU_EXTRA_KEYS)) {
            onIfModalPauseButton(rows) { pickRow(it.comsub, secondPlace = true) }
        }
        onIfModalPauseButton(MENU_MODEL) { scryHousePortal() }
        onIfModalPauseButton(MENU_SCRY_TEXT) { scryHousePortal() }
        for (amulet in Nexus.Amulet.entries) {
            for ((index, place) in amulet.places.withIndex()) {
                onOpLoc1(place.loc) { houses.leave(this, to = amulet.places[index].coords) }
            }
            for (loc in amulet.locs) {
                onOpLoc2(loc) { amuletMenu(amulet) }
                onOpLoc3(loc) { configureAmulet(amulet) }
            }
        }
    }

    private fun ProtectedAccess.owner(): Player? = registry.houseAt(player.coords)?.owner

    // ----------------------------------------------------------------------------- travel

    private fun ProtectedAccess.teleportLeftClick(tier: Nexus.Tier) {
        val owner = owner() ?: return
        // A left-click set on a bigger nexus than the one standing now may point past its slots.
        val held = NexusConfig(tier.slots, saved(owner).values, 0).held
        val destination =
            Nexus.of(owner.vars[Nexus.LEFT_CLICK])?.takeIf { (it.primary ?: it.id) in held }
        if (destination == null) {
            mes("This portal nexus has no left-click teleport set.")
            return
        }
        travel(destination)
    }

    private fun ProtectedAccess.teleportMenu(tier: Nexus.Tier) {
        val owner = owner() ?: return
        val config = NexusConfig(tier.slots, saved(owner).values, owner.vars[Nexus.LEFT_CLICK])
        if (config.held.mapNotNull(Nexus::of).isEmpty()) {
            mes("This portal nexus has no teleports.")
            return
        }
        writeWorking(config, tier)
        ifOpenMainModal(MENU)
        val rows = 0 until Nexus.Tier.CRYSTALLINE.slots
        for (component in listOf(MENU_ROWS, MENU_KEYS, MENU_EXTRA_ROWS, MENU_EXTRA_KEYS)) {
            ifSetEvents(component, rows, IfEvent.PauseButton)
        }
        // None of the menu's parts carries an op name, so each is a pause button, as its rows are.
        ifSetEvents(MENU_OPTIONS, TELEPORT_MODE_ROW..SCRY_MODE_ROW, IfEvent.PauseButton)
        ifSetEvents(MENU_MODEL, -1..-1, IfEvent.PauseButton)
        ifSetEvents(MENU_SCRY_TEXT, -1..-1, IfEvent.PauseButton)
    }

    /**
     * A row of the open teleport menu, clicked or picked by its key: the [row]-th teleport the
     * nexus holds, or with [secondPlace] the [row]-th two-way teleport's second place. It is gone
     * to - or, in Scry mode, scried. Nothing waits on the menu, so its mode buttons stay clickable.
     */
    private suspend fun ProtectedAccess.pickRow(row: Int, secondPlace: Boolean) {
        val house = registry.houseAt(player.coords) ?: return
        val tier = tierOf(house) ?: return
        val held = NexusConfig(tier.slots, saved(house.owner).values, 0).held.mapNotNull(Nexus::of)
        val picked =
            if (secondPlace) {
                held.filter { it.alternate != null }.getOrNull(row)?.alternate?.let(Nexus::of)
            } else {
                held.getOrNull(row)
            } ?: return
        ifClose()
        if (player.vars[SCRY_MODE] == 1) {
            coordsOf(picked)?.let { houses.scry(this, it) } ?: mes("That teleport doesn't seem to lead anywhere.")
        } else {
            travel(picked)
        }
    }

    /**
     * The client takes no further pause button after one until an interface opens or closes, so
     * the menu is opened afresh to stay usable - its init redraws the mode from the varbit.
     */
    private fun ProtectedAccess.setScryMode(scry: Boolean) {
        VarPlayerIntMapSetter.set(player, SCRY_MODE, if (scry) 1 else 0)
        val tier = registry.houseAt(player.coords)?.let(::tierOf) ?: return
        teleportMenu(tier)
    }

    /** The nexus icon in Scry mode looks out at the house's own town portal. */
    private suspend fun ProtectedAccess.scryHousePortal() {
        val owner = owner() ?: return
        ifClose()
        houses.scry(this, houses.exitCoords(owner))
    }

    private fun ProtectedAccess.travel(destination: Nexus.Destination) {
        val coords = coordsOf(destination)
        if (coords == null) {
            mes("That teleport doesn't seem to lead anywhere.")
            return
        }
        houses.leave(this, to = coords)
    }

    private fun spellOf(destination: Nexus.Destination): MagicSpell? {
        val obj = destination.spellObj ?: return null
        val type = ServerCacheManager.getItem(obj) ?: return null
        return spells.getObjSpell(type)
    }

    /** Where a destination lands, with the portal chamber's corrections for spells that land wrong. */
    /** A destination that is a teleport item's rather than a spell's - the basalts - from the portal chamber's list. */
    private fun fixedOf(destination: Nexus.Destination): Portals.Destination? {
        val obj = (destination.primary?.let(Nexus::of) ?: destination).spellObj ?: return null
        val name = runCatching { RSCM.getReverseMapping(RSCMType.OBJ, obj) }.getOrNull() ?: return null
        return Portals.Destination.ofTeleportObj(name)
    }

    private fun coordsOf(destination: Nexus.Destination): CoordGrid? {
        fixedOf(destination)?.fixed?.let { return it }
        val primary = destination.primary?.let(Nexus::of)
        if (primary != null) {
            val name = spellOf(primary)?.name ?: return null
            return Portals.Destination.entries.firstOrNull { it.spell == name }?.alternate?.coords
        }
        val spell = spellOf(destination) ?: return null
        val coords = spell.obj.paramOrNull(params.spell_telecoord) ?: return null
        val fix = Portals.Destination.entries.firstOrNull { it.spell == spell.name && it.spellbook.matches(spell) }
        return fix?.level?.let { coords.copy(level = it) } ?: coords
    }

    private fun String?.matches(spell: MagicSpell): Boolean = this == null || spell.spellbook?.name == this

    private fun reachable(destination: Nexus.Destination): Boolean = coordsOf(destination) != null

    // ------------------------------------------------------------------------ configuring

    private fun saved(owner: Player): NexusConfig {
        val tier = registry.houseAt(owner.coords)?.let(::tierOf) ?: Nexus.Tier.CRYSTALLINE
        return NexusConfig(tier.slots, Nexus.SLOTS.map { owner.vars[it] }, owner.vars[Nexus.LEFT_CLICK])
    }

    private fun tierOf(house: ActiveHouse): Nexus.Tier? {
        val room = house.state.rooms.values.firstOrNull { it.type == RoomType.PORTAL_NEXUS } ?: return null
        val option = room.furniture[NEXUS_HOTSPOT] ?: return null
        return Nexus.Tier.entries.getOrNull(option)
    }

    private fun ProtectedAccess.working(): NexusConfig {
        val tier = Nexus.Tier.entries.getOrNull(player.vars[Nexus.TIER_VARBIT] - 1) ?: Nexus.Tier.MARBLE
        return NexusConfig(tier.slots, Nexus.TEMP_SLOTS.map { player.vars[it] }, player.vars[Nexus.LEFT_CLICK_TEMP])
    }

    /**
     * Sends [config] as the working copy. Every varbit is resent, changed or not, as the client
     * may already have made a different change to its own copy.
     */
    private fun ProtectedAccess.writeWorking(config: NexusConfig, tier: Nexus.Tier) {
        VarPlayerIntMapSetter.set(player, Nexus.TIER_VARBIT, tier.ordinal + 1)
        for ((slot, varbit) in Nexus.TEMP_SLOTS.withIndex()) {
            VarPlayerIntMapSetter.set(player, varbit, config.values[slot])
        }
        VarPlayerIntMapSetter.set(player, Nexus.LEFT_CLICK_TEMP, config.leftClick)
        (Nexus.TEMP_SLOTS + Nexus.LEFT_CLICK_TEMP + Nexus.TIER_VARBIT).forEach(player::resyncVar)
    }

    private fun ProtectedAccess.configure(tier: Nexus.Tier) {
        if (owner() !== player) {
            mes("Only the owner of this house can configure its portal nexus.")
            return
        }
        writeWorking(saved(player), tier)
        refreshCost(saved(player))
        ifOpenMainModal(CONFIG)
        val rows = 1..Nexus.ORDER.size
        ifSetEvents(AVAILABLE, rows, IfEvent.Depth1, IfEvent.DragTarget)
        ifSetEvents(SLOTTED, 1..tier.slots, IfEvent.Depth1, IfEvent.DragTarget)
        for (target in listOf(SCROLLING1, SCROLLING2, LIST2, CLICK_LAYER)) {
            ifSetEvents(target, -1..-1, IfEvent.DragTarget)
        }
        ifSetEvents(RADIO, FIRST_PLACE..SECOND_PLACE, IfEvent.Op1)
        for (button in listOf(CLICK_TEXT, DONE, CONFIRM, CANCEL_BUTTON)) {
            ifSetEvents(button, -1..-1, IfEvent.Op1)
        }
    }

    /** Applies [change] to the working copy and sends it back, cost and all. */
    private fun ProtectedAccess.edit(change: NexusConfig.() -> Unit) {
        if (owner() !== player) {
            return
        }
        val config = working()
        config.change()
        commit(config)
    }

    private fun ProtectedAccess.commit(config: NexusConfig) {
        val tier = Nexus.Tier.entries.first { it.slots == config.capacity }
        writeWorking(config, tier)
        refreshCost(config)
    }

    private fun ProtectedAccess.addFromList(row: Int) {
        val config = working()
        val destination = Nexus.ORDER.getOrNull(row - 1)?.let(Nexus::of)
        if (destination == null || owner() !== player || destination.id in config.held) {
            commit(config)
            return
        }
        val added = config.addedSince(saved(player)).mapNotNull(Nexus::of) + destination
        if (reachable(destination) && canAdd(destination, added) && !config.add(destination.id)) {
            mes("Your portal nexus can't hold any more teleports.")
        }
        commit(config)
    }

    private fun ProtectedAccess.pickPlace(second: Boolean) {
        edit {
            val primary = Nexus.of(leftClick)?.let { it.primary?.let(Nexus::of) ?: it }
            choosePlace(second, twoWay = primary?.alternate != null)
        }
    }

    /** Sets aside in `inv.telenexus_cost` the runes the teleports added so far will take. */
    private fun ProtectedAccess.refreshCost(config: NexusConfig) {
        val cost = player.invMap.getOrPut(COST_INV)
        invClear(cost)
        for (id in config.addedSince(saved(player))) {
            for ((rune, count) in Nexus.of(id)?.runes.orEmpty()) {
                invAdd(cost, objName(rune), count)
            }
        }
        UpdateInventory.updateInvFull(player, cost)
    }

    private fun ProtectedAccess.save() {
        if (owner() !== player) {
            return
        }
        val config = working()
        val added = config.addedSince(saved(player)).mapNotNull(Nexus::of)
        if (!canAfford(added)) {
            return
        }
        for (destination in added) {
            for ((rune, count) in destination.runes) {
                takeWithNotes(objName(rune), count)
            }
        }
        for ((slot, varbit) in Nexus.SLOTS.withIndex()) {
            VarPlayerIntMapSetter.set(player, varbit, config.values[slot])
        }
        VarPlayerIntMapSetter.set(player, Nexus.LEFT_CLICK, config.leftClick)
        invClear(player.invMap.getOrPut(COST_INV))
        ifClose()
        mes("You save your portal nexus's teleports.")
    }

    private fun ProtectedAccess.canAdd(destination: Nexus.Destination, added: List<Nexus.Destination>): Boolean {
        if (statBase(MAGIC) < destination.level) {
            mes("You need a Magic level of ${destination.level} to add that teleport.")
            return false
        }
        val quest = spellOf(destination)?.questReq ?: fixedOf(destination)?.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            mes("You need to complete ${QuestRow.getRow("dbrow.$quest").displayname} to add that teleport.")
            return false
        }
        return canAfford(added)
    }

    /** Whether the inventory holds the runes for every teleport in [added] together. */
    private fun ProtectedAccess.canAfford(added: List<Nexus.Destination>): Boolean {
        val needed = added.flatMap { it.runes }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val short = needed.filter { (rune, count) -> countWithNotes(objName(rune)) < count }
        if (short.isNotEmpty()) {
            val cost = needed.entries.joinToString(", ") { (rune, count) -> "$count ${itemName(rune)}" }
            mes("You need $cost for those teleports.")
            return false
        }
        return true
    }

    // ---------------------------------------------------------------------------- amulets

    private suspend fun ProtectedAccess.amuletMenu(amulet: Nexus.Amulet) {
        val place = pick(amulet.places.map { it.label to it }, "Teleport where?") ?: return
        houses.leave(this, to = place.coords)
    }

    private suspend fun ProtectedAccess.configureAmulet(amulet: Nexus.Amulet) {
        if (owner() !== player) {
            mes("Only the owner of this house can configure the ${amulet.label}.")
            return
        }
        val choices = amulet.places.mapIndexed { index, place -> place.label to index + 1 } + ("None" to 0)
        val leftClick = pick(choices, "Left-click teleport to?") ?: return
        VarPlayerIntMapSetter.set(player, amulet.varbit, leftClick)
        houses.rebuild(this)
    }

    // ----------------------------------------------------------------------------- menus

    /** A paged chat menu over [options]; More... cycles the pages and Cancel gives up. */
    private suspend fun <T> ProtectedAccess.pick(options: List<Pair<String, T>>, title: String): T? {
        if (options.isEmpty()) {
            mes("There's nothing to choose from.")
            return null
        }
        val pages = options.chunked(PAGE_SIZE)
        var page = 0
        while (true) {
            val shown = pages[page].map { it.first }
            val last = if (pages.size > 1) MORE_LABEL else CANCEL_LABEL
            val labels = shown + last
            val index =
                when (labels.size) {
                    2 -> choice2(labels[0], 0, labels[1], 1, title = title)
                    3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
                    4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
                    else -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4, title = title)
                }
            if (index < shown.size) {
                return pages[page][index].second
            }
            if (pages.size == 1) {
                return null
            }
            page = (page + 1) % pages.size
        }
    }

    private fun objName(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private fun itemName(id: Int): String = ServerCacheManager.getItem(id)?.name?.lowercase() ?: objName(id)

    private companion object {
        const val MAGIC = "stat.magic"
        const val PAGE_SIZE = 4
        const val MORE_LABEL = "More..."
        const val CANCEL_LABEL = "Cancel"

        const val NEXUS_HOTSPOT = "nexus"
        const val COST_INV = "inv.telenexus_cost"

        const val MENU = "interface.telenexus_teleport"
        const val MENU_ROWS = "component.telenexus_teleport:rows1"
        const val MENU_KEYS = "component.telenexus_teleport:key_listeners"
        const val MENU_EXTRA_ROWS = "component.telenexus_teleport:rows2"
        const val MENU_EXTRA_KEYS = "component.telenexus_teleport:extra_key_listeners"

        const val CONFIG = "interface.telenexus"
        const val AVAILABLE = "component.telenexus:non_slotted_list"
        const val SLOTTED = "component.telenexus:slotted_list"
        const val SCROLLING1 = "component.telenexus:scrolling1"
        const val SCROLLING2 = "component.telenexus:scrolling2"
        const val LIST2 = "component.telenexus:list2"
        const val CLICK_LAYER = "component.telenexus:click_layer"
        const val RADIO = "component.telenexus:radio_button_options"
        const val CLICK_TEXT = "component.telenexus:click_text"
        const val DONE = "component.telenexus:telenexus_donebutton"
        const val CONFIRM = "component.telenexus:telenexus_confirm"
        const val CANCEL_BUTTON = "component.telenexus:telenexus_cancel"

        /** The radio buttons' rows: the teleport's first place, then a two-way teleport's second. */
        const val FIRST_PLACE = 2
        const val SECOND_PLACE = 3

        const val MENU_OPTIONS = "component.telenexus_teleport:options_layer"
        const val MENU_MODEL = "component.telenexus_teleport:nexus_model"
        const val MENU_SCRY_TEXT = "component.telenexus_teleport:scry_portal"
        const val SCRY_MODE = "varbit.poh_nexus_tele_scry_mode"

        /** The menu's mode buttons, as `telenexus_options` numbers them. */
        const val TELEPORT_MODE_ROW = 2
        const val SCRY_MODE_ROW = 3
    }
}
