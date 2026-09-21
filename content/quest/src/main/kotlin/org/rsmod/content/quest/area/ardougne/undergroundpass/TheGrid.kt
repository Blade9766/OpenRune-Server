package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_CLIMB_OUT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_HANG_READY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_STUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_PORTCULLIS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_EAST_X
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_NORTH_Z
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_SIZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_SOUTH_Z
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_WEST_X
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The floor of rotten grilles between the pass and the furnace.
 *
 * Only one route across it holds. Which route that is differs from player to player and never
 * changes once it has been drawn, because Regicide sends the same player back over the same grid:
 * the whole path is generated from a seed kept in `varp.upass_grid`.
 *
 * The path is drawn one column at a time from the east side to the west. Within a column the safe
 * squares are every row between where the player entered it and where it lets them leave, so a
 * route across never asks for a step backwards or a step on the diagonal - the only two things
 * the grid's own description promises. Anything else gives way underfoot.
 */
@Singleton
class TheGrid
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) : PluginScript() {

    private val portcullisType by lazy { locType(PORTCULLIS) }

    override fun ScriptContext.startup() {
        onOpLoc1(GRID_LEVER) { pullLever() }
        onOpLoc1(TRAP) { mes("The grille is rusted through. It would never take my weight.") }
        onPlayerCoordsChanged { checkFooting(player, lastKnownCoords) }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.pullLever() {
        arriveDelay()
        anim(SEQ_LEVER)
        soundSynth(SOUND_LEVER)
        delay(2)
        if (player.portcullisOpen) {
            mes("The portcullis is already up.")
            return
        }
        soundSynth(SOUND_PORTCULLIS)
        for (coords in UpassCoords.PORTCULLIS) {
            locRepo.findExact(coords, portcullisType)?.let { locRepo.del(it, Int.MAX_VALUE) }
        }
        player.portcullisOpen = true
        mes("The portcullis grinds up into the roof.")
    }

    /**
     * Runs on every step. A player standing on a square of the grid that is not on their own path
     * goes through it, and comes back up on the square they stepped from.
     */
    private fun checkFooting(player: Player, from: CoordGrid) {
        val coords = player.coords
        if (!coords.onGrid() || coords.isSafeFor(player)) {
            return
        }
        val safety =
            when {
                from.onGrid() && from.isSafeFor(player) -> from
                !from.onGrid() && from.nextToGrid() -> from
                else -> UpassCoords.GRID_EAST_LANDING
            }
        launcher.launch(player) { fallThrough(safety) }
    }

    private suspend fun ProtectedAccess.fallThrough(safety: CoordGrid) {
        anim(SEQ_STUMBLE)
        soundSynth(FALL_SOUND)
        mes("The grille gives way under you!")
        delay(1)
        takeInstantHit(HitType.Typeless, FALL_DAMAGE)
        anim(SEQ_HANG_READY)
        delay(2)
        anim(SEQ_CLIMB_OUT)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(safety, TeleportType.Exempt)
        resetAnim()
        mes("You haul yourself back onto solid ground.")
    }

    private fun CoordGrid.onGrid(): Boolean =
        level == 0 && x in GRID_WEST_X..GRID_EAST_X && z in GRID_SOUTH_Z..GRID_NORTH_Z

    private fun CoordGrid.nextToGrid(): Boolean =
        level == 0 &&
            x in (GRID_WEST_X - 1)..(GRID_EAST_X + 1) &&
            z in (GRID_SOUTH_Z - 1)..(GRID_NORTH_Z + 1)

    private fun CoordGrid.isSafeFor(player: Player): Boolean {
        val rows = player.gridPath()
        // Column 0 is the east edge, where the path starts; each column west of it is spanned by
        // the rows between where the path entered it and where it leaves.
        val column = GRID_EAST_X - x
        val row = z - GRID_SOUTH_Z
        val entry = if (column == 0) rows[0] else rows[column - 1]
        val exit = rows[column]
        return row in minOf(entry, exit)..maxOf(entry, exit)
    }

    /**
     * The player's own route, drawn once and then kept. Each column's row is within one of the
     * column before it, so the safe squares always join up.
     */
    private fun Player.gridPath(): IntArray {
        if (gridSeed == 0) {
            gridSeed = random.of(1, MAX_SEED)
        }
        var state = gridSeed
        val rows = IntArray(GRID_SIZE)
        rows[0] = state % GRID_SIZE
        for (column in 1 until GRID_SIZE) {
            state = (state * SEED_MULTIPLIER + SEED_INCREMENT) and SEED_MASK
            val step = (state / SEED_SHIFT) % STEP_RANGE - 1
            rows[column] = (rows[column - 1] + step).coerceIn(0, GRID_SIZE - 1)
        }
        return rows
    }

    private companion object {
        const val GRID_LEVER = "loc.portcullis_lever_up"
        const val PORTCULLIS = "loc.portcullis_upass"
        const val TRAP = "loc.gill_trapa"

        const val FALL_DAMAGE = 15
        const val FALL_SOUND = "synth.jump_and_fall"
        const val CLIMB_SOUND = "synth.climb_wall"

        /** A small linear congruential step, enough to scatter ten rows without a repeat pattern. */
        const val MAX_SEED = 0xFFFF
        const val SEED_MULTIPLIER = 1103515245
        const val SEED_INCREMENT = 12345
        const val SEED_MASK = 0x7FFFFFFF
        const val SEED_SHIFT = 65536
        const val STEP_RANGE = 3
    }
}
