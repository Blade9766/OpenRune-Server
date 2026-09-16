package org.rsmod.content.quest.area.draynor.porcineofinterest

import jakarta.inject.Inject
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.ROPE
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SOURHOG_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_AMBUSHED
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_GOGGLES
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_IN_CAVE
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_SLAIN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The strange hole by the River Lum and the ways in and out of the cave under it: the player's own
 * rope, the climbing rope at the bottom, and the blockage across the corridor that separates the
 * entrance chamber from the sourhogs.
 *
 * `loc.porcine_hole` is a `varbit.porcine` multiloc that only offers "Climb-down" from
 * [STAGE_ROPE_TIED] onwards, so tying the rope is what opens the hole rather than any state of
 * our own.
 */
class StrangeHole @Inject constructor(private val porcine: PorcineOfInterestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(HOLE_NO_ROPE) { investigateHole() }
        onOpLocU(HOLE_NO_ROPE, ROPE) { tieRope() }
        onOpLoc1(HOLE_WITH_ROPE) { climbDown() }
        onOpLocU(HOLE_WITH_ROPE, ROPE) {
            mes("The hole already has a rope tied to it.")
        }
        onOpLoc1(EXIT_ROPE) { climbUp() }
        onOpLoc1(BLOCKAGE) { climbBlockage() }
        onOpLoc1(FALLEN_ROPE) {
            startDialogue {
                chatPlayer(
                    quiz,
                    "Huh... there's a rope already down here. I hope no one else has got to this " +
                        "monster first.",
                )
            }
        }
    }

    private suspend fun ProtectedAccess.investigateHole() {
        if (player.inv.contains(ROPE)) {
            tieRope()
            return
        }
        player.porcineNeedRope = true
        startDialogue {
            chatPlayer(
                quiz,
                "This looks like the place. It's a long way down, though... I'll need to tie " +
                    "something onto the edge.",
            )
        }
    }

    private suspend fun ProtectedAccess.tieRope() {
        arriveDelay()
        faceSquare(PorcineCoords.HOLE)
        anim(TIE_SEQ)
        soundSynth(TIE_SOUND)
        delay(TIE_TICKS)
        invDel(inv, ROPE)
        player.porcineNeedRope = false
        porcine.advanceTo(this, STAGE_ROPE_TIED)
        mesbox("You tie the rope tightly around a protruding rock.")
    }

    private suspend fun ProtectedAccess.climbDown() {
        val stage = porcine.stage(player)
        if (stage == STAGE_AMBUSHED) {
            startDialogue {
                chatPlayer(
                    worried,
                    "I think I should find out more about that monster from Spria in Draynor " +
                        "Village before heading back down there.",
                )
            }
            return
        }
        if (carries(SOURHOG_FOOT)) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "I don't need to go back down there right now. I should take the foot to " +
                        "Sarah and collect my reward.",
                )
            }
            return
        }
        if (stage >= STAGE_GOGGLES && !porcine.isComplete(player) && !wearsGoggles()) {
            startDialogue {
                chatPlayer(
                    worried,
                    "I don't want to go back down there without wearing the right equipment!",
                )
            }
            return
        }
        arriveDelay()
        faceSquare(PorcineCoords.HOLE)
        anim(CLIMB_DOWN_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        telejump(PorcineCoords.CAVE_ENTRANCE, TeleportType.Exempt)
        if (stage == STAGE_ROPE_TIED) {
            porcine.advanceTo(this, STAGE_IN_CAVE)
        }
    }

    private suspend fun ProtectedAccess.climbUp() {
        if (porcine.stage(player) == STAGE_SLAIN && !carries(SOURHOG_FOOT)) {
            startDialogue {
                chatPlayer(
                    neutral,
                    "Before I leave, I should go and collect a foot from that dead Sourhog as " +
                        "proof of my kill.",
                )
            }
            return
        }
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(CLIMB_TICKS)
        telejump(PorcineCoords.HOLE_SIDE, TeleportType.Exempt)
    }

    /**
     * The blockage plugs a two-tile corridor, so climbing it always lands the player on the same
     * column one tile past it. The warning only comes up once the sourhog has already had the
     * player once, and only until they tell the game to stop asking.
     */
    private suspend fun ProtectedAccess.climbBlockage() {
        val goingSouth = player.coords.z >= PorcineCoords.BLOCKAGE_NORTH.z
        if (goingSouth && shouldWarn() && !confirmClimb()) {
            return
        }
        val landing = if (goingSouth) PorcineCoords.BLOCKAGE_SOUTH else PorcineCoords.BLOCKAGE_NORTH
        val column = player.coords.x.coerceIn(landing.x, landing.x + 1)
        val destination = landing.translateX(column - landing.x)
        arriveDelay()
        faceSquare(destination)
        anim(CLIMB_OVER_SEQ)
        soundSynth(CLIMB_OVER_SOUND)
        delay(CLIMB_TICKS)
        telejump(destination, TeleportType.Exempt)
    }

    private fun ProtectedAccess.shouldWarn(): Boolean =
        porcine.stage(player) >= STAGE_AMBUSHED &&
            !porcine.isComplete(player) &&
            !player.porcineStopWarning

    private suspend fun ProtectedAccess.confirmClimb(): Boolean {
        var climb = false
        startDialogue {
            chatPlayer(
                worried,
                "I don't think Spria will be rescuing me this time. If I go in there, I'm " +
                    "fighting this thing to the death!",
            )
            climb = choice2("Yes", true, "No", false, title = "Climb over the blockage?")
        }
        if (climb) {
            player.porcineStopWarning = true
        }
        return climb
    }

    private companion object {
        const val HOLE_NO_ROPE = "loc.porcine_hole_norope"
        const val HOLE_WITH_ROPE = "loc.porcine_hole_rope"
        const val EXIT_ROPE = "loc.porcine_cave_exit_rope"
        const val BLOCKAGE = "loc.porcine_cave_blockage"
        const val FALLEN_ROPE = "loc.porcine_fallen_rope_visible"

        const val TIE_SEQ = "seq.human_throwrope"
        const val TIE_SOUND = "synth.cf_tierope"
        const val TIE_TICKS = 3

        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_UP_SEQ = "seq.human_climbing"
        const val CLIMB_SOUND = "synth.ropeclimb"
        const val CLIMB_TICKS = 2

        const val CLIMB_OVER_SEQ = "seq.human_jump_hurdle"
        const val CLIMB_OVER_SOUND = "synth.climb_wall"
    }
}
