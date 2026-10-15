package org.rsmod.content.quest.area.hemenster.fishingcontest

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.FORESTER
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.SPADE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.WORMS
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * McGrubor's Wood, north of Hemenster. The main gate is locked for good; the way in is the loose
 * railing on the north fence, which anyone can squeeze through. Guard dogs roam the wood (their
 * spawns and aggression are ordinary npc config), so getting in and out is about dodging them, not
 * killing them. The red vines in the north-west corner give a red vine worm per check to anyone
 * with a spade, as often as they like, so running out of bait never blocks the quest.
 */
class McGruborsWood @Inject constructor(private val fc: FishingContestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(LOOSE_RAILING) { squeeze() }
        onOpLoc1(GATE_LEFT) { lockedGate(it.loc) }
        onOpLoc1(GATE_RIGHT) { lockedGate(it.loc) }
        for (vine in VINES) {
            onOpLoc1(vine) { checkVine(it.loc) }
        }
        onOpNpc1(FORESTER) { startDialogue(it.npc) { forester() } }
    }

    private suspend fun ProtectedAccess.squeeze() {
        val leaving = isInside(coords)
        val start = if (leaving) INSIDE else OUTSIDE
        val end = if (leaving) OUTSIDE else INSIDE
        if (coords != start) {
            playerWalk(start)
            arriveDelay()
        }
        mes("You squeeze through the loose railing.")
        soundSynth(SQUEEZE_SOUND)
        anim(SQUEEZE_SEQ)
        exactMove(
            start = start,
            end = end,
            delay1 = 0,
            delay2 = SQUEEZE_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (leaving) constants.em_face_west else constants.em_face_east,
            teleportType = TeleportType.Exempt,
        )
        delay(SQUEEZE_TICKS)
    }

    private suspend fun ProtectedAccess.lockedGate(gate: BoundLocInfo) {
        arriveDelay()
        faceLoc(gate)
        mes("The gate is locked. McGrubor doesn't want visitors.")
    }

    private suspend fun ProtectedAccess.checkVine(vine: BoundLocInfo) {
        arriveDelay()
        faceLoc(vine)
        if (SPADE !in inv) {
            mes("Something is wriggling among the roots, but you'll need a spade to dig it out.")
            return
        }
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(2)
        if (invAdd(inv, WORMS, 1).failure) {
            mes("You don't have room to carry any worms.")
            return
        }
        mes("You dig among the roots of the vine and find a red vine worm.")
    }

    private suspend fun Dialogue.forester() {
        chatNpc(neutral, "Hello there. This is McGrubor's Wood, private property. Mind you keep out.")
        chatPlayer(quiz, "Why's it so private?")
        chatNpc(worried, "McGrubor likes his peace and quiet, and he keeps guard dogs to make sure of it. Big ones. They'll chase anyone they catch inside.")
        if (fc.isContestStage(player)) {
            chatPlayer(quiz, "What if somebody needed something from in there? Worms, say?")
            chatNpc(shifty, "Then somebody had better be quick on their feet, and keep some food handy. I'm not patching anyone up.")
        }
    }

    companion object {
        const val LOOSE_RAILING = "loc.mcgruborlooserailing"
        const val GATE_LEFT = "loc.mcgruborgatel"
        const val GATE_RIGHT = "loc.mcgruborgater"

        val VINES =
            listOf(
                "loc.red_worm_vine",
                "loc.red_worm_corner",
                "loc.red_worm_end",
                "loc.red_worm_junction",
                "loc.red_worm_diag1",
                "loc.red_worm_diag3",
                "loc.red_worm_diagfiller",
                "loc.red_worm_end_diag",
            )

        const val SQUEEZE_SEQ = "seq.railing_squeeze"
        const val SQUEEZE_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"

        /** The railing is a wall on the west edge of its tile; the wood lies on its east side. */
        val INSIDE = CoordGrid(2662, 3500, 0)
        val OUTSIDE = CoordGrid(2661, 3500, 0)

        fun isInside(coords: CoordGrid): Boolean = coords.x >= INSIDE.x
    }
}
