package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PIPE_OPEN
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PIPE_ROPE_TIED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_CHECKED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.ardougne.plaguecity.npcs.Edmond
import org.rsmod.content.quest.area.ardougne.wearingGasMask
import org.rsmod.content.quest.area.lumbridge.lostcity.nearestFree
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The stretch of the Ardougne sewers under Edmond's garden: the mud pile back up to the garden,
 * the grilled pipe at the south end that comes up in the West Ardougne town square, and the
 * manhole in that square that leads back down.
 *
 * The grill and the rope tied to it are varbit multilocs driven by `varbit.plaguecity_pipe`, so
 * every player sees the state matching their own progress.
 */
class ArdougneSewer
@Inject
constructor(
    private val plagueCity: PlagueCityQuest,
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (grill in GRILLS) {
            onOpLoc1(grill) { pullGrill() }
            onOpLocU(grill, ROPE) { tieRope() }
        }
        onOpLoc1(PIPE) { climbPipe() }
        onOpLoc1(MUD_PILE) { climbMudPile() }
        onOpLoc1(MANHOLE_CLOSED) { openManhole(it.loc) }
        onOpLoc1(MANHOLE_OPEN) { climbDownManhole() }
        onOpLoc1(MANHOLE_COVER) { closeManhole(it.loc) }
    }

    private suspend fun ProtectedAccess.pullGrill() {
        arriveDelay()
        faceSquare(GRILL_TILE)
        if (plagueCity.pipeState.get(player) == PIPE_OPEN) {
            mes("The grill has already been pulled off.")
            return
        }
        anim(PULL_SEQ)
        soundSynth(RATTLE_SOUND)
        delay(2)
        if (!plagueCity.checkedGrill.get(player)) {
            plagueCity.checkedGrill.set(player, true)
            plagueCity.syncVars(player)
        }
        if (plagueCity.stage(player) == STAGE_DUG_TUNNEL) {
            plagueCity.advanceTo(this, STAGE_GRILL_CHECKED)
        }
        mesbox("The grill is too secure. You can't pull it off alone.")
    }

    private suspend fun ProtectedAccess.tieRope() {
        arriveDelay()
        faceSquare(GRILL_TILE)
        when {
            plagueCity.pipeState.get(player) == PIPE_OPEN -> mes("The grill has already been pulled off.")
            plagueCity.pipeState.get(player) == PIPE_ROPE_TIED -> mes("There is already a rope tied to the grill.")
            !plagueCity.checkedGrill.get(player) -> startDialogue { chatPlayer(quiz, "Maybe I should try opening it first.") }
            else -> {
                anim(TIE_SEQ)
                delayBySeq(TIE_SEQ)
                invDel(inv, ROPE)
                plagueCity.pipeState.set(player, PIPE_ROPE_TIED)
                plagueCity.syncVars(player)
                if (plagueCity.stage(player) == STAGE_GRILL_CHECKED) {
                    plagueCity.advanceTo(this, STAGE_ROPE_TIED)
                }
                objbox(ROPE, "You tie the end of the rope to the sewer pipe's grill.")
            }
        }
    }

    /** Up the pipe into the West Ardougne town square. Edmond insists on the mask. */
    private suspend fun ProtectedAccess.climbPipe() {
        arriveDelay()
        faceSquare(GRILL_TILE)
        if (plagueCity.pipeState.get(player) != PIPE_OPEN) {
            mesbox("There is a grill blocking your way.")
            return
        }
        if (!player.wearingGasMask()) {
            startDialogue {
                chatNpcSpecific("Edmond", Edmond.EDMOND_HEAD, worried, "I can't let you enter the city without your gas mask on.")
            }
            return
        }
        anim(PIPE_SQUEEZE_SEQ)
        soundSynth(CLIMB_SOUND)
        delayBySeq(PIPE_SQUEEZE_SEQ)
        telejump(collision.nearestFree(SQUARE_ARRIVAL) ?: SQUARE_ARRIVAL)
        mesbox("You climb up through the sewer pipe.")
    }

    private suspend fun ProtectedAccess.climbMudPile() {
        arriveDelay()
        faceSquare(MUD_PILE_TILE)
        anim(CLIMB_UP_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(collision.nearestFree(GARDEN_ARRIVAL) ?: GARDEN_ARRIVAL)
        mes("You climb up the mud pile.")
    }

    private suspend fun ProtectedAccess.openManhole(manhole: BoundLocInfo) {
        arriveDelay()
        soundSynth(MANHOLE_OPEN_SOUND)
        locRepo.change(manhole, MANHOLE_OPEN, MANHOLE_TICKS)
        mes("You pull back the manhole cover.")
    }

    private suspend fun ProtectedAccess.closeManhole(cover: BoundLocInfo) {
        arriveDelay()
        soundSynth(MANHOLE_CLOSE_SOUND)
        locRepo.change(cover, MANHOLE_CLOSED, MANHOLE_TICKS)
        mes("You close the manhole cover.")
    }

    private suspend fun ProtectedAccess.climbDownManhole() {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(collision.nearestFree(PIPE_ARRIVAL) ?: PIPE_ARRIVAL)
        mesbox("You climb down through the manhole.")
    }

    companion object {
        /** The varbit multiloc and the form it shows while the grill is still on. */
        val GRILLS = listOf("loc.plague_grill", "loc.plague_grill_vis")
        const val PIPE = "loc.plaguesewerpipe_open"
        const val MUD_PILE = "loc.plaguemudpile"
        const val MANHOLE_CLOSED = "loc.plaguemanholeclosed"
        const val MANHOLE_OPEN = "loc.plaguemanholeopen"
        const val MANHOLE_COVER = "loc.plaguemanholecover"
        const val ROPE = "obj.rope"

        val GRILL_TILE = CoordGrid(2514, 9739, 0)
        val MUD_PILE_TILE = CoordGrid(2519, 9759, 0)

        /** Just north of the manhole in the town square. */
        val SQUARE_ARRIVAL = CoordGrid(2529, 3304, 0)

        /** The mouth of the pipe, at the south end of the sewer corridor. */
        val PIPE_ARRIVAL = CoordGrid(2514, 9738, 0)

        /** The mud patch behind Edmond's house. */
        val GARDEN_ARRIVAL = CoordGrid(2566, 3332, 0)

        const val PULL_SEQ = "seq.human_leverup"
        const val TIE_SEQ = "seq.rope_tie"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val PIPE_SQUEEZE_SEQ = "seq.plaguecity_human_doublepipesqueeze"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladdertop"
        const val RATTLE_SOUND = "synth.irondoor_locked"
        const val CLIMB_SOUND = "synth.climb_wall"
        const val MANHOLE_OPEN_SOUND = "synth.manhole_open"
        const val MANHOLE_CLOSE_SOUND = "synth.manhole_close"

        const val MANHOLE_TICKS = 100
    }
}
