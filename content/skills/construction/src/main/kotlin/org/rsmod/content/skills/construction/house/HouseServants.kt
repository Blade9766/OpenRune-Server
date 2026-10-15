package org.rsmod.content.skills.construction.house

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Servant
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The servant npc standing in each open house.
 *
 * Which servant a player has hired is `varbit.poh_servant_type`; the npc itself only exists while
 * its owner's house stands, and only if the house has the two bedrooms with beds a servant needs.
 * While it is away on an errand the npc is taken out, and it comes back beside its owner. Told to
 * greet guests, it waits at the entrance until it is next called or sent out.
 */
@Singleton
class HouseServants
@Inject
constructor(private val npcRepo: NpcRepository, private val collision: CollisionFlagMap) {
    private class Hired(
        val owner: Player,
        val servant: Servant,
        var npc: Npc,
        var away: Boolean = false,
        var greeting: Boolean = false,
    )

    private val hired = HashMap<Long, Hired>()

    /** Owners whose servant is out on an errand, kept even while their house is down. */
    private val out = HashSet<Long>()

    fun servantOf(player: Player): Servant? = Servant.of(player.vars[TYPE_VARBIT])

    /** A servant takes the second-best bedroom, so a house needs two with beds for one to live in. */
    fun hasQuarters(state: HouseState): Boolean =
        state.rooms.values.count { it.type == RoomType.BEDROOM && BED in it.furniture } >= 2

    /**
     * Stands the servant in a newly opened house. One still out on an errand stays out - its trip
     * is still queued, and it comes back when that ends - so rebuilding the house can't hurry it.
     */
    fun spawn(owner: Player, state: HouseState, at: CoordGrid) {
        val away = owner.key() in out
        despawn(owner)
        val servant = servantOf(owner) ?: return
        if (!hasQuarters(state)) {
            return
        }
        hired[owner.key()] =
            if (away) Hired(owner, servant, Npc(servant.npc, at), away = true) else Hired(owner, servant, add(servant, at))
    }

    fun despawn(owner: Player) {
        val entry = hired.remove(owner.key()) ?: return
        if (!entry.away) {
            npcRepo.del(entry.npc, Int.MAX_VALUE)
        }
    }

    /** The owner's servant npc, while it is at home rather than out on an errand. */
    fun npcOf(owner: Player): Npc? = hired[owner.key()]?.takeUnless { it.away }?.npc

    fun ownerOf(npc: Npc): Player? = hired.values.firstOrNull { it.npc === npc }?.owner

    fun isAway(owner: Player): Boolean = hired[owner.key()]?.away == true

    /** Forgets an errand that will never come back - its queue went with a logout. */
    fun forget(owner: Player) {
        out -= owner.key()
    }

    /** The owner's servant, while it is waiting at the entrance to greet guests. */
    fun greeterOf(owner: Player): Npc? = hired[owner.key()]?.takeIf { it.greeting && !it.away }?.npc

    /** Sends the owner's servant to wait at [entrance] and greet whoever comes in. */
    fun stationAt(owner: Player, entrance: CoordGrid): Npc? {
        val npc = bring(owner, entrance) ?: return null
        hired[owner.key()]?.greeting = true
        return npc
    }

    /** Moves the owner's servant beside [at] - back from an errand, or answering the bell. */
    fun bring(owner: Player, at: CoordGrid): Npc? {
        out -= owner.key()
        val entry = hired[owner.key()] ?: return null
        entry.greeting = false
        if (entry.away) {
            entry.npc = add(entry.servant, at)
            entry.away = false
        } else {
            entry.npc.telejump(collision, at)
        }
        return entry.npc
    }

    fun sendAway(owner: Player) {
        out += owner.key()
        val entry = hired[owner.key()] ?: return
        entry.greeting = false
        if (!entry.away) {
            npcRepo.del(entry.npc, Int.MAX_VALUE)
            entry.away = true
        }
    }

    private fun add(servant: Servant, at: CoordGrid): Npc {
        val npc = Npc(servant.npc, at)
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        return npc
    }

    private fun Player.key(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private companion object {
        const val TYPE_VARBIT = "varbit.poh_servant_type"
        const val BED = "bed"
    }
}
