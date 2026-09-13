package org.rsmod.content.quest.manager

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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Private copies of map squares for quest scenes that must not be shared with other players:
 * the voyage on the Lady Lumbridge, Elvarg's lair, the Jungle Demon's cavern. World coordinates
 * are used everywhere in the scripts; [Visit.at] translates them into the copy, which keeps the
 * world's local layout.
 */
@Singleton
class QuestInstances
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
