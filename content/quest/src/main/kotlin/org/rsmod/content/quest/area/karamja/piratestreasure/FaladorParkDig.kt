package org.rsmod.content.quest.area.karamja.piratestreasure

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CASKET
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.GARDENER
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.TREASURE_SPOT
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Hector's treasure, buried under the dirt X south of the Saradomin statue in Falador Park. The
 * first dig brings an irate gardener down on the player; no more digging gets done while he is
 * attacking, but once he is dead, or left behind by running out of the park, the next dig turns up
 * the casket.
 */
@Singleton
class FaladorParkDig
@Inject
constructor(
    private val treasure: PiratesTreasureQuest,
    private val spadeDigging: SpadeDigging,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private data class Ambush(val owner: PlayerUid, val spawnedAt: Int)

    private val ambushes = HashMap<Npc, Ambush>()

    override fun ScriptContext.startup() {
        spadeDigging.register(TREASURE_SPOT, radius = 1) { dig() }
        onAiTimer(GARDENER) { tick(npc) }
        onPlayerLogout { gardenerOf(player.uid)?.let(::retreat) }
    }

    fun gardenerOf(owner: PlayerUid): Npc? {
        ambushes.keys.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        return ambushes.entries.firstOrNull { it.value.owner == owner }?.key
    }

    internal suspend fun ProtectedAccess.dig() {
        if (treasure.stage(player) == STAGE_MESSAGE && gardenerOf(player.uid) != null) {
            mes("I can't dig up anything with him attacking me!")
            return
        }
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(DIG_TICKS)
        if (treasure.stage(player) != STAGE_MESSAGE) {
            mes("You dig, but find nothing of interest.")
            return
        }
        if (!player.ptGardenerAppeared) {
            summonGardener(player)
            return
        }
        mes("You dig a hole in the ground...")
        invAddOrDrop(objRepo, CASKET)
        mes("and find a little chest of treasure.")
        treasure.quest.completeQuest(this)
    }

    private fun summonGardener(player: Player) {
        player.ptGardenerAppeared = true
        val tile = collision.nearestFree(player.coords.translate(1, 0), SPAWN_RADIUS) ?: player.coords
        val gardener = Npc(GARDENER, tile)
        npcRepo.add(gardener, LIFETIME + DESPAWN_MARGIN)
        gardener.respawns = false
        gardener.assignSpawnOwner(player, clock.cycle)
        ambushes[gardener] = Ambush(player.uid, clock.cycle)
        gardener.aiTimer(1)
        gardener.say(BATTLE_CRY)
        gardener.opPlayer2(player, aiInteractions)
    }

    fun tick(gardener: Npc) {
        val ambush = ambushes[gardener] ?: return
        val owner = ambush.owner.resolve(playerList)
        val elapsed = clock.cycle - ambush.spawnedAt
        if (owner == null || owner.hitpoints <= 0 || !owner.inPark() || elapsed >= LIFETIME) {
            retreat(gardener)
        }
    }

    private fun Player.inPark(): Boolean =
        coords.level == TREASURE_SPOT.level && coords.chebyshevDistance(TREASURE_SPOT) <= PARK_RADIUS

    private fun retreat(gardener: Npc) {
        ambushes.remove(gardener)
        if (gardener.isSlotAssigned) {
            npcRepo.del(gardener, Int.MAX_VALUE)
        }
    }

    companion object {
        const val BATTLE_CRY = "First moles, now this! Take this, vandal!"
        const val LIFETIME = 500
        const val PARK_RADIUS = 16
        private const val DESPAWN_MARGIN = 10
        private const val SPAWN_RADIUS = 2
        private const val DIG_SEQ = "seq.human_dig"
        private const val DIG_SOUND = "synth.digspade"
        private const val DIG_TICKS = 1
    }
}
