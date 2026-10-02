package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CAMP_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CAMP_GUARD_2
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.ENCOUNTER_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GUARD_KILLED
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
 * The level-110 Tyras guard who catches the player coming out of the dense forest west of the
 * tracker. He uses the cache's combat definition (a reach of one tile, so the usual safespots
 * work) and the shared Tyras guard drop table.
 *
 * Each player has at most one guard, spawned for them and owned by them: nobody else can attack
 * him, and only his owner is credited when he dies, whoever else is nearby. He goes back into the
 * forest if his owner walks off, dies, logs out or takes longer than [LIFETIME] cycles, and going
 * through the forest again brings him back. Killing any of the camp guards while the guard is
 * still owed counts as well, as it does on the wiki.
 */
@Singleton
class TyrasGuardEncounter
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private data class Summon(val owner: PlayerUid, val spawnedAt: Int)

    private val guardType by lazy {
        ServerCacheManager.getNpc(ENCOUNTER_GUARD.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $ENCOUNTER_GUARD")
    }

    private val summons = HashMap<Npc, Summon>()

    override fun ScriptContext.startup() {
        onAiTimer(ENCOUNTER_GUARD) { tick(npc) }
        onNpcQueue(guardType, "queue.death") { defeated() }
        onPlayerLogout { guardOf(player.uid)?.let(::retreat) }
    }

    fun guardOf(owner: PlayerUid): Npc? = summons.entries.firstOrNull { it.value.owner == owner }?.key

    fun ownerOf(guard: Npc): PlayerUid? = summons[guard]?.owner

    fun summon(access: ProtectedAccess) {
        val player = access.player
        if (regicide.stage(player) != STAGE_DENSE_FOREST) {
            return
        }
        val existing = guardOf(player.uid)
        if (existing != null) {
            existing.opPlayer2(player, aiInteractions)
            return
        }
        val tile = collision.nearestFree(player.coords.translate(-SPAWN_OFFSET, 0), SPAWN_RADIUS) ?: return
        val guard = Npc(guardType, tile)
        guard.respawns = false
        npcRepo.add(guard, LIFETIME + DESPAWN_MARGIN)
        guard.assignSpawnOwner(player, mapClock.cycle)
        summons[guard] = Summon(player.uid, mapClock.cycle)
        guard.aiTimer(1)
        player.seenGuard = 1
        guard.say(BATTLE_CRY)
        guard.opPlayer2(player, aiInteractions)
    }

    fun tick(guard: Npc) {
        val summon = summons[guard] ?: return
        val owner = summon.owner.resolve(playerList)
        val elapsed = mapClock.cycle - summon.spawnedAt
        if (owner == null || owner.hitpoints <= 0 || !owner.isNearby(guard) || elapsed >= LIFETIME) {
            owner?.takeIf { it.isNearby(guard) }?.mes("The guard melts back into the forest.")
            retreat(guard)
        }
    }

    private fun Player.isNearby(guard: Npc): Boolean =
        coords.level == guard.coords.level && coords.chebyshevDistance(guard.coords) <= LEASH_RANGE

    private fun retreat(guard: Npc) {
        summons.remove(guard)
        if (guard.isSlotAssigned) {
            npcRepo.del(guard, Int.MAX_VALUE)
        }
    }

    /** Reads the owner before [NpcDeath] deletes the guard, which would otherwise lose the summon. */
    private suspend fun StandardNpcAccess.defeated() {
        creditKill(npc)
        death.deathWithDrops(this)
    }

    /** Marks the guard's owner, and only its owner, as having fought their way past him. */
    fun creditKill(guard: Npc): Player? {
        val owner = summons.remove(guard)?.owner?.resolve(playerList) ?: return null
        if (!owner.isNearby(guard) || regicide.stage(owner) != STAGE_DENSE_FOREST) {
            return null
        }
        owner.mes("With the guard dead, you should be able to find Tyras's camp.")
        credit(regicide, launcher, owner)
        return owner
    }

    companion object {
        const val LIFETIME = 500
        const val BATTLE_CRY = "I see you making friends with that elf, traitor!"
        private const val DESPAWN_MARGIN = 10
        private const val LEASH_RANGE = 16
        private const val SPAWN_OFFSET = 3
        private const val SPAWN_RADIUS = 3

        val CAMP_GUARDS = listOf(CAMP_GUARD, CAMP_GUARD_2)
    }
}

/**
 * Moves [player] past the guard. The stage is not the quest's last, so if the player cannot take a
 * script right now it is stored directly rather than lost.
 */
internal fun credit(regicide: RegicideQuest, launcher: ProtectedAccessLauncher, player: Player) {
    if (!launcher.launch(player) { regicide.advanceTo(this, STAGE_GUARD_KILLED) }) {
        regicide.quest.jumpToStage(player, STAGE_GUARD_KILLED)
    }
}

/**
 * The camp guards are the other way past: killing one while the forest guard is still owed counts
 * for the player credited with the kill.
 */
class TyrasGuardKillHook
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {
    private val campIds by lazy { TyrasGuardEncounter.CAMP_GUARDS.map { it.asRSCM(RSCMType.NPC) }.toSet() }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in campIds || regicide.stage(context.hero) != STAGE_DENSE_FOREST) {
            return
        }
        credit(regicide, launcher, context.hero)
    }
}

/** Only the player a quest guard was summoned for may fight him. */
class TyrasGuardAttackHook @Inject constructor() : NpcAttackValidateHook {
    private val encounterId by lazy { ENCOUNTER_GUARD.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != encounterId || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("He isn't interested in you.")
    }
}
