package org.rsmod.content.other.barrows

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.wilderness.magearena.walkable
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The brothers and tunnel monsters raised for each player. Barrows is not instanced, so every
 * summon belongs to the player who raised it (see [BarrowsHooks]) and is sent away when that
 * player leaves the crypts and tunnels.
 */
@Singleton
class BarrowsSpawns
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
    private val aiInteractions: AiPlayerInteractions,
    private val random: GameRandom,
) {
    private val steps = StepValidator(collision)
    private val brothers = HashMap<PlayerUid, Npc>()
    private val monsters = HashMap<PlayerUid, MutableList<Npc>>()

    val brotherTypes: Map<Int, Brother> by lazy {
        Brother.entries.associateBy { it.npc.asRSCM(RSCMType.NPC) }
    }

    private val monsterTypes: Set<Int> by lazy {
        TUNNEL_MONSTERS.mapTo(mutableSetOf()) { it.asRSCM(RSCMType.NPC) }
    }

    fun isBarrowsNpc(npc: Npc): Boolean = npc.id in brotherTypes || npc.id in monsterTypes

    fun activeBrother(player: Player): Brother? {
        val npc = brothers[player.uid] ?: return null
        if (!npc.isAliveInWorld()) {
            brothers.remove(player.uid)
            return null
        }
        return brotherTypes[npc.id]
    }

    fun summonBrother(player: Player, brother: Brother, shout: String?): Npc? {
        dismissBrother(player)
        val npc = spawn(player, typeOf(brother.npc), BROTHER_LIFETIME) ?: return null
        brothers[player.uid] = npc
        shout?.let(npc::say)
        return npc
    }

    fun summonMonster(player: Player): Npc? {
        val type = TUNNEL_SPAWN_TABLE.pick(random) ?: return null
        val npc = spawn(player, typeOf(type), MONSTER_LIFETIME) ?: return null
        monsters.getOrPut(player.uid) { mutableListOf() } += npc
        return npc
    }

    /** Whether the room around [coords] is already crowded enough that a door stays quiet. */
    fun isCrowded(coords: CoordGrid): Boolean {
        var count = 0
        for (npc in npcList) {
            if (npc.coords.level == coords.level &&
                npc.coords.chebyshevDistance(coords) <= ROOM_RADIUS &&
                npc.isAliveInWorld() &&
                isBarrowsNpc(npc)
            ) {
                count++
            }
        }
        return count >= CROWDED_ROOM
    }

    fun dismissBrother(player: Player) {
        val npc = brothers.remove(player.uid) ?: return
        delete(npc)
    }

    fun dismissAll(player: Player) {
        dismissBrother(player)
        monsters.remove(player.uid)?.forEach(::delete)
    }

    fun forget(npc: Npc) {
        brothers.values.removeIf { it === npc }
        monsters.values.forEach { list -> list.removeIf { it === npc } }
    }

    private fun spawn(player: Player, type: NpcServerType, lifetime: Int): Npc? {
        val tile = spawnTile(player.coords, type.size) ?: return null
        val npc = Npc(type, tile)
        npcRepo.add(npc, lifetime)
        npc.respawns = false
        npc.assignSpawnOwner(player, clock.cycle)
        npc.facePlayer(player)
        npc.opPlayer2(player, aiInteractions)
        return npc
    }

    private fun delete(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /**
     * A free tile within [SPAWN_RADIUS] steps of [near] that can be walked to from it, so a summon
     * never appears on the far side of the door the player just came through.
     */
    private fun spawnTile(near: CoordGrid, size: Int): CoordGrid? {
        val candidates = reachableTiles(near).filter { it != near }.toMutableList()
        for (i in candidates.lastIndex downTo 1) {
            val j = random.of(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        return candidates
            .sortedBy { it.chebyshevDistance(near) }
            .firstOrNull { sw ->
                (0 until size).all { dx ->
                    (0 until size).all { dz -> collision.walkable(sw.translate(dx, dz)) }
                } && !(near.x in sw.x until sw.x + size && near.z in sw.z until sw.z + size)
            }
    }

    private fun reachableTiles(origin: CoordGrid): Set<CoordGrid> {
        val seen = mutableSetOf(origin)
        var frontier = listOf(origin)
        repeat(SPAWN_RADIUS) {
            val next = mutableListOf<CoordGrid>()
            for (tile in frontier) {
                for ((dx, dz) in STEPS) {
                    val step = tile.translate(dx, dz)
                    if (step !in seen && steps.canTravel(tile.level, tile.x, tile.z, dx, dz)) {
                        seen += step
                        next += step
                    }
                }
            }
            frontier = next
        }
        return seen
    }

    private fun typeOf(npc: String): NpcServerType =
        ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $npc")

    private class SpawnTable(val entries: List<Pair<String, Int>>) {
        fun pick(random: GameRandom): String? {
            val total = entries.sumOf { it.second }
            var roll = random.of(total)
            for ((npc, weight) in entries) {
                if (roll < weight) {
                    return npc
                }
                roll -= weight
            }
            return null
        }
    }

    companion object {
        const val BROTHER_LIFETIME = 1000
        const val MONSTER_LIFETIME = 500
        const val SPAWN_RADIUS = 2
        const val ROOM_RADIUS = 7
        const val CROWDED_ROOM = 11
        private val STEPS = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)

        /** A door summons a brother 12 times in 128; otherwise one of these, by weight. */
        const val BROTHER_DOOR_CHANCE = 12
        const val DOOR_CHANCE_OUT_OF = 128

        val TUNNEL_MONSTERS =
            listOf(
                "npc.barrows_bloodworm",
                "npc.barrows_rat",
                "npc.barrows_giantrat",
                "npc.barrows_giantrat2",
                "npc.barrows_giantrat3",
                "npc.barrows_spider",
                "npc.barrows_giantspider",
                "npc.barrows_skeleton_unarmed",
                "npc.barrows_skeleton_unarmed2",
                "npc.barrows_skeleton_armed",
                "npc.barrows_skeleton_armed2",
            )

        private val TUNNEL_SPAWN_TABLE =
            SpawnTable(
                listOf(
                    "npc.barrows_skeleton_unarmed" to 13,
                    "npc.barrows_skeleton_unarmed2" to 13,
                    "npc.barrows_skeleton_armed" to 13,
                    "npc.barrows_skeleton_armed2" to 13,
                    "npc.barrows_bloodworm" to 32,
                    "npc.barrows_rat" to 32,
                )
            )
    }
}
