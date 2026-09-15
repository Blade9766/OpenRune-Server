package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ELKOY_HEAD
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Where Elkoy leaves the player when he leads them through the maze. */
object GnomeMaze {
    /** Beside Elkoy at the north-west entrance, outside the hedges. */
    val ENTRANCE = CoordGrid(2503, 3191, 0)

    /** The village side of the loose railing, beside the second Elkoy. */
    val VILLAGE_GATE = CoordGrid(2515, 3160, 0)

    /** The railing hangs on the south edge of this tile; the village lies south of it. */
    val RAILING_TILE = CoordGrid(2515, 3161, 0)

    /** Anything south of the railing's tile row is inside the village. */
    fun insideVillage(coords: CoordGrid): Boolean = coords.z < RAILING_TILE.z
}

/**
 * Elkoy walks the player through the maze: the screen fades, the player reappears at [dest] and
 * Elkoy has a parting word.
 */
internal suspend fun ProtectedAccess.elkoyGuides(dest: CoordGrid, line: String, boxText: String? = null) {
    if (boxText != null) {
        mesbox(boxText)
    }
    fadeToBlack()
    teleport(dest)
    delay(1)
    fadeFromBlack()
    closeFadeOverlay()
    startDialogue { chatNpcSpecific("Elkoy", ELKOY_HEAD, happy, line) }
}

/**
 * The loose railing between the maze and the village. Squeezing through takes the player from
 * the maze tile to the village tile, or back.
 */
class LooseRailing @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(RAILING) { squeeze(it.loc) }
    }

    private suspend fun ProtectedAccess.squeeze(railing: BoundLocInfo) {
        val fromVillage = GnomeMaze.insideVillage(player.coords)
        val start = if (fromVillage) railing.coords.translateZ(-1) else railing.coords
        val end = if (fromVillage) railing.coords else railing.coords.translateZ(-1)
        if (player.coords != start) {
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
            dir = if (fromVillage) constants.em_face_north else constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(SQUEEZE_TICKS)
    }

    private companion object {
        const val RAILING = "loc.treegnomelooserailing"
        const val SQUEEZE_SEQ = "seq.railing_squeeze"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val SQUEEZE_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
    }
}
