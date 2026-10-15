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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BALANCE_STUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_DEATH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LADDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_PORTCULLIS_CLOSE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_PORTCULLIS_OPEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEVER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_PORTCULLIS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_EAST_X
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_NORTH_Z
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_SOUTH_Z
import org.rsmod.content.quest.area.ardougne.undergroundpass.UpassCoords.GRID_WEST_X
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The grid of rusted grilles between the pass and the furnace.
 *
 * The safe way over is three bands, each two rows deep, laid across the grid from east to west
 * and overlapping where they meet. Every player has their own: three rows picked from five, each
 * within one of the last, and kept in `varp.upass_grid` so the same player always finds the same
 * way across. A step off the bands drops the player into the spike pit under the grid, from which
 * a hand hold climbs back up to the east side.
 */
@Singleton
class TheGrid
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) : PluginScript() {

    private val portcullisType by lazy { locType(PORTCULLIS) }
    private val leverDownType by lazy { locType(LEVER_DOWN) }

    override fun ScriptContext.startup() {
        onOpLoc1(GRID_LEVER) { pullLever(it.loc) }
        onPlayerCoordsChanged {
            if (lastKnownCoords != player.coords) {
                checkFooting(player)
            }
        }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    /** The lever lifts the portcullis just long enough for the player to duck under it. */
    private suspend fun ProtectedAccess.pullLever(lever: BoundLocInfo) {
        arriveDelay()
        mes("You pull the lever...")
        soundSynth(SOUND_LEVER)
        locRepo.change(lever, leverDownType, LEVER_TICKS)
        delay(1)
        mes("The portcullis opens.")
        soundSynth(SOUND_PORTCULLIS)
        animatePortcullis(SEQ_LOC_PORTCULLIS_OPEN)
        val walk = UpassCoords.PORTCULLIS_WALK
        if (coords.x >= lever.coords.x) {
            stepThrough(lineTo(walk.first()) + walk)
        } else {
            playerWalk(walk.last())
            stepThrough(walk.reversed())
        }
        mes("The portcullis closes.")
        animatePortcullis(SEQ_LOC_PORTCULLIS_CLOSE)
    }

    private fun ProtectedAccess.animatePortcullis(seq: String) {
        for (coords in UpassCoords.PORTCULLIS) {
            locRepo.findExact(coords, portcullisType)?.let { locAnim(worldRepo, it, seq) }
        }
    }

    /** Runs on every step: a foot off the player's own path goes through the grille. */
    private fun checkFooting(player: Player) {
        val coords = player.coords
        if (!coords.onGrid() || coords.isSafeFor(player)) {
            return
        }
        launcher.launch(player) { fallThrough() }
    }

    private suspend fun ProtectedAccess.fallThrough() {
        stopAction()
        mes("It's a trap!")
        anim(SEQ_BALANCE_STUMBLE)
        delay(1)
        mes("You fall onto the spikes.")
        telejump(
            UpassCoords.GRID_PIT.translate(random.of(0, 1), random.of(0, 1)),
            TeleportType.Exempt,
        )
        anim(SEQ_DEATH)
        delay(1)
        resetAnim()
        say("Ouch!")
        takeInstantHit(HitType.Typeless, FALL_DAMAGE)
        // Every tile of the pit is spikes, so the hand holds cannot be walked to; climb for them.
        delay(PIT_TICKS)
        anim(SEQ_LADDER)
        delay(1)
        mes("You crawl out of the pit.")
        telejump(UpassCoords.GRID_CLIMB_OUT, TeleportType.Exempt)
    }

    private fun CoordGrid.onGrid(): Boolean =
        level == 0 && x in GRID_WEST_X..GRID_EAST_X && z in GRID_SOUTH_Z..GRID_NORTH_Z

    /** Inside one of the player's three bands. */
    private fun CoordGrid.isSafeFor(player: Player): Boolean {
        val rows = player.gridRows()
        return BANDS.withIndex().any { (index, band) ->
            val bottom = GRID_SOUTH_Z + (rows[index] - 1) * 2
            x in band && z in bottom..bottom + 1
        }
    }

    private fun Player.gridRows(): IntArray {
        if (gridSeed !in VALID_SEEDS || gridSeed.toString().any { it !in '1'..'5' }) {
            var row = random.of(1, BAND_ROWS)
            var value = row
            repeat(BANDS.size - 1) {
                row = random.of(maxOf(row - 1, 1), minOf(row + 1, BAND_ROWS))
                value = value * 10 + row
            }
            gridSeed = value
        }
        val seed = gridSeed
        return intArrayOf(seed / 100 % 10, seed / 10 % 10, seed % 10)
    }

    private companion object {
        const val GRID_LEVER = "loc.portcullis_lever_up"
        const val LEVER_DOWN = "loc.upass_lever_down"
        const val PORTCULLIS = "loc.portcullis_upass"

        /** The columns each band covers, east to west; neighbouring bands share two columns. */
        val BANDS = listOf(2473..2476, 2469..2474, 2467..2470)
        const val BAND_ROWS = 5
        val VALID_SEEDS = 111..555

        const val FALL_DAMAGE = 15
        const val PIT_TICKS = 3
        const val LEVER_TICKS = 15
    }
}
