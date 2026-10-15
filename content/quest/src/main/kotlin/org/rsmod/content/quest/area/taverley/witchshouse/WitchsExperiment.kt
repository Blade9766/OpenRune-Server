package org.rsmod.content.quest.area.taverley.witchshouse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_DEFEATED_EXPERIMENT
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.inShed
import org.rsmod.content.quest.area.wilderness.magearena.freeFootprint
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** The witch's experiment each player has woken in the shed, one form at a time. */
@Singleton
class WitchsExperiments
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
    private val aiInteractions: AiPlayerInteractions,
) {
    private val experiments = HashMap<PlayerUid, Npc>()

    private val formTypes: List<NpcServerType> by lazy {
        FORMS.map { ServerCacheManager.getNpc(it.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $it") }
    }

    fun of(player: Player): Npc? = experiments[player.uid]?.takeIf { it.isSlotAssigned }

    fun summon(player: Player, form: Int, attack: Boolean): Npc {
        dismiss(player)
        val type = formTypes[form]
        val tile = collision.freeFootprint(SPAWN_TILE, type.size, radius = SPAWN_RADIUS) ?: SPAWN_TILE
        val npc = Npc(type, tile)
        npcRepo.add(npc, LIFETIME_TICKS)
        npc.respawns = false
        npc.assignSpawnOwner(player, clock.cycle)
        npc.facePlayer(player)
        experiments[player.uid] = npc
        if (attack) {
            npc.opPlayer2(player, aiInteractions)
        }
        return npc
    }

    fun attack(player: Player, npc: Npc) {
        npc.opPlayer2(player, aiInteractions)
    }

    fun dismiss(player: Player) {
        val npc = experiments.remove(player.uid) ?: return
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun forget(npc: Npc) {
        experiments.values.removeIf { it === npc }
    }

    companion object {
        val FORMS =
            listOf(
                "npc.shapeshifterglob",
                "npc.shapeshifterspider",
                "npc.shapeshifterbear",
                "npc.shapeshifterwolf",
            )

        const val LIFETIME_TICKS = 500
        const val SPAWN_RADIUS = 2

        /** Between the sacks and the crate the ball sits on. */
        val SPAWN_TILE = CoordGrid(2935, 3462, 0)
    }
}

/**
 * The experiment shapeshifts each time it falls - skavid, spider, bear, then wolf - and the new
 * form turns on the player straight away. Killing the wolf lets the player take the ball.
 */
class WitchsExperimentScript
@Inject
constructor(
    private val witchsHouse: WitchsHouseQuest,
    private val experiments: WitchsExperiments,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for ((index, form) in WitchsExperiments.FORMS.withIndex()) {
            val type = ServerCacheManager.getNpc(form.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $form")
            onNpcQueue(type, DEATH_QUEUE) { formDefeated(index) }
        }
        onPlayerLogout { experiments.dismiss(player) }
    }

    private suspend fun StandardNpcAccess.formDefeated(index: Int) {
        val owner = npc.spawnOwner.resolve(playerList)
        experiments.forget(npc)
        death.deathNoDrops(this)
        if (owner == null || !inShed(owner.coords)) {
            return
        }
        if (index == WitchsExperiments.FORMS.lastIndex) {
            owner.mes("You finally kill the shapeshifter once and for all.")
            launchWhenFree(owner.uid) {
                witchsHouse.advanceTo(this, STAGE_DEFEATED_EXPERIMENT)
            }
            return
        }
        val (deform, becomes) = TRANSFORM_MESSAGES[index]
        experiments.summon(owner, index + 1, attack = true)
        owner.mes(deform)
        owner.mes(becomes)
    }

    private fun launchWhenFree(uid: PlayerUid, block: suspend ProtectedAccess.() -> Unit) {
        launchWhenFree(uid, LAUNCH_ATTEMPTS, block)
    }

    private fun launchWhenFree(
        uid: PlayerUid,
        attempts: Int,
        block: suspend ProtectedAccess.() -> Unit,
    ) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (!launcher.launch(player, block = block) && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    private companion object {
        const val DEATH_QUEUE = "queue.death"
        const val LAUNCH_ATTEMPTS = 50

        val TRANSFORM_MESSAGES =
            listOf(
                "The shapeshifter's body begins to deform!" to "The shapeshifter turns into a spider!",
                "The shapeshifter's body begins to twist!" to "The shapeshifter turns into a bear!",
                "The shapeshifter's body pulses!" to "The shapeshifter turns into a wolf!",
            )
    }
}

/** Nobody may fight another player's experiment, and dying sends the player's experiment away. */
class WitchsExperimentHooks
@Inject
constructor(private val experiments: Provider<WitchsExperiments>) :
    NpcAttackValidateHook, PlayerDeathCleanupHook {
    private val formIds by lazy { WitchsExperiments.FORMS.map { it.asRSCM(RSCMType.NPC) }.toSet() }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in formIds || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("That isn't after you.")
    }

    override fun cleanup(player: Player) {
        experiments.get().dismiss(player)
    }
}
