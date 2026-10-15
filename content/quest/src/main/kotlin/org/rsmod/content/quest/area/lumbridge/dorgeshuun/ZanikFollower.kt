package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.CELLAR_STAGES
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_HAM_HIDEOUT
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_STOREROOMS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.UNDERGROUND_OFFSET
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER_HAM
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_SHOWDOWN
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Zanik out with the player: the npc that follows them around, pet-style, while she is being shown
 * Lumbridge, while the two of them sneak about the H.A.M. hideout and on the way to the water mill.
 *
 * She keeps up with the player over stairs, ladders and trapdoors, but a real teleport (anything
 * that moves the player further than [NEAR_TRAVEL] tiles in one go) sends her home to the castle
 * cellar, as does logging out. Other parts of the quest hook their per-tick checks in with
 * [onTick]; they run while she is following and the player is free to be talked to.
 */
@Singleton
class ZanikFollower
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val listeners = mutableListOf<(Player, Npc) -> Unit>()
    private val steps by lazy { StepValidator(collision) }
    private val zanikTypes by lazy {
        listOf(ZANIK_FOLLOWER, ZANIK_FOLLOWER_HAM, ZANIK_SHOWDOWN).map { it.asRSCM(RSCMType.NPC) }.toSet()
    }

    override fun ScriptContext.startup() {
        onPlayerSoftTimer(TICK_TIMER) { tick(player) }
        onPlayerLogin {
            clearSavedFollower(player)
            returnToCellarIfDue(player)
        }
        onPlayerLogout {
            remove(player)
            returnToCellarIfDue(player)
        }
    }

    fun onTick(listener: (Player, Npc) -> Unit) {
        listeners += listener
    }
    fun following(player: Player): Npc? {
        val npc = player.attr[ZANIK_NPC] ?: return null
        return if (npc.isSlotAssigned) npc else null
    }

    fun isFollowing(player: Player): Boolean = following(player) != null

    fun isWaiting(player: Player): Boolean = isFollowing(player) && player.attr[WAITING] == true

    /** Whether the player has some other follower (a pet) out, which Zanik won't share them with. */
    fun hasOtherFollower(player: Player): Boolean {
        val packed = player.vars[FOLLOWER_VARP]
        if (packed == NO_FOLLOWER || packed == 0) {
            return false
        }
        return following(player) == null
    }

    fun typeFor(stage: Int): String =
        when {
            stage in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS -> ZANIK_FOLLOWER_HAM
            stage >= STAGE_MILL -> ZANIK_SHOWDOWN
            else -> ZANIK_FOLLOWER
        }

    /** Brings Zanik out beside the player (or on [at]) and has her follow them. */
    fun spawn(player: Player, type: String = typeFor(dttd.stage(player)), at: CoordGrid? = null): Npc {
        remove(player)
        val npc = Npc(type, at ?: spawnTile(player.coords))
        npc.mode = NpcMode.PlayerFollow
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        npc.facePlayer(player)
        player.attr[ZANIK_NPC] = npc
        player.attr[LAST_COORDS] = player.coords
        player.attr[RESYNC] = true
        player.attr.remove(WAITING)
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, (npc.visType.id shl 16) or (npc.slotId and 0xFFFF))
        if (player.dttdZanikInCellar) {
            player.dttdZanikInCellar = false
        }
        player.softTimer(TICK_TIMER, 1)
        return npc
    }

    /** Takes Zanik away without saying where she has gone; the caller decides that. */
    fun remove(player: Player) {
        val npc = player.attr[ZANIK_NPC]
        player.attr.remove(ZANIK_NPC)
        player.attr.remove(LAST_COORDS)
        player.attr.remove(ZANIK_LAST_COORDS)
        player.attr.remove(STUCK_TICKS)
        player.attr.remove(WAITING)
        player.clearSoftTimer(TICK_TIMER)
        if (npc != null) {
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
            VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, NO_FOLLOWER)
        }
    }

    /** Zanik goes back to the castle cellar to wait for the player. */
    fun sendHome(player: Player) {
        remove(player)
        returnToCellarIfDue(player)
    }

    /** Zanik never survives a logout, so a follower var still naming her would read as a pet. */
    private fun clearSavedFollower(player: Player) {
        val packed = player.vars[FOLLOWER_VARP]
        if (packed != NO_FOLLOWER && packed ushr 16 in zanikTypes) {
            VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, NO_FOLLOWER)
        }
    }

    fun returnToCellarIfDue(player: Player) {
        if (dttd.stage(player) in CELLAR_STAGES && !isFollowing(player)) {
            player.dttdZanikInCellar = true
        }
    }

    fun waitHere(player: Player) {
        val npc = following(player) ?: return
        player.attr[WAITING] = true
        npc.mode = NpcMode.None
        npc.resetFaceEntity()
    }

    fun followAgain(player: Player) {
        val npc = following(player) ?: return
        player.attr.remove(WAITING)
        player.attr[LAST_COORDS] = player.coords
        npc.facePlayer(player)
        npc.mode = NpcMode.PlayerFollow
    }

    /** Puts Zanik beside [dest], for when the player is moved somewhere she comes along to. */
    fun relocate(player: Player, dest: CoordGrid = player.coords) {
        val npc = following(player) ?: return
        npc.telejump(collision, spawnTile(dest))
        player.attr[LAST_COORDS] = dest
        if (!isWaiting(player)) {
            npc.facePlayer(player)
            npc.mode = NpcMode.PlayerFollow
        }
    }

    private fun tick(player: Player) {
        val npc = following(player)
        if (npc == null) {
            remove(player)
            returnToCellarIfDue(player)
            return
        }
        if (player.attr[RESYNC] == true) {
            player.attr.remove(RESYNC)
            player.resyncVar(FOLLOWER_VARP)
        }
        val last = player.attr[LAST_COORDS] ?: player.coords
        player.attr[LAST_COORDS] = player.coords
        if (travelled(last, player.coords) > NEAR_TRAVEL) {
            sendHome(player)
            return
        }
        val waiting = isWaiting(player)
        val lost = !waiting && !npc.coords.isWithinDistance(player.coords, LOST_DISTANCE)
        if (npc.coords.level != player.coords.level || lost) {
            relocate(player)
        }
        if (!waiting) {
            catchUpIfStuck(player, npc)
            if (!npc.isFacingPlayer) {
                npc.facePlayer(player)
            }
            if (npc.mode != NpcMode.PlayerFollow) {
                npc.mode = NpcMode.PlayerFollow
            }
        }
        player.softTimer(TICK_TIMER, 1)
        for (listener in listeners) {
            if (following(player) !== npc) {
                return
            }
            listener(player, npc)
        }
    }

    /**
     * Npc following walks a naive route, so a wall between Zanik and the player (a doorway she has
     * to step round) holds her in place. Like a pet, she hops to the player once she has stood
     * still out of reach for a few ticks.
     */
    private fun catchUpIfStuck(player: Player, npc: Npc) {
        val previous = player.attr[ZANIK_LAST_COORDS]
        player.attr[ZANIK_LAST_COORDS] = npc.coords
        if (npc.coords != previous || npc.coords.isWithinDistance(player.coords, CLOSE_ENOUGH)) {
            player.attr.remove(STUCK_TICKS)
            return
        }
        val stuck = (player.attr[STUCK_TICKS] ?: 0) + 1
        if (stuck < STUCK_LIMIT) {
            player.attr[STUCK_TICKS] = stuck
            return
        }
        player.attr.remove(STUCK_TICKS)
        relocate(player)
    }

    /** Tiles moved in one go, treating the underground copy of a map as lying beneath it. */
    private fun travelled(from: CoordGrid, to: CoordGrid): Int {
        val fromZ = if (from.z >= UNDERGROUND_OFFSET && to.z < UNDERGROUND_OFFSET) from.z - UNDERGROUND_OFFSET else from.z
        val toZ = if (to.z >= UNDERGROUND_OFFSET && from.z < UNDERGROUND_OFFSET) to.z - UNDERGROUND_OFFSET else to.z
        return maxOf(abs(from.x - to.x), abs(fromZ - toZ))
    }

    private fun spawnTile(origin: CoordGrid): CoordGrid {
        for (direction in SPAWN_DIRECTIONS) {
            val tile = origin.translate(direction.xOff, direction.zOff)
            if (
                collision.isZoneValid(tile) &&
                    !collision.isWalkBlocked(tile) &&
                    steps.canTravel(origin.level, origin.x, origin.z, direction.xOff, direction.zOff)
            ) {
                return tile
            }
        }
        return origin
    }

    companion object {
        const val TICK_TIMER = "timer.dttd_zanik"
        private const val FOLLOWER_VARP = "varp.follower_npc"
        private const val NO_FOLLOWER = -1
        private const val LOST_DISTANCE = 15
        private const val NEAR_TRAVEL = 96
        private const val CLOSE_ENOUGH = 2
        private const val STUCK_LIMIT = 3

        private val ZANIK_NPC = AttributeKey<Npc>()
        private val LAST_COORDS = AttributeKey<CoordGrid>()
        private val RESYNC = AttributeKey<Boolean>()
        private val ZANIK_LAST_COORDS = AttributeKey<CoordGrid>()
        private val STUCK_TICKS = AttributeKey<Int>()
        private val WAITING = AttributeKey<Boolean>()

        private val SPAWN_DIRECTIONS =
            listOf(
                Direction.South,
                Direction.West,
                Direction.East,
                Direction.North,
                Direction.SouthWest,
                Direction.SouthEast,
                Direction.NorthWest,
                Direction.NorthEast,
            )
    }
}
