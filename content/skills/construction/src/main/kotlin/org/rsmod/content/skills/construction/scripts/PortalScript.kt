package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.construction.data.Portals
import org.rsmod.content.skills.construction.data.Portals.Destination
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Directing portal chamber portals, and travelling through them.
 *
 * The owner directs a portal space at the centrepiece's Direct-portal: pick the space, then a
 * destination from a paged list. It costs [Portals.RUNE_MULTIPLIER] times the spell's runes, which
 * have to be loose in the inventory - no staves or pouches, and no combination runes - and needs the
 * spell's Magic level (boosts count) and quest, though not its spellbook. Redirecting a space costs
 * the full price again. The house is put back together so the frame shows its new portal.
 *
 * Anyone can step through a portal; it leaves the house as the exit portal does. A two-way portal's
 * first op goes wherever its varbit puts first, its second op to the other place, and Toggle swaps
 * them.
 */
class PortalScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
    private val spells: MagicSpellRegistry,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private val spellOf: Map<Destination, MagicSpell> by lazy {
        Destination.entries
            .filter { it.spell != null }
            .mapNotNull { destination ->
                spells.allSpells()
                    .firstOrNull { it.name == destination.spell && destination.spellbook.matches(it) }
                    ?.let { destination to it }
            }
            .toMap()
    }

    override fun ScriptContext.startup() {
        for (focus in Portals.FOCI) {
            onOpLoc1(focus) { direct() }
        }
        onOpLoc2(SCRYING_POOL) { scry() }
        for (frame in Portals.FRAMES) {
            for (destination in Destination.entries) {
                val portal = destination.portal(frame)
                val alternate = destination.alternate
                if (alternate == null) {
                    onOpLoc1(portal) { enter(destination, alternate = false) }
                    continue
                }
                onOpLoc1(portal) { enter(destination, alternate = alternateFirst(alternate)) }
                onOpLoc2(portal) { enter(destination, alternate = !alternateFirst(alternate)) }
                onOpLoc3(portal) { toggle(alternate) }
            }
        }
    }

    private fun String?.matches(spell: MagicSpell): Boolean = this == null || spell.spellbook?.name == this

    // ------------------------------------------------------------------------ travelling

    private fun ProtectedAccess.alternateFirst(alternate: Portals.Alternate): Boolean =
        player.vars[alternate.varbit] == 1

    private fun ProtectedAccess.toggle(alternate: Portals.Alternate) {
        VarPlayerIntMapSetter.set(player, alternate.varbit, if (alternateFirst(alternate)) 0 else 1)
    }

    private fun ProtectedAccess.enter(destination: Destination, alternate: Boolean) {
        val coords = if (alternate) destination.alternate?.coords else coordsOf(destination)
        if (coords == null) {
            mes("This portal doesn't seem to lead anywhere.")
            return
        }
        houses.leave(this, to = coords)
    }

    private fun coordsOf(destination: Destination): CoordGrid? {
        destination.fixed?.let { return it }
        val coords = spellOf[destination]?.obj?.paramOrNull(params.spell_telecoord) ?: return null
        return destination.level?.let { coords.copy(level = it) } ?: coords
    }

    // ---------------------------------------------------------------------------- scrying

    /** The scrying pool's Scry: a look at where one of the chamber's portals leads, as it is set. */
    private suspend fun ProtectedAccess.scry() {
        val house = registry.houseAt(player.coords) ?: return
        val owner = house.owner
        val portals =
            Portals.VARBITS.mapIndexedNotNull { index, varbit ->
                Destination.of(owner.vars[varbit])?.let { "Portal ${index + 1}: ${it.label}" to it }
            }
        if (portals.isEmpty()) {
            mes("None of this house's portals has been directed anywhere.")
            return
        }
        val destination =
            when (portals.size) {
                1 -> portals.single().second
                2 -> choice2(portals[0].first, portals[0].second, portals[1].first, portals[1].second, title = "Scry which portal?")
                else ->
                    choice3(
                        portals[0].first,
                        portals[0].second,
                        portals[1].first,
                        portals[1].second,
                        portals[2].first,
                        portals[2].second,
                        title = "Scry which portal?",
                    )
            }
        val alternate = destination.alternate
        val coords =
            // Which place a two-way portal leads to first is each player's own choice, as stepping through it is.
            if (alternate != null && alternateFirst(alternate)) alternate.coords else coordsOf(destination)
        if (coords == null) {
            mes("The pool shows nothing.")
            return
        }
        houses.scry(this, coords)
    }

    // ------------------------------------------------------------------------- directing

    private suspend fun ProtectedAccess.direct() {
        val house = registry.houseAt(player.coords) ?: return
        if (house.owner !== player) {
            mes("Only the owner of this house can direct its portals.")
            return
        }
        val space =
            choice3(
                spaceLabel(1),
                1,
                spaceLabel(2),
                2,
                spaceLabel(3),
                3,
                title = "Direct which portal?",
            )
        if (!hasFrame(house, space)) {
            mes("There is no portal frame built in that space.")
            return
        }
        val destination = pickDestination() ?: return
        if (!hasFrame(house, space)) {
            return
        }
        val varbit = Portals.VARBITS[space - 1]
        if (player.vars[varbit] == destination.stored) {
            mes("That portal already leads to ${destination.label}.")
            return
        }
        val spell = spellOf[destination]
        if (spell == null) {
            if (!canDirectFixed(destination)) {
                return
            }
            for ((obj, count) in destination.cost) {
                takeWithNotes(obj, count)
            }
        } else {
            if (!canDirect(spell)) {
                return
            }
            for ((obj, count) in runeCost(spell)) {
                invDel(inv, obj, count)
            }
            statAdvance(MAGIC, spell.castXp * Portals.XP_MULTIPLIER * xpMods.get(player, MAGIC))
        }
        VarPlayerIntMapSetter.set(player, varbit, destination.stored)
        mes("You direct portal $space to ${destination.label}.")
        houses.rebuild(this)
    }

    private fun ProtectedAccess.hasFrame(house: ActiveHouse, space: Int): Boolean =
        registry.roomAt(house, player.coords)?.furniture?.containsKey("portal_$space") == true

    private fun ProtectedAccess.spaceLabel(space: Int): String {
        val current = Destination.of(player.vars[Portals.VARBITS[space - 1]])
        return "Portal $space: ${current?.label ?: "nowhere"}"
    }

    /** A paged menu of every destination, cheapest spell first; More... cycles through the pages. */
    private suspend fun ProtectedAccess.pickDestination(): Destination? {
        val options = (spellOf.keys + Destination.entries.filter { it.fixed != null }).sortedBy { levelOf(it) }
        val pages = options.chunked(PAGE_SIZE)
        var page = 0
        while (true) {
            val entries = pages[page].map { "${it.label} (${levelOf(it)})" to it.stored }
            val last = if (pages.size > 1) "More..." to MORE else "Cancel" to CANCEL
            val picked = choose(entries + last)
            when (picked) {
                MORE -> page = (page + 1) % pages.size
                CANCEL -> return null
                else -> return Destination.of(picked)
            }
        }
    }

    private suspend fun ProtectedAccess.choose(entries: List<Pair<String, Int>>): Int =
        when (entries.size) {
            2 -> choice2(entries[0].first, entries[0].second, entries[1].first, entries[1].second)
            3 ->
                choice3(
                    entries[0].first,
                    entries[0].second,
                    entries[1].first,
                    entries[1].second,
                    entries[2].first,
                    entries[2].second,
                )
            4 ->
                choice4(
                    entries[0].first,
                    entries[0].second,
                    entries[1].first,
                    entries[1].second,
                    entries[2].first,
                    entries[2].second,
                    entries[3].first,
                    entries[3].second,
                )
            else ->
                choice5(
                    entries[0].first,
                    entries[0].second,
                    entries[1].first,
                    entries[1].second,
                    entries[2].first,
                    entries[2].second,
                    entries[3].first,
                    entries[3].second,
                    entries[4].first,
                    entries[4].second,
                )
        }

    private fun ProtectedAccess.canDirect(spell: MagicSpell): Boolean {
        if (player.stat(MAGIC) < spell.levelReq) {
            mes("You need a Magic level of ${spell.levelReq} to direct a portal there.")
            return false
        }
        val quest = spell.questReq
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            mes("You need to complete ${QuestRow.getRow("dbrow.$quest").displayname} to direct a portal there.")
            return false
        }
        val cost = runeCost(spell)
        if (cost.any { (obj, count) -> inv.count(obj) < count }) {
            mes("You need ${cost.joinToString(", ") { (obj, count) -> "$count ${objName(obj)}" }} to direct a portal there.")
            return false
        }
        return true
    }

    /** The Magic level a destination's menu entry shows; the basalt ones need none. */
    private fun levelOf(destination: Destination): Int = spellOf[destination]?.levelReq ?: 1

    private fun ProtectedAccess.canDirectFixed(destination: Destination): Boolean {
        val quest = destination.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            mes("You need to complete ${QuestRow.getRow("dbrow.$quest").displayname} to direct a portal there.")
            return false
        }
        if (destination.cost.any { (obj, count) -> countWithNotes(obj) < count }) {
            mes("You need ${destination.cost.joinToString(", ") { (obj, count) -> "$count ${objName(obj)}" }} to direct a portal there.")
            return false
        }
        return true
    }

    private fun runeCost(spell: MagicSpell): List<Pair<String, Int>> =
        spell.objReqs
            .filter { it.wornSlot == null }
            .map { RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id) to it.count * Portals.RUNE_MULTIPLIER }

    private fun objName(obj: String): String =
        dev.openrune.ServerCacheManager.getItem(RSCM.getRSCM(obj))?.name?.lowercase() ?: obj

    private companion object {
        const val MAGIC = "stat.magic"
        const val SCRYING_POOL = "loc.poh_scrying_pool"
        const val PAGE_SIZE = 4
        const val MORE = -1
        const val CANCEL = -2
    }
}
