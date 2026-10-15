package org.rsmod.content.quest.area.feldip.bigchompy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BAIT_ZONE_NORTH_EAST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BAIT_ZONE_SOUTH_WEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BLOATED_TOAD_NPC
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_DEAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SWAMP_TOAD
import org.rsmod.content.quest.area.wilderness.magearena.freeFootprint
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The bait and the birds: which player a placed toad and the chompy that came down for it belong
 * to, and where everything is allowed to happen.
 *
 * Spawn ownership is what keeps two hunters apart. A placed bloated toad and the chompy it lures
 * are both stamped with the player who baited them, so only that player's arrows count, only their
 * quest stage advances, and only they see the hint arrow.
 */
@Singleton
class ChompyHunt
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
) {
    private val bloatedToadId = BLOATED_TOAD_NPC.asRSCM(RSCMType.NPC)
    private val chompyId = CHOMPY.asRSCM(RSCMType.NPC)
    private val deadChompyId = CHOMPY_DEAD.asRSCM(RSCMType.NPC)

    fun isBloatedToad(npc: Npc): Boolean = npc.id == bloatedToadId

    fun isChompy(npc: Npc): Boolean = npc.id == chompyId

    /** Rantz's own patch of hills, the only place he will shoot a baited chompy during the quest. */
    fun inRantzClearing(coords: CoordGrid): Boolean =
        coords.level == BAIT_ZONE_SOUTH_WEST.level &&
            coords.x in BAIT_ZONE_SOUTH_WEST.x..BAIT_ZONE_NORTH_EAST.x &&
            coords.z in BAIT_ZONE_SOUTH_WEST.z..BAIT_ZONE_NORTH_EAST.z

    fun placeBait(player: Player, coords: CoordGrid): Npc? {
        val toad = spawn(player, BLOATED_TOAD_NPC, coords, exact = true, lifetime = BAIT_LIFETIME)
        toad?.facePlayer(player)
        return toad
    }

    fun releaseSwampToad(player: Player, near: CoordGrid): Npc? =
        spawn(player, SWAMP_TOAD, near, exact = false, lifetime = RELEASED_TOAD_LIFETIME)

    fun landChompy(player: Player, near: CoordGrid): Npc? =
        spawn(player, CHOMPY, near, exact = false, lifetime = CHOMPY_LIFETIME)

    fun leaveCarcass(player: Player, coords: CoordGrid): Npc? =
        spawn(player, CHOMPY_DEAD, coords, exact = true, lifetime = CARCASS_LIFETIME)

    fun ownsKill(npc: Npc, player: Player): Boolean = npc.isSpawnOwnedBy(player)

    fun ownerOf(npc: Npc): Player? = resolve(npc.spawnOwner)

    fun resolve(uid: PlayerUid): Player? = uid.resolve(playerList)

    /** The chompy bird nearest [coords], for Rantz's own hunting. */
    fun chompyNear(coords: CoordGrid, radius: Int): Npc? =
        npcRepo
            .findAll(ZoneKey.from(coords), zoneRadius = ZONE_SEARCH_RADIUS)
            .filter { it.id == chompyId && it.isSlotAssigned }
            .filter { it.coords.chebyshevDistance(coords) <= radius }
            .minByOrNull { it.coords.chebyshevDistance(coords) }

    /**
     * Runs [block] against [player] as soon as nothing else has hold of them, for the stage changes
     * that come out of an npc queue or a death hook rather than an interaction.
     */
    fun withAccess(player: Player, block: suspend ProtectedAccess.() -> Unit) {
        withAccess(player.uid, LAUNCH_ATTEMPTS, block)
    }

    private fun withAccess(
        uid: PlayerUid,
        attempts: Int,
        block: suspend ProtectedAccess.() -> Unit,
    ) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (!launcher.launch(player, block = block) && attempts > 0) {
                withAccess(uid, attempts - 1, block)
            }
        }
    }

    fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** The bloated toad a chompy came down for, while it is still on the ground. */
    fun baitNear(coords: CoordGrid, radius: Int): Npc? =
        npcRepo
            .findAll(ZoneKey.from(coords), zoneRadius = 1)
            .firstOrNull {
                it.id == bloatedToadId &&
                    it.isSlotAssigned &&
                    it.coords.chebyshevDistance(coords) <= radius
            }

    fun carcassAt(coords: CoordGrid): Npc? =
        npcRepo.findAll(coords).firstOrNull { it.id == deadChompyId }

    private fun spawn(
        player: Player,
        type: String,
        near: CoordGrid,
        exact: Boolean,
        lifetime: Int,
    ): Npc? {
        val serverType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val tile =
            if (exact) {
                near
            } else {
                collision.freeFootprint(near, serverType.size, radius = SPAWN_RADIUS) ?: near
            }
        val npc = Npc(serverType, tile)
        npc.respawns = false
        npcRepo.add(npc, lifetime)
        npc.assignSpawnOwner(player, clock.cycle)
        return npc
    }

    companion object {
        /** How long a placed toad sits there before it hops away on its own. */
        const val BAIT_LIFETIME = 101

        const val RELEASED_TOAD_LIFETIME = 10
        const val CHOMPY_LIFETIME = 200
        const val CARCASS_LIFETIME = 100

        const val SPAWN_RADIUS = 4

        const val LAUNCH_ATTEMPTS = 20

        /** Zones to sweep when looking for a nearby toad or bird; one zone is eight tiles. */
        const val ZONE_SEARCH_RADIUS = 2

        /** Cycles between a placed toad's rolls for a chompy bird. */
        const val BAIT_ROLL_CYCLES = 25

        /** One in this many rolls brings a chompy down. */
        const val BAIT_ROLL_CHANCE = 6

        /** Rolls after which a toad that has attracted nothing bursts. */
        const val BURST_ROLL = 3
        const val BURST_ROLL_LATE = 13

        /** The roll count is offset by this once a chompy has been drawn to the toad. */
        const val CHOMPY_DRAWN = 10
    }
}
