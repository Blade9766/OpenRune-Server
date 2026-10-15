package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerHit
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The events behind [DreamFight]: each fear's death bringing on the next, hits feeding Cyrisus'
 * courage and sending The Illusive underground, The Inadequacy's choice of melee or ranged, and
 * the lectern that is the only way out of the dream.
 */
class DreamFightScript
@Inject
constructor(
    private val fight: DreamFight,
    private val arena: DreamArena,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val npcList: NpcList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val hitModifier: PlayerHitModifier,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerSoftTimer(DreamFight.FIGHT_TIMER) {
            fight.tick(player)
            if (!fight.isFighting(player)) {
                player.clearSoftTimer(DreamFight.FIGHT_TIMER)
            }
        }
        for (fear in Fear.entries) {
            val type = npcType(fear.type)
            onNpcHit(type) { hitTaken(npc, hit) }
            if (fear == Fear.Illusive) {
                onNpcQueue(type, DEATH_QUEUE) { illusiveFalls() }
            } else {
                onNpcQueue(type, DEATH_QUEUE) { fearFalls() }
            }
        }
        val doubt = npcType(DreamFight.DOUBT)
        onNpcHit(doubt) { hitTaken(npc, hit) }
        onNpcQueue(doubt, DEATH_QUEUE) { fearFalls() }

        val inadequacy = npcType(Fear.Inadequacy.type)
        onAiOpPlayer2(inadequacy) { inadequacyAttacks(it.target) }
        onAiApPlayer2(inadequacy) { inadequacyAttacks(it.target) }

        onPlayerHit {
            if (hit.isFromNpc && fight.isFighting(player)) {
                val attacker = hit.resolveNpcSource(npcList) ?: return@onPlayerHit
                if (attacker.isSpawnOwnedBy(player)) {
                    fight.avenge(player, attacker, hit.damage)
                }
            }
        }

        onOpLoc1(LECTERN) { leaveDream() }
        onOpLoc2(LECTERN) { leaveDream() }
        onPlayerLogout {
            fight.cleanup(player)
            arena.forget(player)
        }
    }

    private fun hitTaken(npc: Npc, hit: Hit) {
        if (!hit.isFromPlayer) {
            return
        }
        val attacker = hit.resolvePlayerSource(playerList) ?: return
        if (npc.isSpawnOwnedBy(attacker)) {
            fight.onHit(attacker, npc, hit.damage)
        }
    }

    private suspend fun StandardNpcAccess.fearFalls() {
        val owner = npc.spawnOwner.resolve(playerList)
        death.deathNoDrops(this)
        owner ?: return
        fight.onFearDeath(owner, npc)
    }

    private suspend fun StandardNpcAccess.illusiveFalls() {
        val owner = npc.spawnOwner.resolve(playerList)
        if (owner == null || fight.encounterOf(owner)?.boss !== npc) {
            death.deathNoDrops(this)
            return
        }
        npc.resetMode()
        val illusive = npc
        launchWhenFree(owner.uid, LAUNCH_ATTEMPTS) {
            with(fight) { conquer(illusive) }
            fight.cleanup(player)
            fadeToBlack()
            with(arena) { wake() }
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
    }

    /** Melee from up close; anyone keeping their distance is spat at instead. */
    private fun StandardNpcAccess.inadequacyAttacks(target: Player) {
        if (!target.isValidTarget() || !npc.isSpawnOwnedBy(target)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        actionDelay = mapClock + npc.attackRate()
        if (inMeleeReach(npc, target)) {
            anim(MELEE_SEQ)
            val hits = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)
            val damage = if (hits) random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush)) else 0
            target.finishNpcHit(npc, MELEE_DELAY, HitType.Melee, damage, hitModifier)
        } else {
            anim(RANGED_SEQ)
            npc.spotanim(RANGED_SPOT)
            val hits = accuracy.rollRangedAccuracy(npc, target, random)
            val damage = if (hits) random.of(0..maxHits.getRangedMaxHit(npc, target)) else 0
            target.finishNpcHit(npc, RANGED_DELAY, HitType.Ranged, damage, hitModifier)
        }
    }

    private suspend fun ProtectedAccess.leaveDream() {
        if (!arena.isDreaming(player)) {
            return
        }
        var leave = false
        startDialogue {
            leave = choice2("Leave", true, "Stay", false, title = "You'll have to fight from the beginning.")
        }
        if (!leave) {
            return
        }
        fight.cleanup(player)
        fadeToBlack()
        with(arena) { wake() }
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        mes("You wake up beside the brazier in the Dream Hall.")
    }

    private fun launchWhenFree(uid: PlayerUid, attempts: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (!launcher.launch(player, block = block) && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    companion object {
        const val LECTERN = "loc.dream_plinth"
        private const val DEATH_QUEUE = "queue.death"
        private const val LAUNCH_ATTEMPTS = 20
        private const val MELEE_SEQ = "seq.dream_inadequacy_attack_front"
        private const val RANGED_SEQ = "seq.dream_inadequacy_ranged_attack"
        private const val RANGED_SPOT = "spotanim.dream_inadequacy_mouth_spotanim"
        private const val MELEE_DELAY = 1
        private const val RANGED_DELAY = 2

        /** Whether [target] stands on a tile beside (not diagonal to) the npc's footprint. */
        fun inMeleeReach(npc: Npc, target: Player): Boolean {
            val size = npc.type.size
            val dx = gap(target.coords.x, npc.coords.x, size)
            val dz = gap(target.coords.z, npc.coords.z, size)
            return target.coords.level == npc.coords.level && dx + dz == 1
        }

        private fun gap(value: Int, start: Int, size: Int): Int =
            when {
                value < start -> start - value
                value >= start + size -> value - (start + size - 1)
                else -> 0
            }
    }
}
