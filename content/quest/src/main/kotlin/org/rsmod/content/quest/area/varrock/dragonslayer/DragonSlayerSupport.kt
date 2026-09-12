package org.rsmod.content.quest.area.varrock.dragonslayer

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/* Helpers shared by the Dragon Slayer scripts. */

/** Which side of a wall-shaped loc a tile lies on, from the wall's angle. */
enum class WallSide {
    WEST,
    EAST,
    NORTH,
    SOUTH,
}

/**
 * The side of [loc] (a shape 0 wall or door on one edge of its tile) that [coords] is on. Angle 0
 * puts the wall on the west edge of the tile, 1 on the north, 2 on the east and 3 on the south.
 */
fun BoundLocInfo.sideOf(coords: CoordGrid): WallSide =
    when (angle.id) {
        0 -> if (coords.x >= this.coords.x) WallSide.EAST else WallSide.WEST
        1 -> if (coords.z > this.coords.z) WallSide.NORTH else WallSide.SOUTH
        2 -> if (coords.x > this.coords.x) WallSide.EAST else WallSide.WEST
        else -> if (coords.z >= this.coords.z) WallSide.NORTH else WallSide.SOUTH
    }

/** The tile just across [loc]'s wall from [from]. */
fun BoundLocInfo.tileAcross(from: CoordGrid): CoordGrid {
    val tile = coords
    return when (angle.id) {
        0 -> if (from.x >= tile.x) tile.translateX(-1) else tile
        1 -> if (from.z > tile.z) tile else tile.translateZ(1)
        2 -> if (from.x > tile.x) tile else tile.translateX(1)
        else -> if (from.z >= tile.z) tile else tile.translateZ(-1)
    }
}

/**
 * Doors that have no open form in the cache: the coloured and one-way doors of Melzar's Maze,
 * the Champions' Guild door and the secret wall on Crandor. Opening swaps the door for the same
 * loc turned a quarter onto the far tile (how a plain door leaf looks when open), walks the
 * player through, and puts the door back a few cycles later with a closing sound.
 *
 * Nothing here suspends: deleting the loc the player is interacting with ends their script, so
 * the closing step is scheduled on the world queue instead (as the Mage Arena levers do).
 */
@Singleton
class DoorPassage
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
) {
    /** Opens [door], walks the player to the tile across it, and closes it behind them. */
    fun walkThrough(access: ProtectedAccess, door: BoundLocInfo, openedLoc: String, sound: String = DOOR_OPEN_SOUND) {
        val player = access.player
        val dest = door.tileAcross(player.coords)
        player.soundSynth(sound)
        locRepo.del(door, CLOSE_TICKS)
        locRepo.add(dest, openedLoc, CLOSE_TICKS, door.turnAngle(1), door.shape)
        player.walk(dest)
        val uid = player.uid
        worldQueues.add(CLOSE_TICKS) {
            val walker = uid.resolve(playerList) ?: return@add
            walker.soundSynth(DOOR_CLOSE_SOUND)
        }
    }

    /**
     * Shows [openedLoc] in place of [door] for a moment and hops the player to [dest]; for doors
     * whose open form still blocks the doorway (the three-tile magic door).
     */
    fun hopThrough(access: ProtectedAccess, door: BoundLocInfo, openedLoc: String, dest: CoordGrid, sound: String) {
        val player = access.player
        player.soundSynth(sound)
        locRepo.del(door, HOP_OPEN_TICKS)
        locRepo.add(door.coords, openedLoc, HOP_OPEN_TICKS, door.angle, door.shape)
        val uid = player.uid
        worldQueues.add(1) {
            val walker = uid.resolve(playerList) ?: return@add
            PathingEntityCommon.telejump(walker, collision, dest)
        }
    }

    private companion object {
        const val DOOR_OPEN_SOUND = "synth.door_open"
        const val DOOR_CLOSE_SOUND = "synth.door_close"

        /** Long enough to walk the one or two tiles through the doorway. */
        const val CLOSE_TICKS = 3
        const val HOP_OPEN_TICKS = 4
    }
}

/**
 * Private copies of map squares for the two scenes that must not be shared with other players:
 * the voyage on the Lady Lumbridge and Elvarg's lair. World coordinates are used everywhere in
 * the scripts; [Visit.at] translates them into the copy, which keeps the world's local layout.
 */
@Singleton
class DragonSlayerInstances
@Inject
constructor(
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
) {
    class Visit(val session: InstanceSession, val enter: CoordGrid, worldEnter: CoordGrid) {
        private val dx = enter.x - worldEnter.x
        private val dz = enter.z - worldEnter.z

        /** The instance tile matching world tile [world]. */
        fun at(world: CoordGrid): CoordGrid = CoordGrid(world.x + dx, world.z + dz, enter.level)
    }

    /**
     * Copies the map square holding [worldEnter] and telejumps the player to its copy of that
     * tile. Returns null (after messaging the player) when no copy could be made.
     */
    fun ProtectedAccess.enterCopy(
        key: String,
        worldEnter: CoordGrid,
        exit: CoordGrid,
        spawns: List<InstanceNpc> = emptyList(),
        bossName: String = "",
    ): Visit? {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return null
        }
        val regionId = (worldEnter.x shr 6 shl 8) or (worldEnter.z shr 6)
        val area =
            InstanceArea.copyRegions(
                regionIds = listOf(regionId),
                level = worldEnter.level,
                enterCoord = RegionLocal(worldEnter.level, worldEnter.mx, worldEnter.mz, worldEnter.lx, worldEnter.lz),
                exitCoord = exit,
                npcSpawns = spawns,
            )
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = RECLAIM_TICKS,
                graceTicks = GRACE_TICKS,
                destroyWhenEmpty = true,
                area = area,
                settingsRowId = -1,
                bossName = bossName,
            )
        return when (val result = manager.create(player, key, spec, InstanceAccess.Private, mapClock)) {
            is InstanceManager.Result.Failed -> {
                mes(result.reason)
                null
            }
            is InstanceManager.Result.Created -> settle(result.session, result.enter, worldEnter)
            is InstanceManager.Result.Joined -> settle(result.session, result.enter, worldEnter)
        }
    }

    private fun ProtectedAccess.settle(session: InstanceSession, enter: CoordGrid, worldEnter: CoordGrid): Visit? {
        telejump(enter, TeleportType.Exempt)
        if (player.coords != enter) {
            mes("You can't go there right now.")
            manager.leave(player, session, mapClock)
            return null
        }
        manager.finalizeEntry(player, session, mapClock)
        return Visit(session, enter, worldEnter)
    }

    /** Removes the player from their copy; it is destroyed once empty. */
    fun ProtectedAccess.leaveCopy(): CoordGrid? {
        val session = manager.sessionForPlayer(player) ?: return null
        return manager.leave(player, session, mapClock)
    }

    fun ProtectedAccess.insideCopy(): Boolean = manager.sessionForPlayer(player) != null

    /** Spawns a scene npc at world tile [world] inside the visit and ties it to the instance. */
    fun spawn(visit: Visit, type: String, world: CoordGrid, face: Direction? = null): Npc {
        val npc = Npc(type, visit.at(world))
        npc.mode = NpcMode.None
        if (face != null) {
            npc.respawnDir = face
        }
        npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(visit.session.id, npc)
        if (face != null) {
            npc.lockFacingDirection(face)
        }
        return npc
    }

    fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** The npcs the instance spawned, plus anything attached since. */
    fun npcsIn(visit: Visit): List<Npc> = manager.npcsForInstance(visit.session.id)

    private companion object {
        const val RECLAIM_TICKS = 100
        const val GRACE_TICKS = 50
    }
}
