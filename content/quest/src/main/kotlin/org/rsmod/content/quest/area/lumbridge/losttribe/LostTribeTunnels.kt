package org.rsmod.content.quest.area.lumbridge.losttribe

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_READ_BOOK
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The unstable tunnels between the castle cellar and the Dorgeshuun mines.
 *
 * The safe route is marked by tribal symbols carved into the walls, each pointing the way its
 * tribe's direction (north, south, east or west). Every wrong turning ends at one of the cache's
 * trap markers: `loc.lost_tribe_trap_ceiling` drops rocks on the player and
 * `loc.lost_tribe_trap_floor` gives way into the Lumbridge Swamp Caves below, where swamp gas
 * catches any naked flame. The trap tiles are the tiles those markers sit on.
 */
class LostTribeTunnels
@Inject
constructor(
    private val lostTribe: LostTribeQuest,
    private val launcher: ProtectedAccessLauncher,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged {
            if (lastKnownCoords != player.coords) {
                checkTraps(player, lastKnownCoords)
            }
        }
        for ((symbol, tribe) in SYMBOLS) {
            onOpLoc1(symbol) { lookAtSymbol(tribe) }
        }
    }

    private fun checkTraps(player: Player, previous: CoordGrid) {
        val coords = player.coords
        when (coords) {
            in CEILING_TRAPS -> launcher.launch(player) { rockfall(previous) }
            in FLOOR_TRAPS -> launcher.launch(player) { fallThrough() }
        }
    }

    private suspend fun ProtectedAccess.rockfall(previous: CoordGrid) {
        stopAction()
        val boulder = locRepo.add(coords, BOULDER, TRAP_LOC_TICKS, LocAngle.West, LocShape.GroundDecor)
        locAnim(worldRepo, boulder, ROCKFALL_SEQ)
        soundSynth(ROCKFALL_SOUND)
        mes("Rocks fall from the ceiling!")
        anim(STUMBLE_SEQ)
        delay(1)
        takeInstantHit(HitType.Typeless, random.of(ROCKFALL_DAMAGE))
        walk(previous)
    }

    private suspend fun ProtectedAccess.fallThrough() {
        stopAction()
        val floor = locRepo.add(coords, COLLAPSING_FLOOR, TRAP_LOC_TICKS, LocAngle.West, LocShape.GroundDecor)
        locAnim(worldRepo, floor, COLLAPSE_SEQ)
        soundSynth(PITFALL_SOUND)
        mes("The floor collapses beneath you!")
        anim(FALL_SEQ)
        delay(2)
        telejump(SWAMP_LANDING, TeleportType.Exempt)
        anim(LAND_SEQ)
        soundSynth(LAND_SOUND)
        takeInstantHit(HitType.Typeless, random.of(FALL_DAMAGE))
        mes("You fall into the caves beneath Lumbridge Swamp.")
        igniteSwampGas()
    }

    /** Naked flames set the swamp gas off; enclosed lanterns are safe. */
    private suspend fun ProtectedAccess.igniteSwampGas() {
        val flame = NAKED_FLAMES.keys.firstOrNull { it in player.inv } ?: return
        delay(1)
        soundSynth(EXPLOSION_SOUND)
        mes("The swamp gas ignites! Your light source is blown out in the explosion.")
        takeInstantHit(HitType.Typeless, random.of(EXPLOSION_DAMAGE))
        if (invDel(inv, flame).success) {
            invAdd(inv, NAKED_FLAMES.getValue(flame))
        }
    }

    private suspend fun ProtectedAccess.lookAtSymbol(tribe: Tribe) {
        arriveDelay()
        if (lostTribe.stage(player) < STAGE_READ_BOOK) {
            mes("A strange symbol has been carved into the rock.")
            return
        }
        mes("It's the symbol of the ${tribe.tribeName}, the goblins of the ${tribe.direction}.")
    }

    private enum class Tribe(val tribeName: String, val direction: String) {
        North("Rekeshuun", "north"),
        South("Idithuun", "south"),
        East("Ekeleshuun", "east"),
        West("Narogoshuun", "west"),
    }

    private companion object {
        const val BOULDER = "loc.lost_tribe_collapsing_roof_location"
        const val COLLAPSING_FLOOR = "loc.lost_tribe_collapsing_floor"
        const val ROCKFALL_SEQ = "seq.big_rockfall"
        const val COLLAPSE_SEQ = "seq.collapsing_floor"
        const val STUMBLE_SEQ = "seq.human_stumble_back"
        const val FALL_SEQ = "seq.human_falling"
        const val LAND_SEQ = "seq.human_falling_end"
        const val ROCKFALL_SOUND = "synth.cave_collapse"
        const val PITFALL_SOUND = "synth.pitfall_fall"
        const val LAND_SOUND = "synth.fall_land"
        const val EXPLOSION_SOUND = "synth.gas_explosion"

        const val TRAP_LOC_TICKS = 3
        val ROCKFALL_DAMAGE = 1..4
        val FALL_DAMAGE = 1..3
        val EXPLOSION_DAMAGE = 3..6

        /** Beneath the maze, at the foot of the swamp caves' hole back up into the tunnels. */
        val SWAMP_LANDING = CoordGrid(3223, 9597, 0)

        val SYMBOLS =
            listOf(
                "loc.swamp_cavewall_northmarker" to Tribe.North,
                "loc.swamp_cavewall_southmarker" to Tribe.South,
                "loc.swamp_cavewall_eastmarker" to Tribe.East,
                "loc.swamp_cavewall_westmarker" to Tribe.West,
            )

        val NAKED_FLAMES =
            mapOf(
                "obj.lit_candle" to "obj.unlit_candle",
                "obj.lit_black_candle" to "obj.unlit_black_candle",
                "obj.torch_lit" to "obj.torch_unlit",
                "obj.oil_lamp_lit" to "obj.oil_lamp_unlit",
            )

        val CEILING_TRAPS =
            setOf(
                CoordGrid(3249, 9646, 0),
                CoordGrid(3250, 9646, 0),
                CoordGrid(3255, 9616, 0),
                CoordGrid(3260, 9636, 0),
                CoordGrid(3260, 9637, 0),
                CoordGrid(3269, 9617, 0),
                CoordGrid(3269, 9618, 0),
                CoordGrid(3273, 9641, 0),
                CoordGrid(3273, 9642, 0),
            )

        val FLOOR_TRAPS =
            setOf(
                CoordGrid(3238, 9622, 0),
                CoordGrid(3239, 9622, 0),
                CoordGrid(3244, 9635, 0),
                CoordGrid(3244, 9636, 0),
                CoordGrid(3275, 9630, 0),
                CoordGrid(3275, 9631, 0),
                CoordGrid(3287, 9631, 0),
                CoordGrid(3288, 9631, 0),
                CoordGrid(3301, 9618, 0),
                CoordGrid(3302, 9618, 0),
            )
    }
}
