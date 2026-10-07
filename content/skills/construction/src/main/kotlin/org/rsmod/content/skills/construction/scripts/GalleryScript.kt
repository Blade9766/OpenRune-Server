package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.construction.data.Gallery
import org.rsmod.content.skills.construction.data.Gallery.Cape
import org.rsmod.content.skills.construction.data.Gallery.Lair
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The achievement gallery's boss lair display, cape hanger, mounted displays, adventure log and
 * quest list.
 *
 * The owner puts a boss's jar in the lair display by using it on the display; Configure picks which
 * lair to show from the jars inside, and Jars hands a jar back, emptying the display if its lair was
 * on show. A cape is hung by using it on the empty hanger and taken back with Take; the house is put
 * back together after each change so the display shows it. The mounted capes' own perks - their
 * teleports, the max cape's perks and the like - are left to the capes themselves.
 *
 * Nothing on this server keeps an adventure log, so it reports the reader's levels and quest
 * points, and the quest list their quest points.
 */
class GalleryScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val lairs = listOf(Gallery.LAIR_BLANK) + Lair.entries.map { it.loc }
        for (loc in lairs) {
            onOpLocU(loc) { addJar(it.objType.id) }
            onOpLoc1(loc) { configure() }
            onOpLoc2(loc) { takeJar() }
        }
        onOpLocU(Gallery.CAPE_BLANK) { hang(it.objType.id) }
        for (cape in Cape.entries) {
            onOpLoc1(cape.loc) { mes("You admire the ${itemName(cape.obj)}.") }
            onOpLoc2(cape.loc) { mes("You'll need to wear the cape for that.") }
            onOpLoc4(cape.loc) { takeCape() }
        }
        onOpLoc1(Gallery.EMBLEM) { mes("You admire the emblem.") }
        onOpLoc1(Gallery.COINS) { mes("A hundred million coins, mounted for all to see.") }
        for (log in Gallery.ADVENTURE_LOGS) {
            onOpLoc1(log) { readLog() }
        }
        onOpLoc1(Gallery.QUEST_LIST) { mes("You have ${player.vars[QUEST_POINTS]} quest points.") }
    }

    private fun ProtectedAccess.isOwner(): Boolean {
        val house = registry.houseAt(player.coords) ?: return false
        if (house.owner !== player) {
            mes("Only the owner of this house can do that.")
            return false
        }
        return true
    }

    // ------------------------------------------------------------------------------ lairs

    private var ProtectedAccess.lairVarp: Int
        get() = player.vars[Gallery.LAIR_VARP]
        set(value) = VarPlayerIntMapSetter.set(player, Gallery.LAIR_VARP, value)

    private fun ProtectedAccess.addJar(obj: Int) {
        if (!isOwner()) {
            return
        }
        val lair = Lair.entries.firstOrNull { id(it.jar) == obj }
        if (lair == null) {
            mes("Only a boss's jar can go in the display.")
            return
        }
        if (Gallery.hasJar(lairVarp, lair)) {
            mes("The display already holds that jar.")
            return
        }
        if (invDel(inv, lair.jar, 1).failure) {
            return
        }
        var varp = Gallery.withJar(lairVarp, lair, held = true)
        if (Gallery.shownLair(varp) == null) {
            varp = Gallery.withShown(varp, lair)
        }
        lairVarp = varp
        mes("You put the jar in the display.")
        houses.rebuild(this)
    }

    private suspend fun ProtectedAccess.configure() {
        if (!isOwner()) {
            return
        }
        val held = Lair.entries.filter { Gallery.hasJar(lairVarp, it) }
        if (held.isEmpty()) {
            mes("Use a boss's jar on the display to be able to show its lair.")
            return
        }
        val options = held.map { it.label to it } + ("Nothing" to null)
        val lair = pick(options, "Display which lair?") ?: return
        lairVarp = Gallery.withShown(lairVarp, lair.second)
        houses.rebuild(this)
    }

    private suspend fun ProtectedAccess.takeJar() {
        if (!isOwner()) {
            return
        }
        val held = Lair.entries.filter { Gallery.hasJar(lairVarp, it) }
        if (held.isEmpty()) {
            mes("The display holds no jars.")
            return
        }
        val lair = pick(held.map { it.label to it }, "Take which jar?")?.second ?: return
        if (!Gallery.hasJar(lairVarp, lair)) {
            return
        }
        if (invAdd(inv, lair.jar, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        var varp = Gallery.withJar(lairVarp, lair, held = false)
        if (Gallery.shownLair(varp) == lair) {
            varp = Gallery.withShown(varp, null)
        }
        lairVarp = varp
        houses.rebuild(this)
    }

    // ------------------------------------------------------------------------------- capes

    private fun ProtectedAccess.hang(obj: Int) {
        if (!isOwner()) {
            return
        }
        val cape = Cape.entries.firstOrNull { id(it.obj) == obj }
        if (cape == null) {
            mes("That can't be hung on the cape hanger.")
            return
        }
        if (player.vars[Gallery.CAPE_VARP] != 0) {
            mes("There is already a cape on the hanger.")
            return
        }
        if (invDel(inv, cape.obj, 1).failure) {
            return
        }
        VarPlayerIntMapSetter.set(player, Gallery.CAPE_VARP, cape.ordinal + 1)
        houses.rebuild(this)
    }

    private fun ProtectedAccess.takeCape() {
        if (!isOwner()) {
            return
        }
        val cape = Cape.entries.getOrNull(player.vars[Gallery.CAPE_VARP] - 1) ?: return
        if (invAdd(inv, cape.obj, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        VarPlayerIntMapSetter.set(player, Gallery.CAPE_VARP, 0)
        houses.rebuild(this)
    }

    // ---------------------------------------------------------------------------- reading

    private fun ProtectedAccess.readLog() {
        val total = ServerCacheManager.getStats().values.sumOf { statBase(RSCM.getReverseMapping(RSCMType.STAT, it.id)) }
        mes("Your adventure log reads: total level $total, ${player.vars[QUEST_POINTS]} quest points.")
    }

    // ------------------------------------------------------------------------------ shared

    /** A paged chat menu; each option carries its label so a null value can still be chosen. */
    private suspend fun <T> ProtectedAccess.pick(options: List<Pair<String, T>>, title: String): Pair<String, T>? {
        val pages = options.chunked(PAGE_SIZE)
        var page = 0
        while (true) {
            val shown = pages[page]
            val labels = shown.map { it.first } + if (pages.size > 1) MORE else CANCEL
            val index =
                when (labels.size) {
                    2 -> choice2(labels[0], 0, labels[1], 1, title = title)
                    3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
                    4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
                    else -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4, title = title)
                }
            if (index < shown.size) {
                return shown[index]
            }
            if (pages.size == 1) {
                return null
            }
            page = (page + 1) % pages.size
        }
    }

    private fun id(obj: String): Int = obj.asRSCM(RSCMType.OBJ)

    private fun itemName(obj: String): String = ServerCacheManager.getItem(id(obj))?.name?.lowercase() ?: obj

    private companion object {
        const val QUEST_POINTS = "varp.qp"
        const val PAGE_SIZE = 4
        const val MORE = "More..."
        const val CANCEL = "Cancel"
    }
}
