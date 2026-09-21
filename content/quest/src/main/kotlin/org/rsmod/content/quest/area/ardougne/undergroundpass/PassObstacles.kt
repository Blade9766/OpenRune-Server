package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PICKLOCK_THIEVING_REQ
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ROPE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BALANCE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_CLIMB
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_PICKLOCK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_ROPESWING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SQUEEZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEDGE
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Everything in the pass that is crossed rather than opened: the rockslides along the north wall,
 * the pipes, the tunnels between the unicorn's caves, the narrow ledge over the chasm, the stone
 * bridges of the beacon maze, the rope swing and the locked cell gates.
 *
 * All of them stand on tiles the routefinder refuses, so none of them can be walked over. Each one
 * works out the tile directly across itself from wherever the player is standing, snaps that to
 * the nearest tile the player can actually stand on, and glides them over.
 */
@Singleton
class PassObstacles
@Inject
constructor(
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) : PluginScript() {

    private val ropedSwingType by lazy { locType(ROPE_SWING) }

    override fun ScriptContext.startup() {
        for (pipe in PIPES) {
            onOpLoc1(pipe) { squeezeThrough(it.loc) }
        }
        for (tunnel in TUNNELS) {
            onOpLoc1(tunnel) { passThrough(it.loc) }
        }
        for (slide in ROCKSLIDES) {
            onOpLoc1(slide) { clamberOver(it.loc) }
        }
        onOpLoc1(CELL_GATE) { pickGate(it.loc, requiredLevel = 0) }
        onOpLoc1(MAZE_GATE) { pickGate(it.loc, requiredLevel = PICKLOCK_THIEVING_REQ) }
        onOpLoc1(LEDGE) { crossLedge() }
        onOpLoc1(STONE_BRIDGE) { crossStoneBridge(it.loc) }
        onOpLoc1(ROPE_SWING) { swingAcross(it.loc) }
        onOpLocU(BARE_ROCK, ROPE) { tieRope(it.loc) }
        onOpLoc1(BARE_ROCK) {
            mes("There is nothing to hold on to. If I had a rope I could tie it to the rock.")
        }
        onOpLoc1(HAND_HOLDS) { clamberOver(it.loc) }
        onOpLoc1(ROCKPILE) { climbToWell() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.squeezeThrough(loc: BoundLocInfo) {
        arriveDelay()
        val dest = landing(acrossFrom(loc)) ?: return refuse()
        soundSynth(SQUEEZE_SOUND)
        climbOver(dest, SEQ_SQUEEZE, SQUEEZE_TICKS)
        mes("You squeeze through the pipe.")
    }

    private suspend fun ProtectedAccess.passThrough(loc: BoundLocInfo) {
        arriveDelay()
        val dest = landing(acrossFrom(loc)) ?: return refuse()
        climbOver(dest, SEQ_CRAWL, CRAWL_TICKS)
        mes("You crawl through the tunnel.")
    }

    private suspend fun ProtectedAccess.clamberOver(loc: BoundLocInfo) {
        arriveDelay()
        val dest = landing(acrossFrom(loc)) ?: return refuse()
        soundSynth(CLIMB_SOUND)
        climbOver(dest, SEQ_CLIMB, CLIMB_TICKS)
    }

    /**
     * The five cell gates. Anyone can force a gate that is already on the far side of it, so the
     * lock only matters coming in; leaving needs no pick.
     */
    private suspend fun ProtectedAccess.pickGate(loc: BoundLocInfo, requiredLevel: Int) {
        arriveDelay()
        val dest = landing(acrossFrom(loc)) ?: return refuse()
        if (requiredLevel > 0 && player.mazeGatePicked) {
            climbOver(dest, SEQ_SQUEEZE, CRAWL_TICKS)
            return
        }
        if (requiredLevel > 0 && statBase("stat.thieving") < requiredLevel) {
            mes("You need a Thieving level of $requiredLevel to pick a lock this good.")
            return
        }
        if (!invContains(inv, LOCKPICK)) {
            mes("The gate is locked, and the lock is far too good to force by hand.")
            return
        }
        anim(SEQ_PICKLOCK)
        soundSynth(PICK_SOUND)
        delay(PICK_TICKS)
        if (requiredLevel > 0 && !statRandom("stat.thieving", PICK_LOW, PICK_HIGH, 0)) {
            mes("You fail to pick the lock.")
            return
        }
        soundSynth(UNLOCK_SOUND)
        if (requiredLevel > 0) {
            player.mazeGatePicked = true
        }
        mes("You pick the lock and slip through.")
        climbOver(dest, SEQ_SQUEEZE, CRAWL_TICKS)
    }

    private suspend fun ProtectedAccess.crossLedge() {
        arriveDelay()
        val target = farEndOf(UpassCoords.LEDGE_SOUTH, UpassCoords.LEDGE_NORTH)
        val dest = landing(target, LEDGE_SEARCH_RADIUS) ?: return refuse()
        soundSynth(SOUND_LEDGE)
        mes("You edge out along the ledge...")
        climbOver(dest, SEQ_BALANCE, LEDGE_TICKS)
        mes("...and reach the far side.")
    }

    private suspend fun ProtectedAccess.crossStoneBridge(loc: BoundLocInfo) {
        arriveDelay()
        val dest = landing(acrossFrom(loc)) ?: return refuse()
        soundSynth(SOUND_LEDGE)
        climbOver(dest, SEQ_BALANCE, LEDGE_TICKS)
    }

    private suspend fun ProtectedAccess.tieRope(loc: BoundLocInfo) {
        arriveDelay()
        if (invDel(inv, ROPE).failure) {
            return
        }
        anim(SEQ_TIE_ROPE)
        delay(1)
        locRepo.change(loc, ropedSwingType, Int.MAX_VALUE)
        player.ropeOnSwing = true
        mes("You tie your rope securely round the rock.")
    }

    private suspend fun ProtectedAccess.swingAcross(loc: BoundLocInfo) {
        arriveDelay()
        val dest = landing(acrossFrom(loc), SWING_SEARCH_RADIUS) ?: return refuse()
        soundSynth(SWING_SOUND)
        mes("You swing across the pit.")
        climbOver(dest, SEQ_ROPESWING, SWING_TICKS)
    }

    /** The chute back up out of the prison, to the lip of the well the player came down. */
    private suspend fun ProtectedAccess.climbToWell() {
        arriveDelay()
        soundSynth(CLIMB_SOUND)
        anim(SEQ_CLIMB)
        delay(CLIMB_TICKS)
        telejump(UpassCoords.WELL_CLIMB_BACK)
        mes("You climb the rockpile and haul yourself back up into the well shaft.")
    }

    private fun ProtectedAccess.landing(ideal: CoordGrid, radius: Int = LANDING_RADIUS): CoordGrid? =
        collision.nearestFree(ideal, radius)

    private fun ProtectedAccess.refuse() {
        mes("You can't reach the other side from here.")
    }

    private companion object {
        val PIPES =
            arrayOf(
                "loc.upass_pipe4",
                "loc.upass_pipe5",
                "loc.upass_pipe6",
                "loc.upass_pipe7",
                "loc.upass_pipe8",
            )

        val TUNNELS = arrayOf("loc.upass_unicorn_doorl", "loc.upass_unicorn_doorr")

        val ROCKSLIDES = arrayOf("loc.rockslide2_obstacle_upass", "loc.caverockpile")

        const val CELL_GATE = "loc.cave_railings2"
        const val MAZE_GATE = "loc.cave_railings5"

        const val LEDGE = "loc.upass_ledge"
        const val STONE_BRIDGE = "loc.walkway_upass_narrow_mid_top"
        const val ROPE_SWING = "loc.obstical_rockswing_withrope2"
        const val BARE_ROCK = "loc.obstical_rockswing_norope"
        const val HAND_HOLDS = "loc.upass_grilltrap_hand_holds"
        const val ROCKPILE = "loc.mudpile_upass"

        const val LOCKPICK = "obj.lockpick"

        const val SEQ_CRAWL = "seq.human_crawling"
        const val SEQ_TIE_ROPE = "seq.human_pickuptable"

        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val CLIMB_SOUND = "synth.climb_wall"
        const val SWING_SOUND = "synth.ropeclimb"
        const val PICK_SOUND = "synth.pick_lock"
        const val UNLOCK_SOUND = "synth.unlock"

        const val SQUEEZE_TICKS = 3
        const val CRAWL_TICKS = 2
        const val CLIMB_TICKS = 2
        const val LEDGE_TICKS = 4
        const val SWING_TICKS = 3
        const val PICK_TICKS = 3
        const val LANDING_RADIUS = 2
        const val LEDGE_SEARCH_RADIUS = 3
        const val SWING_SEARCH_RADIUS = 3

        const val PICK_LOW = 1
        const val PICK_HIGH = PICKLOCK_THIEVING_REQ + 20
    }
}
