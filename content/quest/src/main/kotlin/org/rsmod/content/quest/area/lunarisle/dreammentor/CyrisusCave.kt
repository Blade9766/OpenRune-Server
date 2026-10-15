package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

/**
 * Cyrisus in his little cave in the Lunar Isle mine. Everyone who finds him finds him in their own
 * state of recovery, so each player in the cave gets their own Cyrisus, spawned when they crawl in
 * (or log in there) in the form their progress calls for, and removed when they leave.
 */
@Singleton
class CyrisusCave
@Inject
constructor(
    private val dreamMentor: DreamMentorQuest,
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
) {
    private val spawned = HashMap<PlayerUid, Npc>()

    fun cyrisusOf(player: Player): Npc? = spawned[player.uid]?.takeIf { it.isSlotAssigned }

    /** The npc type Cyrisus should be for [player], or null once he has left the cave. */
    fun form(player: Player): String? {
        val stage = dreamMentor.stage(player)
        return when {
            dreamMentor.isComplete(player) || stage > DreamMentorQuest.STAGE_RESTORED -> null
            stage < DreamMentorQuest.STAGE_AWAKE -> UNCONSCIOUS
            stage < DreamMentorQuest.STAGE_SITTING -> BARELY_CONSCIOUS
            stage < DreamMentorQuest.STAGE_STANDING -> SITTING
            stage < DreamMentorQuest.STAGE_ARMED -> STANDING
            else -> CombatStyle.of(player).cyrisusType
        }
    }

    /** Spawns, swaps or removes the player's Cyrisus so it matches their progress and position. */
    fun refresh(player: Player) {
        val wanted = form(player).takeIf { inCave(player.coords) }
        val current = cyrisusOf(player)
        if (current != null && wanted != null && current.isType(wanted)) {
            return
        }
        despawn(player)
        wanted ?: return
        val type = ServerCacheManager.getNpc(wanted.asRSCM(RSCMType.NPC)) ?: return
        val npc = Npc(type, SPAWN)
        npcRepo.add(npc, LIFETIME)
        npc.respawns = false
        npc.assignSpawnOwner(player, clock.cycle)
        spawned[player.uid] = npc
    }

    fun despawn(player: Player) {
        val npc = spawned.remove(player.uid) ?: return
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun isSomeoneElses(player: Player, npc: Npc): Boolean = npc.isSpawnOwnedByOther(player)

    suspend fun ProtectedAccess.crawl(into: Boolean) {
        anim(CRAWL_SEQ)
        delay(CRAWL_TICKS)
        telejump(if (into) CAVE_SIDE else MINE_SIDE, TeleportType.Exempt)
        resetAnim()
        if (into) {
            refresh(player)
            softTimer(CAVE_TIMER, CAVE_CHECK_TICKS)
        } else {
            despawn(player)
            clearSoftTimer(CAVE_TIMER)
        }
    }

    companion object {
        const val UNCONSCIOUS = "npc.dream_cyrisus_unconscious"
        const val BARELY_CONSCIOUS = "npc.dream_cyrisus_barely_conscious"
        const val SITTING = "npc.dream_cyrisus_sitting"
        const val STANDING = "npc.dream_cyrisus"

        val CAVE_FORMS =
            listOf(UNCONSCIOUS, BARELY_CONSCIOUS, SITTING, STANDING) + CombatStyle.entries.map { it.cyrisusType }

        const val ENTRANCE = "loc.dream_cave_wall_entrance"
        const val CAVE_TIMER = "timer.dream_mentor_cave"
        const val CAVE_CHECK_TICKS = 5

        /** Both ends of the crawlway are the same loc; this one opens onto the main mine. */
        val MINE_ENTRANCE = CoordGrid(2335, 10346, 2)
        val MINE_SIDE = CoordGrid(2335, 10345, 2)
        val CAVE_SIDE = CoordGrid(2341, 10356, 2)
        val SPAWN = CoordGrid(2348, 10362, 2)

        private const val LIFETIME = 6000
        private const val CRAWL_SEQ = "seq.human_crawling"
        private const val CRAWL_TICKS = 2

        fun inCave(coords: CoordGrid): Boolean =
            coords.level == SPAWN.level && coords.x in 2336..2352 && coords.z in 10355..10366
    }
}
