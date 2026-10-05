package org.rsmod.content.skills.construction.house

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.skills.construction.data.Treasure
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The guard npcs standing in each open house's dungeon.
 *
 * A guard is built as a statue of itself; outside building mode the statue is taken away and the
 * guard spawned on its spot. Guards stand and only fight back, until the owner pulls a throne room
 * lever into challenge mode: then they go for any guest who comes near. They give no experience, as
 * the wiki says, and come back after dying while the house stands.
 */
@Singleton
class HouseGuards
@Inject
constructor(private val npcRepo: NpcRepository, private val interactions: AiPlayerInteractions) {
    private val spawned = HashMap<Long, MutableList<Npc>>()

    private val guardianTypes by lazy { Treasure.GUARDIANS.mapTo(HashSet()) { "npc.$it".asRSCM(RSCMType.NPC) } }

    fun spawn(owner: Player, guards: Map<CoordGrid, String>) {
        despawn(owner)
        val npcs = ArrayList<Npc>()
        for ((coords, type) in guards) {
            val npc = Npc(type, coords)
            npc.mode = NpcMode.None
            npc.respawns = true
            npcRepo.add(npc, Int.MAX_VALUE)
            npc.combatXpMultiplier = 0
            npcs += npc
        }
        if (npcs.isNotEmpty()) {
            spawned[owner.key()] = npcs
        }
    }

    fun despawn(owner: Player) {
        val npcs = spawned.remove(owner.key()) ?: return
        for (npc in npcs) {
            npc.respawns = false
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** Sends every guard not already fighting, within [GUARD_RANGE] of [guest] after them. */
    fun engage(owner: Player, guest: Player) {
        for (npc in spawned[owner.key()].orEmpty()) {
            if (npc.mode == NpcMode.OpPlayer2 || !npc.isValidTarget()) {
                continue
            }
            if (npc.coords.level != guest.coords.level || npc.coords.chebyshevDistance(guest.coords) > GUARD_RANGE) {
                continue
            }
            npc.opPlayer2(guest, interactions)
        }
    }

    /** True while any of [owner]'s treasure room guardians is alive. */
    fun treasureGuarded(owner: Player): Boolean =
        spawned[owner.key()].orEmpty().any { it.type.id in guardianTypes && it.isAliveInWorld() }

    /** Calls every guard off whoever it is fighting, as challenge mode is switched off. */
    fun standDown(owner: Player) {
        for (npc in spawned[owner.key()].orEmpty()) {
            npc.clearInteraction()
            npc.noneMode()
        }
    }

    private fun Player.key(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private companion object {
        const val GUARD_RANGE = 4
    }
}
