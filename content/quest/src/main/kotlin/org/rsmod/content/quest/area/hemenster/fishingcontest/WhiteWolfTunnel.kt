package org.rsmod.content.quest.area.hemenster.fishingcontest

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.AUSTRI
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.VESTRI
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The dwarven tunnel under White Wolf Mountain. Vestri keeps the western stairs by Catherby and
 * Austri the eastern ones towards Taverley; both turn the player away until Fishing Contest is
 * complete, and both let them down from then on. The stairs up are always open, so nobody is ever
 * shut inside.
 */
class WhiteWolfTunnel @Inject constructor(private val fc: FishingContestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(WEST_DOWN) { climbDown(it.loc, WEST_BOTTOM, VESTRI, "Vestri") }
        onOpLoc1(EAST_DOWN) { climbDown(it.loc, EAST_BOTTOM, AUSTRI, "Austri") }
        onOpLoc1(WEST_UP) { climbUp(WEST_TOP) }
        onOpLoc1(EAST_UP) { climbUp(EAST_TOP) }
    }

    private suspend fun ProtectedAccess.climbDown(stairs: BoundLocInfo, dest: CoordGrid, dwarf: String, name: String) {
        arriveDelay()
        if (!fc.isComplete(player)) {
            faceLoc(stairs)
            startDialogue {
                chatNpcSpecific(name, dwarf, angry, "Oi! Where do you think you're going? That tunnel is for friends of the dwarves only.")
            }
            return
        }
        telejump(dest)
        mes("You climb down into the dwarven tunnel under White Wolf Mountain.")
    }

    private suspend fun ProtectedAccess.climbUp(dest: CoordGrid) {
        arriveDelay()
        telejump(dest)
    }

    companion object {
        const val WEST_DOWN = "loc.tunnelstairstop"
        const val EAST_DOWN = "loc.tunnelstairstop2"
        const val WEST_UP = "loc.tunnelstairs"
        const val EAST_UP = "loc.tunnelstairs2"

        val WEST_TOP = CoordGrid(2820, 3486, 0)
        val EAST_TOP = CoordGrid(2876, 3482, 0)
        val WEST_BOTTOM = CoordGrid(2820, 9882, 0)
        val EAST_BOTTOM = CoordGrid(2876, 9879, 0)
    }
}
