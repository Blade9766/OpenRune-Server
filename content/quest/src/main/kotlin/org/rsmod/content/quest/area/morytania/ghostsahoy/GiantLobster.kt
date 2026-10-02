package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GIANT_LOBSTER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_2
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chest in the wreck's hold and the level-32 giant lobster living in it.
 *
 * Searching the chest while the second map scrap is still needed brings the lobster out to
 * fight the searcher. Each player has at most one lobster, which belongs to them: only its owner
 * is credited when it dies, whoever else joined in. It retreats into the chest if its owner
 * leaves the hold, dies, logs out or takes longer than [LIFETIME] cycles, and the chest can then
 * be searched again for a fresh one. Once the owner's lobster is dead (`varbit.ahoy_killed_lobster`)
 * the chest gives up the scrap to them, and never sets a lobster on them again.
 */
@Singleton
class GiantLobster
@Inject
constructor(
    private val ahoy: GhostsAhoyQuest,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val mapClock: MapClock,
) : PluginScript() {

    private data class Summon(val owner: PlayerUid, val spawnedAt: Int)

    private val lobsterType = ServerCacheManager.getNpc(GIANT_LOBSTER.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $GIANT_LOBSTER")

    private val summons = HashMap<Npc, Summon>()

    override fun ScriptContext.startup() {
        onOpLoc1(CHEST) { search(it.loc) }
        onAiTimer(GIANT_LOBSTER) { tick(npc) }
        onNpcQueue(lobsterType, "queue.death") { defeated() }
        onPlayerLogout { lobsterOf(player.uid)?.let(::retreat) }
    }

    fun lobsterOf(owner: PlayerUid): Npc? = summons.entries.firstOrNull { it.value.owner == owner }?.key

    fun ownerOf(lobster: Npc): PlayerUid? = summons[lobster]?.owner

    private suspend fun ProtectedAccess.search(chest: BoundLocInfo) {
        arriveDelay()
        anim(SEARCH_SEQ)
        if (chest.coords != CHEST_TILE || !ahoy.needsScrap(player, SCRAP_2)) {
            mes("You search the chest, but find nothing of interest.")
            return
        }
        if (ahoy.isLobsterKilled(player)) {
            if (invAdd(inv, SCRAP_2).failure) {
                mes("You need a free inventory space to take anything from the chest.")
                return
            }
            objbox(SCRAP_2, "Now that the lobster is gone, you find a scrap of a map at the bottom of the chest.")
            return
        }
        val existing = lobsterOf(player.uid)
        if (existing != null) {
            mes("The giant lobster is still after you!")
            return
        }
        summon(player)
    }

    private fun summon(player: Player) {
        val lobster = Npc(lobsterType, SPAWN_TILE)
        lobster.respawns = false
        npcRepo.add(lobster, LIFETIME + DESPAWN_MARGIN)
        summons[lobster] = Summon(player.uid, mapClock.cycle)
        lobster.aiTimer(1)
        player.mes("A giant lobster scuttles out of the chest and attacks you!")
        lobster.opPlayer2(player, aiInteractions)
    }

    fun tick(lobster: Npc) {
        val summon = summons[lobster] ?: return
        val owner = summon.owner.resolve(playerList)
        val elapsed = mapClock.cycle - summon.spawnedAt
        if (owner == null || owner.hitpoints <= 0 || !owner.isNearby(lobster) || elapsed >= LIFETIME) {
            owner?.takeIf { it.isNearby(lobster) }?.mes("The giant lobster scuttles back into the chest.")
            retreat(lobster)
        }
    }

    private fun Player.isNearby(lobster: Npc): Boolean =
        coords.level == lobster.coords.level && coords.chebyshevDistance(lobster.coords) <= LEASH_RANGE

    private fun retreat(lobster: Npc) {
        summons.remove(lobster)
        if (lobster.isSlotAssigned) {
            npcRepo.del(lobster, Int.MAX_VALUE)
        }
    }

    /** Reads the owner before [NpcDeath] deletes the npc, which would otherwise lose the summon. */
    private suspend fun StandardNpcAccess.defeated() {
        creditKill(npc)
        death.deathNoDrops(this)
    }

    /** Marks the lobster's owner, and only its owner, as having killed it. */
    fun creditKill(lobster: Npc): Player? {
        val owner = summons.remove(lobster)?.owner?.resolve(playerList) ?: return null
        if (!owner.isNearby(lobster)) {
            return null
        }
        ahoy.setLobsterKilled(owner)
        owner.mes("You have killed the giant lobster. Now you can search the chest again.")
        return owner
    }

    companion object {
        const val CHEST = "loc.ahoy_chest_open"
        val CHEST_TILE = CoordGrid(3619, 3542, 0)

        /** South-west tile of the lobster's 2x2 footprint, beside the chest in the hold. */
        val SPAWN_TILE = CoordGrid(3616, 3543, 0)

        const val LIFETIME = 300
        private const val DESPAWN_MARGIN = 10
        private const val LEASH_RANGE = 12
        private const val SEARCH_SEQ = "seq.human_openchest"
    }
}
