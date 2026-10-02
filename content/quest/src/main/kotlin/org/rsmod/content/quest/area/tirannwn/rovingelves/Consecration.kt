package org.rsmod.content.quest.area.tirannwn.rovingelves

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.NEW_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.SPADE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANT_SEED
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Planting the enchanted consecration seed ("Plant") beside the chalice in Glarial's resting
 * place. The unenchanted seed has no Plant option at all.
 *
 * Baxtorian's tomb is mapped twice (see the Waterfall Quest's `WaterfallDungeon`): the room the
 * key door leads into, where the chalice floats above the floor, and the raised copy a player
 * stands in after placing Glarial's amulet, where it rests on the floor. Either chalice will do.
 *
 * Deleting the seed and recording the planting happen in the same tick, before anything waits,
 * so an interrupted ritual has either not started or already counts. The crystal growth that
 * follows is a short-lived loc without options on a free tile next to the player: purely visual,
 * so another player who sees it is not advanced by it.
 */
class Consecration
@Inject
constructor(
    private val roving: RovingElvesQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(NEW_SEED) { plant() }
    }

    private suspend fun ProtectedAccess.plant() {
        val stage = roving.stage(player)
        if (stage > STAGE_PLANT_SEED) {
            mes("Glarial's resting place has already been consecrated.")
            return
        }
        if (stage < STAGE_PLANT_SEED) {
            mes("You don't know what to do with this seed.")
            return
        }
        if (!inRitualArea(player.coords)) {
            mes("This isn't the right place. The seed must be planted beside the chalice where Glarial rests.")
            return
        }
        if (SPADE !in inv) {
            mes("You need a spade to dig a hole for the seed.")
            return
        }
        if (!commitPlanting()) {
            return
        }
        delay(DIG_TICKS)
        growCrystal()
        mesbox("You plant the seed beside the chalice. As you step back, crystal begins to grow from the earth, singing softly.")
    }

    /** The commit point: the seed goes and the stage moves, or neither happens. */
    fun ProtectedAccess.commitPlanting(): Boolean {
        anim(DIG_SEQ)
        if (invDel(inv, NEW_SEED).failure) {
            return false
        }
        roving.advanceTo(this, STAGE_PLANTED)
        return true
    }

    private fun ProtectedAccess.growCrystal() {
        val tile = NEIGHBOURS.map { (dx, dz) -> player.coords.translate(dx, dz) }.firstOrNull(::isFree) ?: return
        faceSquare(tile)
        val loc = locRepo.add(tile, GROWTH_LOC, GROWTH_TICKS, LocAngle.South, LocShape.CentrepieceStraight)
        worldRepo.locAnim(loc, GROWTH_SEQ)
    }

    private fun isFree(tile: CoordGrid): Boolean =
        collision.isZoneAllocated(tile.x, tile.z, tile.level) &&
            collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    companion object {
        const val GROWTH_LOC = "loc.roving_crystal_growth"
        const val GROWTH_SEQ = "seq.roving_crystal_growth_start"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_TICKS = 2
        const val GROWTH_TICKS = 15

        private val NEIGHBOURS = listOf(0 to 1, 1 to 0, -1 to 0, 0 to -1)

        /** The chalice on the floor of the raised copy (2x2 at 2603,9910) and the tiles around it. */
        private val RAISED_CHALICE = Ritual(2602..2605, 9909..9912)

        /** Below and around the chalice floating over the room the key door opens on (2x2 at 2565,9911). */
        private val FLOATING_CHALICE = Ritual(2564..2567, 9910..9913)

        fun inRitualArea(coords: CoordGrid): Boolean =
            coords.level == 0 && (RAISED_CHALICE.contains(coords) || FLOATING_CHALICE.contains(coords))

        private data class Ritual(val x: IntRange, val z: IntRange) {
            fun contains(coords: CoordGrid): Boolean = coords.x in x && coords.z in z
        }
    }
}
