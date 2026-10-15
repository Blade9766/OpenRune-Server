package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.DACONIA_ROCK
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_SEARCHING_ROOTS
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The roots beneath the Grand Tree. Once the guards have cleared Glough's hoard the last Daconia
 * rock hides in one of the fifteen searchable roots, picked per player; the roots north of the
 * tunnel King open onto the Grand Tree mine for anyone who has finished the quest.
 */
class GrandTreeRoots
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ROOT) { search(it.loc) }
        onOpLoc1(ROOT_2) { search(it.loc) }
        onOpLoc1(ROOT_DOOR) { pushRoots(it.loc) }
    }

    private suspend fun ProtectedAccess.search(root: BoundLocInfo) {
        anim(GrandTree.SEARCH_SEQ)
        val hidingPlace = ROOTS.getOrNull(grandTree.rockRoot.get(player))
        val rockHere =
            grandTree.stage(player) == STAGE_SEARCHING_ROOTS &&
                !player.inv.contains(DACONIA_ROCK) &&
                root.coords == hidingPlace
        if (!rockHere) {
            mesbox("You search the root but don't find anything.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            mesbox("You find something in the root, but you have no room to carry it.")
            return
        }
        invAddOrDrop(objRepo, DACONIA_ROCK)
        objbox(DACONIA_ROCK, "You've found a Daconia rock!")
    }

    private suspend fun ProtectedAccess.pushRoots(roots: BoundLocInfo) {
        if (!grandTree.quest.isQuestCompleted(player)) {
            mesbox("The roots are far too thick to push through.")
            return
        }
        val fromSouth = player.coords.z < roots.coords.z
        val farZ = if (fromSouth) roots.coords.z + 1 else roots.coords.z - 1
        val nearX = player.coords.x.coerceIn(roots.coords.x, roots.coords.x + ROOT_DOOR_WIDTH - 1)
        val candidates =
            (0 until ROOT_DOOR_WIDTH)
                .map { CoordGrid(roots.coords.x + it, farZ, roots.coords.level) }
                .sortedBy { kotlin.math.abs(it.x - nearX) }
        anim(GrandTree.SEARCH_SEQ)
        mes("You push the roots apart and squeeze through.")
        delay(1)
        telejump(freeTileNear(candidates))
    }

    companion object {
        const val ROOT = "loc.largeroot_gnome"
        const val ROOT_2 = "loc.largeroot2_gnome"
        const val ROOT_DOOR = "loc.grandtree_rootdoor"
        const val ROOT_DOOR_WIDTH = 4

        /** Every searchable root in the tunnels, by its base tile. */
        val ROOTS =
            listOf(
                CoordGrid(2439, 9881, 0),
                CoordGrid(2444, 9893, 0),
                CoordGrid(2455, 9874, 0),
                CoordGrid(2457, 9881, 0),
                CoordGrid(2465, 9891, 0),
                CoordGrid(2467, 9872, 0),
                CoordGrid(2468, 9890, 0),
                CoordGrid(2481, 9904, 0),
                CoordGrid(2443, 9878, 0),
                CoordGrid(2452, 9893, 0),
                CoordGrid(2456, 9886, 0),
                CoordGrid(2467, 9896, 0),
                CoordGrid(2473, 9897, 0),
                CoordGrid(2485, 9885, 0),
                CoordGrid(2490, 9889, 0),
            )
    }
}
