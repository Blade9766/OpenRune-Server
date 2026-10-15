package org.rsmod.content.skills.construction.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoubleDoors
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A house's double doors. They swing like any others, but stay where they are left. Both sides of a
 * doorway carry a door hotspot in the template, and a door swinging off either one, open or shut,
 * would uncover it - a solid wall in a finished house - so that is taken away again. Anywhere else
 * they are ordinary doors.
 */
class HouseDoorScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val doors: DoubleDoors,
    private val locRepo: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (pair in HouseStyle.entries.map { it.doors }.distinctBy { it.left }) {
            with(doors) {
                onOpLoc1(pair.left) {
                    if (inHouse()) {
                        clearHotspots(openLeftDoor(it.loc, it.type, PERMANENT), it.loc.shape)
                    } else {
                        openLeftDoor(it.loc, it.type)
                    }
                }
                onOpLoc1(pair.right) {
                    if (inHouse()) {
                        clearHotspots(openRightDoor(it.loc, it.type, PERMANENT), it.loc.shape)
                    } else {
                        openRightDoor(it.loc, it.type)
                    }
                }
                onOpLoc1(pair.leftOpen) {
                    if (inHouse()) {
                        clearHotspots(closeLeftDoor(it.loc, it.type, PERMANENT), it.loc.shape)
                    } else {
                        closeLeftDoor(it.loc, it.type)
                    }
                }
                onOpLoc1(pair.rightOpen) {
                    if (inHouse()) {
                        clearHotspots(closeRightDoor(it.loc, it.type, PERMANENT), it.loc.shape)
                    } else {
                        closeRightDoor(it.loc, it.type)
                    }
                }
            }
        }
    }

    private fun ProtectedAccess.inHouse(): Boolean = registry.houseAt(player.coords) != null

    private fun clearHotspots(doorways: List<CoordGrid>, shape: LocShape) {
        for (coords in doorways) {
            val uncovered = locRepo.findExact(coords, shape) ?: continue
            if (uncovered.id in hotspots) {
                locRepo.del(uncovered, PERMANENT)
            }
        }
    }

    private val hotspots: Set<Int> by lazy {
        HouseStyle.entries
            .flatMap { listOf(it.doorLeft, it.doorRight) }
            .mapTo(HashSet()) { it.asRSCM(RSCMType.LOC) }
    }

    private companion object {
        const val PERMANENT = Int.MAX_VALUE
    }
}
