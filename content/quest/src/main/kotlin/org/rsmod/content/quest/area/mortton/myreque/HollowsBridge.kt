package org.rsmod.content.quest.area.mortton.myreque

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.BRIDGE_SECTIONS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HAMMER
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS_PER_SECTION
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANK
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BRIDGE_REPAIRED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_REACHED_HOLLOWS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rope bridge across the Hollows. The two tree bases follow `route_bridgecomplete`: only
 * "Climb" while any of the three sections is broken, "Cross-bridge" too once all are mended.
 *
 * Climbing works on the sections from the tree top, so the player never stands over the gap:
 * each one takes a plank and [NAILS_PER_SECTION] nails and is saved the moment it is nailed
 * down, so running short, logging out or being knocked out by the guard loses nothing done.
 */
class HollowsBridge @Inject constructor(private val myq: InSearchOfTheMyrequeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(TREE_BROKEN) { climb() }
        onOpLoc2(TREE_MENDED) { lookFromTree() }
        onOpLoc1(TREE_MENDED) { cross() }
        onApLoc1(BRIDGE) { crossFromBridge(it.loc) }
    }

    private suspend fun ProtectedAccess.climb() {
        arriveDelay()
        if (myq.stage(player) < STAGE_STARTED) {
            climbUpAndDown()
            mes("Several sections of the rope bridge are missing. You have no reason to fix it.")
            return
        }
        if (HAMMER !in inv) {
            mes("The bridge is broken in places. You'll need a hammer, planks and steel nails to fix it.")
            return
        }
        anim(CLIMB_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        mes("You climb up the tree and look along the broken bridge.")
        for (section in 1..BRIDGE_SECTIONS) {
            if (myq.isSectionRepaired(player, section)) {
                continue
            }
            if (inv.count(PLANK) < 1 || inv.count(NAILS) < NAILS_PER_SECTION) {
                mes("You need a plank and $NAILS_PER_SECTION steel nails to repair the next section.")
                break
            }
            anim(HAMMER_SEQ)
            soundSynth(HAMMER_SOUND)
            delay(REPAIR_TICKS)
            if (inv.count(PLANK) < 1 || inv.count(NAILS) < NAILS_PER_SECTION || HAMMER !in inv) {
                break
            }
            invDel(inv, PLANK, 1)
            invDel(inv, NAILS, NAILS_PER_SECTION)
            myq.repairSection(player, section)
            mes("You nail a plank across section $section of the bridge.")
        }
        resetAnim()
        anim(CLIMB_DOWN_SEQ)
        delay(CLIMB_TICKS)
        if (myq.isBridgeRepaired(player)) {
            mes("The bridge is whole again. You can cross it from either tree.")
            if (myq.stage(player) >= STAGE_REACHED_HOLLOWS) {
                myq.advanceTo(this, STAGE_BRIDGE_REPAIRED)
            }
        } else {
            mes("You have repaired ${myq.sectionsRepaired(player)} of $BRIDGE_SECTIONS sections.")
        }
    }

    private suspend fun ProtectedAccess.lookFromTree() {
        arriveDelay()
        climbUpAndDown()
        mes("From the treetop you can see a guarded door in the earth to the north.")
    }

    private suspend fun ProtectedAccess.climbUpAndDown() {
        anim(CLIMB_SEQ)
        delay(CLIMB_TICKS)
        anim(CLIMB_DOWN_SEQ)
        delay(CLIMB_TICKS)
    }

    /** The planks hang over the gap, so the bridge is used from the bank rather than walked onto. */
    private suspend fun ProtectedAccess.crossFromBridge(bridge: BoundLocInfo) {
        if (!isWithinApRange(bridge, BRIDGE_AP_RANGE)) {
            return
        }
        if (!myq.isBridgeRepaired(player)) {
            mes("Several planks are missing. You'd have to climb the tree beside it to reach the gaps.")
            return
        }
        cross()
    }

    private suspend fun ProtectedAccess.cross() {
        arriveDelay()
        val northbound = coords.z < MyrequeCoords.BRIDGE_MID_Z
        anim(CLIMB_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        telejump(if (northbound) MyrequeCoords.BRIDGE_NORTH_END else MyrequeCoords.BRIDGE_SOUTH_END, TeleportType.Exempt)
        mes("You edge across the creaking rope bridge and climb down the far tree.")
    }

    private companion object {
        const val TREE_BROKEN = "loc.route_treebase_1op"
        const val TREE_MENDED = "loc.route_treebase_2ops"
        const val BRIDGE = "loc.swamp_bridge1"
        const val BRIDGE_AP_RANGE = 5

        const val CLIMB_SEQ = "seq.human_climbing"
        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val HAMMER_SEQ = "seq.human_hammer_hit"
        const val CLIMB_SOUND = "synth.climb_wall"
        const val HAMMER_SOUND = "synth.hammer_and_build"

        const val CLIMB_TICKS = 2
        const val REPAIR_TICKS = 3
    }
}
