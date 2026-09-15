package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.BALLISTA_FIRED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_FINDING_TRACKERS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_LOGS_GIVEN
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnome ballista in the south-west corner of the battlefield. The height and y coordinates
 * are entered for the player; the x coordinate is the one the mad tracker hinted at, chosen from
 * a menu. A direct hit breaches the stronghold wall for good.
 */
class Ballista
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(BALLISTA) { fire(it.loc) }
    }

    private suspend fun ProtectedAccess.fire(ballista: BoundLocInfo) {
        val stage = treeGnomeVillage.stage(player)
        when {
            stage < STAGE_LOGS_GIVEN ->
                mesbox("The ballista is damaged. It cannot be used until the gnomes have finished their repairs.")
            stage >= STAGE_BREACHED -> mesbox("The Khazard stronghold has already been breached.")
            stage < STAGE_FINDING_TRACKERS || !treeGnomeVillage.hasAllCoordinates(player) ->
                mesbox("I don't have all the coordinates of the stronghold yet. The tracker gnomes should have them.")
            else -> aim(ballista)
        }
    }

    private suspend fun ProtectedAccess.aim(ballista: BoundLocInfo) {
        startDialogue {
            chatPlayer(quiz, "That tracker gnome was a bit vague about the x coordinate! What could it be?")
        }
        val chosen =
            choice4(
                "0001", 1,
                "0002", 2,
                "0003", 3,
                "0004", 4,
                title = "Enter the x-coordinate of the stronghold",
            )
        mesbox("You enter the height and y coordinates you got from the tracker gnomes.")
        faceLoc(ballista)
        soundArea(worldRepo, ballista.coords, LAUNCH_SOUND, radius = SOUND_RADIUS)
        locAnim(worldRepo, ballista, LAUNCH_SEQ)
        delay(FLIGHT_TICKS)
        if (chosen != treeGnomeVillage.trackerX.get(player)) {
            mesbox("The huge spear completely misses the Khazard stronghold!")
            startDialogue { chatPlayer(sad, "Evidently that wasn't the right x coordinate.") }
            return
        }
        soundArea(worldRepo, STRONGHOLD_WALL, HIT_SOUND, radius = SOUND_RADIUS)
        treeGnomeVillage.ballistaState.set(player, BALLISTA_FIRED)
        treeGnomeVillage.syncVars(player)
        treeGnomeVillage.advanceTo(this, STAGE_BREACHED)
        mesbox(
            "The huge spear flies through the air and screams down directly into the Khazard " +
                "stronghold. A deafening crash echoes over the battlefield as the front entrance " +
                "is reduced to rubble."
        )
    }

    private companion object {
        const val BALLISTA = "loc.catabow"
        const val LAUNCH_SEQ = "seq.catabow_launch"
        const val LAUNCH_SOUND = "synth.treevillage_catabow_launch"
        const val HIT_SOUND = "synth.treevillage_catabow_hit"
        const val SOUND_RADIUS = 15
        const val FLIGHT_TICKS = 4

        /** The crumbled wall of the stronghold, where the spear lands. */
        val STRONGHOLD_WALL = CoordGrid(2509, 3253, 0)
    }
}
