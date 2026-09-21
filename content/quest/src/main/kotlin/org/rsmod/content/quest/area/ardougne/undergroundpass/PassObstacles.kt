package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PICKLOCK_THIEVING_REQ
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ROPE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BALANCE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_BALANCE_STUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_CLIMB_DOWN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LADDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_LOC_ROPESWING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_PICKLOCK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_PIPE_SQUEEZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_ROPESWING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_STUMBLE_BACK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_THROW_ROPE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_GRATE_CLOSE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LEDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_LOCKED_DOOR
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_SQUEEZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_UNICORN
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.content.quest.area.wilderness.magearena.walkable
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Everything in the pass that is crossed rather than opened: the rockslides, the pipes, the
 * tunnels into the unicorn's cave, the narrow ledge over the rat pit, the stone bridges of the
 * maze, the rope swing over the pit and the locked cell gates.
 *
 * The rockslides, the ledge, the stone bridges and the rope swing all roll against Agility; a
 * slip costs a little health and, off the swing, the rope as well.
 */
@Singleton
class PassObstacles
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val ropedRockType by lazy { locType(ROPED_ROCK) }

    override fun ScriptContext.startup() {
        onOpLoc1(ROCKSLIDE) { climbRockslide(it.loc) }
        onOpLoc1(SOUTH_PIPE) { crawlThroughFirstPipe() }
        onOpLoc1(SOUTH_PIPE_GRATE) {
            mes("The other end of the pipe is blocked by a grill.")
            mes("You cannot open the grill from this side.")
        }
        for (pipe in PIPES) {
            onOpLoc1(pipe) { crawlThroughPipe(it.loc) }
        }
        for (tunnel in UNICORN_TUNNELS) {
            onOpLoc1(tunnel) { passThroughTunnel(it.loc) }
        }
        onOpLoc1(CELL_GATE) { pickGate(it.loc, requiredLevel = 0) }
        onOpLoc2(CELL_GATE) { searchBars() }
        onOpLoc1(MAZE_GATE) { pickGate(it.loc, requiredLevel = PICKLOCK_THIEVING_REQ) }
        onOpLoc2(MAZE_GATE) { searchBars() }
        onOpLoc1(LEDGE) { crossLedge(it.loc) }
        onOpLoc1(STONE_BRIDGE) { crossStoneBridge(it.loc) }
        onOpLocU(BARE_ROCK, ROPE) { ropeSwing(it.loc) }
        onOpLocU(ROPED_ROCK, ROPE) { mesbox("The rock is being used.") }
        onOpLoc1(ROPED_ROCK) { mesbox("The rock is being used.") }
        onOpLoc1(ROPE_SWING_BACK) { swingBack() }
        onOpLoc1(HAND_HOLDS) { climbOutOfPit() }
        onOpLoc1(ROCKPILE) { climbToWell() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.climbRockslide(rock: BoundLocInfo) {
        arriveDelay()
        val dest = acrossFrom(rock)
        mes("You climb onto the rock...")
        faceSquare(rock.coords)
        if (!statRandom("stat.agility", ROCKSLIDE_LOW, ROCKSLIDE_HIGH, 0)) {
            delay(1)
            anim(SEQ_STUMBLE_BACK)
            mes("...but you slip back down.")
            takeInstantHit(HitType.Typeless, ROCKSLIDE_DAMAGE)
            return
        }
        mes("...and step down the other side.")
        climbOver(dest, SEQ_CLIMB_DOWN, ticks = 2)
    }

    /**
     * The pipe on the south side of the first cavern runs north through a grate that only opens
     * from this end, and comes out six tiles on.
     */
    private suspend fun ProtectedAccess.crawlThroughFirstPipe() {
        arriveDelay()
        mes("You open the grating.")
        soundSynth(SOUND_SQUEEZE)
        climbOver(coords.translate(0, PIPE_STRETCH), SEQ_PIPE_SQUEEZE, PIPE_TICKS)
        mes("You crawl through the pipe.")
        soundSynth(SOUND_SQUEEZE)
        climbOver(coords.translate(0, PIPE_STRETCH), SEQ_PIPE_SQUEEZE, PIPE_TICKS)
        mes("You hear the grating slam shut behind you.")
        soundSynth(SOUND_GRATE_CLOSE)
        statAdvance("stat.agility", PIPE_XP)
    }

    /**
     * The other pipes join up end to end, so the crawl carries on past any pipe it comes out
     * against until it reaches open floor.
     */
    private suspend fun ProtectedAccess.crawlThroughPipe(pipe: BoundLocInfo) {
        arriveDelay()
        val first = acrossFrom(pipe)
        val dx = Integer.signum(first.x - coords.x)
        val dz = Integer.signum(first.z - coords.z)
        var dest = first
        var steps = 0
        while (!collision.walkable(dest) && steps < MAX_PIPE_RUN) {
            dest = dest.translate(dx, dz)
            steps++
        }
        if (!collision.walkable(dest)) {
            dest = collision.nearestFree(first, LANDING_RADIUS) ?: return refuse()
        }
        soundSynth(SOUND_SQUEEZE)
        climbOver(dest, SEQ_PIPE_SQUEEZE, PIPE_TICKS + steps / 2)
        mes("You crawl through the pipe.")
        statAdvance("stat.agility", PIPE_XP)
    }

    /**
     * The tunnels out of the unicorn's cave come up in the cavern north of it. The ones going in
     * choose between the two copies of the cave: the unicorn caged, or the cage smashed.
     */
    private suspend fun ProtectedAccess.passThroughTunnel(tunnel: BoundLocInfo) {
        arriveDelay()
        val dest =
            when {
                tunnel.angle == LocAngle.South -> UpassCoords.UNICORN_TUNNEL_NORTH
                quest.stage(player) >= STAGE_UNICORN -> UpassCoords.UNICORN_CAVE_DEAD
                else -> UpassCoords.UNICORN_CAVE_ALIVE
            }
        telejump(dest)
    }

    /**
     * The cell gates and the maze gates. The cell gates take no skill to pick, only luck; the
     * maze gates want a Thieving level of 50 as well.
     */
    private suspend fun ProtectedAccess.pickGate(gate: BoundLocInfo, requiredLevel: Int) {
        arriveDelay()
        if (requiredLevel > 0 && stat("stat.thieving") < requiredLevel) {
            mesbox("You need a Thieving level of $requiredLevel to pick this lock.")
            return
        }
        val dest = collision.nearestFree(acrossFrom(gate), LANDING_RADIUS) ?: return refuse()
        mes("You attempt to pick the lock...")
        anim(SEQ_PICKLOCK)
        delay(1)
        if (random.of(PICK_ROLL) < PICK_FAIL_BELOW) {
            mes("You fail to pick the lock.")
            return
        }
        soundSynth(SOUND_LOCKED_DOOR)
        mes("You manage to pick the lock.")
        delay(if (requiredLevel > 0) 1 else 2)
        statAdvance("stat.thieving", PICK_XP)
        mes("You walk through.")
        stepThrough(listOf(dest))
        mes("The cage slams shut behind you.")
        soundSynth(SOUND_GRATE_CLOSE)
    }

    private suspend fun ProtectedAccess.searchBars() {
        arriveDelay()
        mes("You search the bars...")
        delay(1)
        mes("...The cage has been locked.")
    }

    /**
     * The ledge is edged along sideways, x 2374, from one end to the other. The player can slip
     * off it into the rat pit to the west, which is a fall of a few feet and no worse.
     */
    private suspend fun ProtectedAccess.crossLedge(ledge: BoundLocInfo) {
        arriveDelay()
        if (coords.x < ledge.coords.x) {
            mes("You can't do that from here.")
            return
        }
        mes("You put your foot on the ledge and try to edge across...")
        val fromSouth = coords.z < ledge.coords.z
        val x = UpassCoords.LEDGE_X
        val level = ledge.coords.level
        val (into, walk, ready, out) =
            if (fromSouth) {
                listOf(SIDESTEP_INTO_LEFT, SIDESTEP_WALK_LEFT, SIDESTEP_READY_LEFT, SIDESTEP_OUT_LEFT)
            } else {
                listOf(SIDESTEP_INTO, SIDESTEP_WALK, SIDESTEP_READY, SIDESTEP_OUT)
            }
        val firstHalf =
            if (fromSouth) {
                listOf(9639, 9640, 9641, 9642).map { CoordGrid(x, it, level) }
            } else {
                listOf(9643, 9642).map { CoordGrid(x, it, level) }
            }
        val secondHalf =
            if (fromSouth) {
                listOf(CoordGrid(x, 9643, level), CoordGrid(x + 1, 9644, level))
            } else {
                listOf(9641, 9640, 9639, 9638).map { CoordGrid(x, it, level) }
            }
        anim(into)
        soundSynth(SOUND_LEDGE)
        setWalkStyle(walk, ready)
        var landed = false
        try {
            stepThrough(firstHalf)
            if (!statRandom("stat.agility", LEDGE_LOW, LEDGE_HIGH, 0)) {
                clearWalkStyle()
                mes("You fall in to the rat pit.")
                climbOver(coords.translate(-1, 0), SEQ_BALANCE_STUMBLE, ticks = 1)
                landed = true
                takeInstantHit(HitType.Typeless, LEDGE_DAMAGE)
                return
            }
            stepThrough(secondHalf)
            landed = true
            anim(out)
        } finally {
            clearWalkStyle()
            if (!landed) {
                telejump(secondHalf.last(), TeleportType.Exempt)
            }
        }
    }

    /** The stone bridges of the maze are one tile of walkway each; a slip drops off the side. */
    private suspend fun ProtectedAccess.crossStoneBridge(bridge: BoundLocInfo) {
        arriveDelay()
        mes("You start to cross the rock bridge...")
        val dx = if (coords.x > bridge.coords.x) -1 else 1
        val end = CoordGrid(bridge.coords.x + dx, bridge.coords.z, coords.level)
        val middle = CoordGrid(bridge.coords.x, bridge.coords.z, coords.level)
        setWalkStyle(SEQ_BALANCE, SEQ_BALANCE_READY)
        try {
            stepThrough(listOf(middle))
            if (!statRandom("stat.agility", BRIDGE_LOW, BRIDGE_HIGH, 0)) {
                clearWalkStyle()
                val dz = if (CoordGrid(bridge.coords.x, bridge.coords.z, 0) in NORTH_FALLING_BRIDGES) 1 else -1
                mes("...and fall off it.")
                climbOver(coords.translate(0, dz), SEQ_BALANCE_STUMBLE, ticks = 2)
                delay(2)
                takeInstantHit(HitType.Typeless, BRIDGE_DAMAGE)
                return
            }
            mes("...and make it.")
            stepThrough(listOf(end))
        } finally {
            clearWalkStyle()
            if (coords == middle) {
                telejump(end, TeleportType.Exempt)
            }
        }
    }

    /**
     * A rope tied round the rock over the pit swings the player across to the east lip. Nobody
     * climbs back up for it afterwards, so it is lost either way.
     */
    private suspend fun ProtectedAccess.ropeSwing(rock: BoundLocInfo) {
        arriveDelay()
        stepThrough(lineTo(UpassCoords.SWING_START))
        faceSquare(UpassCoords.SWING_START.translate(1, 0))
        delay(1)
        mes("You tie the rope to the rock...")
        anim(SEQ_THROW_ROPE)
        if (invDel(inv, ROPE).failure) {
            return
        }
        locRepo.change(rock, ropedRockType, ROPED_ROCK_TICKS)
        delay(1)
        if (!statRandom("stat.agility", SWING_LOW, SWING_HIGH, 0)) {
            mes("You try to swing but fall in to the darkness.")
            telejump(UpassCoords.CREVASSE_FLOOR, TeleportType.Exempt)
            say("Aargh!")
            delay(1)
            takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * SWING_DAMAGE_PERCENT / 100 + 1)
            mes("You've lost your rope.")
            return
        }
        locRepo.findExact(UpassCoords.SWING_ROCK, ropedRockType)?.let {
            locAnim(worldRepo, it, SEQ_LOC_ROPESWING)
        }
        climbOver(UpassCoords.SWING_LANDING, SEQ_ROPESWING, ticks = 3, startDelay = SWING_START_CYCLES)
        mes("You skillfully swing across.")
        mes("The rope gets tangled in some stalagmites.")
        delay(1)
        mes("You've lost your rope.")
    }

    private suspend fun ProtectedAccess.swingBack() {
        arriveDelay()
        if (coords.x < UpassCoords.SWING_BACK_START.x - 1) {
            mes("You cannot do that from here.")
            return
        }
        stepThrough(lineTo(UpassCoords.SWING_BACK_START))
        faceSquare(UpassCoords.SWING_BACK_LANDING)
        delay(1)
        climbOver(UpassCoords.SWING_BACK_LANDING, SEQ_ROPESWING, ticks = 3, startDelay = SWING_START_CYCLES)
        mes("You skillfully swing across.")
    }

    private suspend fun ProtectedAccess.climbOutOfPit() {
        arriveDelay()
        anim(SEQ_LADDER)
        delay(1)
        mes("You crawl out of the pit.")
        telejump(UpassCoords.GRID_CLIMB_OUT, TeleportType.Exempt)
    }

    /** The rockpile at the back of the prison climbs back up into the shaft of the well. */
    private suspend fun ProtectedAccess.climbToWell() {
        arriveDelay()
        anim(SEQ_LADDER)
        mes("You climb up in to the pipe...")
        delay(1)
        telejump(UpassCoords.MUDPILE_TOP)
        mes("...and out of the well.")
    }

    private fun ProtectedAccess.refuse() {
        mes("You can't reach the other side from here.")
    }

    private companion object {
        const val ROCKSLIDE = "loc.rockslide2_obstacle_upass"
        const val SOUTH_PIPE = "loc.upass_pipe4"
        const val SOUTH_PIPE_GRATE = "loc.upass_pipe5"
        val PIPES = arrayOf("loc.upass_pipe6", "loc.upass_pipe7", "loc.upass_pipe8")
        val UNICORN_TUNNELS = arrayOf("loc.upass_unicorn_doorl", "loc.upass_unicorn_doorr")

        const val CELL_GATE = "loc.cave_railings2"
        const val MAZE_GATE = "loc.cave_railings5"
        const val LEDGE = "loc.upass_ledge"
        const val STONE_BRIDGE = "loc.walkway_upass_narrow_mid_top"
        const val BARE_ROCK = "loc.obstical_rockswing_norope"
        const val ROPED_ROCK = "loc.obstical_rockswing_withrope"
        const val ROPE_SWING_BACK = "loc.obstical_rockswing_withrope2"
        const val HAND_HOLDS = "loc.upass_grilltrap_hand_holds"
        const val ROCKPILE = "loc.mudpile_upass"

        const val SEQ_BALANCE_READY = "seq.human_walk_logbalance_ready"
        const val SIDESTEP_INTO = "seq.human_into_sidestep"
        const val SIDESTEP_WALK = "seq.human_walk_sidestep"
        const val SIDESTEP_READY = "seq.human_ready_sidestep"
        const val SIDESTEP_OUT = "seq.human_outof_sidestep"
        const val SIDESTEP_INTO_LEFT = "seq.human_into_sidestepl"
        const val SIDESTEP_WALK_LEFT = "seq.human_walk_sidestepl"
        const val SIDESTEP_READY_LEFT = "seq.human_ready_sidestepl"
        const val SIDESTEP_OUT_LEFT = "seq.human_outof_sidestepl"

        /** The two stone bridges whose drop is on their north side rather than the south. */
        val NORTH_FALLING_BRIDGES = setOf(CoordGrid(2399, 9632, 0), CoordGrid(2406, 9632, 0))

        const val ROCKSLIDE_LOW = 95
        const val ROCKSLIDE_HIGH = 305
        const val ROCKSLIDE_DAMAGE = 3
        const val LEDGE_LOW = 90
        const val LEDGE_HIGH = 300
        const val LEDGE_DAMAGE = 5
        const val BRIDGE_LOW = 90
        const val BRIDGE_HIGH = 300
        const val BRIDGE_DAMAGE = 5
        const val SWING_LOW = 100
        const val SWING_HIGH = 410
        const val SWING_DAMAGE_PERCENT = 15
        const val SWING_START_CYCLES = 20

        const val PIPE_STRETCH = 3
        const val PIPE_TICKS = 3
        const val PIPE_XP = 3.0
        const val MAX_PIPE_RUN = 6
        const val ROPED_ROCK_TICKS = 5

        const val PICK_ROLL = 100
        const val PICK_FAIL_BELOW = 50
        const val PICK_XP = 3.0
        const val LANDING_RADIUS = 2
    }
}
