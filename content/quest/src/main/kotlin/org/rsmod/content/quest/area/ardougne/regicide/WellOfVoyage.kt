package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way west once King Lathas's mages have repaired the Well of Voyage.
 *
 * After the Underground Pass, Iban's temple stands in ruins; its doors lead instead into the
 * restored temple, a separate copy of the room mapped [RestoredTemple.DX] tiles west and
 * [RestoredTemple.DZ] tiles north of the original, with the Well of Voyage where the Well of the
 * Damned was. The well drops into the last cavern of the pass on the Tirannwn side, whose cave
 * mouth opens onto Isafdar, and both lead back the way they came.
 */
class WellOfVoyage
@Inject
constructor(private val regicide: RegicideQuest, private val idris: IdrisScene) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TEMPLE_WELL) { climbDownToTirannwn() }
        onOpLoc1(TIRANNWN_WELL) { climbBackToTemple() }
        onOpLoc1(CAVE_EXIT) { leaveCave() }
        onOpLoc1(CAVE_ENTRANCE) { enterCave() }
    }

    private suspend fun ProtectedAccess.climbDownToTirannwn() {
        arriveDelay()
        if (!regicide.isStarted(player)) {
            mes("King Lathas's mages are still working on the well.")
            return
        }
        anim(CLIMB_SEQ)
        mes("You climb down into the well...")
        delay(2)
        telejump(TIRANNWN_WELL_LANDING, TeleportType.Exempt)
        player.downWell = 1
        mes("...and find yourself in a cavern beneath the western mountains.")
    }

    private suspend fun ProtectedAccess.climbBackToTemple() {
        arriveDelay()
        anim(CLIMB_SEQ)
        mes("You climb down into the well...")
        delay(2)
        telejump(RestoredTemple.WELL_LANDING, TeleportType.Exempt)
        mes("...and climb out into Iban's old temple.")
    }

    private suspend fun ProtectedAccess.leaveCave() {
        arriveDelay()
        telejump(ISAFDAR_ARRIVAL)
        idris.trigger(this)
    }

    private suspend fun ProtectedAccess.enterCave() {
        arriveDelay()
        telejump(TIRANNWN_CAVE_LANDING)
    }

    companion object {
        const val TEMPLE_WELL = "loc.regicide_voyage_temple_well1"
        const val TIRANNWN_WELL = "loc.regicide_voyage_temple_well2"
        const val CAVE_EXIT = "loc.regicide_voyage_temple_exit"
        const val CAVE_ENTRANCE = "loc.regicide_voyage_temple_entrance"
        const val CLIMB_SEQ = "seq.human_reachforladder"

        val TIRANNWN_WELL_TILE = CoordGrid(2341, 9622, 0)
        val TIRANNWN_WELL_LANDING = CoordGrid(2340, 9622, 0)
        val CAVE_EXIT_TILE = CoordGrid(2312, 9623, 0)
        val TIRANNWN_CAVE_LANDING = CoordGrid(2314, 9624, 0)
        val CAVE_ENTRANCE_TILE = CoordGrid(2313, 3215, 0)
        val ISAFDAR_ARRIVAL = CoordGrid(2311, 3216, 0)
    }
}

/** The restored copy of Iban's temple and how its doors map onto the ruined original. */
object RestoredTemple {
    const val DX = -128
    const val DZ = 64

    val WELL_TILE = CoordGrid(2008, 4711, 1)
    val WELL_LANDING = CoordGrid(2010, 4711, 1)

    private const val MIN_X = 1984
    private const val MAX_X = 2047

    fun contains(coords: CoordGrid): Boolean = coords.level == 1 && coords.x in MIN_X..MAX_X

    /** The tile just inside the restored temple for a player opening [door] from the ruins. */
    fun entryFor(door: BoundLocInfo, playerZ: Int): CoordGrid =
        CoordGrid(door.coords.x - 1 + DX, playerZ + DZ, door.coords.level)

    /** The tile just outside the ruined temple for a player leaving the restored one by [door]. */
    fun exitFor(door: BoundLocInfo, playerZ: Int): CoordGrid =
        CoordGrid(door.coords.x + 2 - DX, playerZ - DZ, door.coords.level)
}
