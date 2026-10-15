package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The single ladder that runs through all four floors of the Grand Tree. Besides the usual
 * Climb-up and Climb-down, the bottom and middle sections offer Top-Floor and the middle and top
 * sections Bottom-Floor, which jump straight to Charlie's cage and the glider, or the King.
 */
class GrandTreeLadders @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BOTTOM) { climbTo(1) }
        onOpLoc2(BOTTOM) { climbTo(TOP_FLOOR) }

        onOpLoc1(MIDDLE_BOTTOM) { climbEither() }
        onOpLoc2(MIDDLE_BOTTOM) { climbTo(2) }
        onOpLoc3(MIDDLE_BOTTOM) { climbTo(0) }
        onOpLoc4(MIDDLE_BOTTOM) { climbTo(TOP_FLOOR) }

        onOpLoc1(MIDDLE_TOP) { climbEither() }
        onOpLoc2(MIDDLE_TOP) { climbTo(TOP_FLOOR) }
        onOpLoc3(MIDDLE_TOP) { climbTo(1) }
        onOpLoc4(MIDDLE_TOP) { climbTo(0) }

        onOpLoc1(TOP) { climbTo(2) }
        onOpLoc2(TOP) { climbTo(0) }
    }

    private suspend fun ProtectedAccess.climbEither() {
        var translate = 0
        startDialogue { translate = choice2("Climb-up", 1, "Climb-down", -1, title = "Climb up or down the ladder?") }
        if (translate != 0) {
            climbTo(player.coords.level + translate)
        }
    }

    private suspend fun ProtectedAccess.climbTo(level: Int) {
        anim(GrandTree.LADDER_SEQ)
        delay(1)
        telejump(landNear(CoordGrid(GrandTree.LADDER_LANDING_X, GrandTree.LADDER_LANDING_Z, level)))
    }

    private companion object {
        const val BOTTOM = "loc.grandtree_ladderbottom"
        const val MIDDLE_BOTTOM = "loc.grandtree_laddermiddle_bottom"
        const val MIDDLE_TOP = "loc.grandtree_laddermiddle_top"
        const val TOP = "loc.grandtree_laddertop"
        const val TOP_FLOOR = 3
    }
}
