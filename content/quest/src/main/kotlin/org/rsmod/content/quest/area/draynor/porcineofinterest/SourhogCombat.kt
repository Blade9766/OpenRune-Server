package org.rsmod.content.quest.area.draynor.porcineofinterest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onPlayerSoftQueueWithArgs
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.CORPSE_WITH_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.QUEST_SOURHOG
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SOURHOG
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_GOGGLES
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_SLAIN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sourhogs fight with their tusks up close and spit at range, and the spit is the whole point of
 * the quest: a face full of it blinds a player who is not wearing reinforced goggles, taking most
 * of their Attack and Defence with it. With the goggles on it is an ordinary ranged attack.
 *
 * Taking the attacks over from the generic npc combat is what makes the second attack style
 * possible; the sourhog's cache type carries only melee bonuses.
 */
@Singleton
class SourhogCombat
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in SOURHOGS) {
            val type = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onAiOpPlayer2(type) { attack(it.target) }
            onAiApPlayer2(type) { attack(it.target) }
        }
        onPlayerSoftQueueWithArgs<Blinding>(BLIND_QUEUE) { player.blind(args) }
    }

    private fun StandardNpcAccess.attack(target: Player) {
        if (!canAttack(target)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        if (!npc.isInCombat()) {
            resetMode()
            return
        }
        actionDelay = mapClock + npc.attackRate()
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid

        val inTuskRange = npc.isWithinDistance(target, MELEE_RANGE)
        if (!inTuskRange || random.of(SPIT_ONE_IN) == 0) {
            spit(target)
        } else {
            gore(target)
        }
    }

    private fun StandardNpcAccess.gore(target: Player) {
        anim(MELEE_SEQ)
        worldRepo.soundArea(npc, MELEE_SOUND, radius = SOUND_RADIUS)
        val landed = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)
        val damage =
            if (landed) random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush)) else 0
        target.finishNpcHit(npc, MELEE_HIT_DELAY, HitType.Melee, damage, hitModifier)
    }

    /**
     * The spit is rolled as a ranged attack either way. Unprotected eyes turn what would have been
     * a glancing hit into [BLIND_MIN_DAMAGE]-[BLIND_MAX_DAMAGE] and a blinding, so the goggles are
     * worth more than any armour the player could be wearing.
     */
    private fun StandardNpcAccess.spit(target: Player) {
        anim(SPIT_SEQ)
        worldRepo.soundArea(npc, SPIT_SOUND, radius = SOUND_RADIUS)

        val projType =
            ServerCacheManager.getProjectile(SPIT_PROJANIM.asRSCM(RSCMType.PROJANIM))
                ?: error("Missing projanim: $SPIT_PROJANIM")
        val flight =
            ProjAnim.fromNpcToPlayer(npc, target, SPIT_TRAVEL.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(flight)

        val landed = accuracy.rollRangedAccuracy(npc, target, random)
        val blinds = landed && !target.wearsGoggles()
        val damage =
            when {
                blinds -> random.of(BLIND_MIN_DAMAGE..BLIND_MAX_DAMAGE)
                landed -> random.of(0..maxHits.getRangedMaxHit(npc, target))
                else -> 0
            }

        if (landed) {
            target.spotanim(SPIT_IMPACT, delay = flight.clientCycles, height = IMPACT_HEIGHT)
            worldRepo.soundArea(target, SPIT_HIT_SOUND, delay = flight.clientCycles, radius = SOUND_RADIUS)
        }
        target.finishNpcHit(
            npc,
            flight.serverCycles,
            HitType.Ranged,
            damage,
            hitModifier,
            flight.clientCycles,
        )
        if (blinds) {
            target.softQueue(BLIND_QUEUE, flight.serverCycles, Blinding)
        }
    }

    /** Acid in the eyes: most of the player's Attack and Defence goes with it. */
    private fun Player.blind(blinding: Blinding) {
        check(blinding == Blinding)
        say(BLIND_CRY)
        statSub("stat.attack", constant = 0, percent = BLIND_DRAIN_PERCENT)
        statSub("stat.defence", constant = 0, percent = BLIND_DRAIN_PERCENT)
    }

    private fun StandardNpcAccess.canAttack(target: Player): Boolean {
        if (!target.isValidTarget()) {
            return false
        }
        val singleCombat = !mapMultiway(areaChecker)
        if (singleCombat) {
            if (target.isInPvpCombat()) {
                return false
            }
            if (target.isInPvnCombat()) {
                val aggressor = target.aggressiveNpc
                if (aggressor != null && aggressor != npc.uid) {
                    return false
                }
            }
        }
        return true
    }

    /** Marker for the soft queue that lands with the spit. */
    private object Blinding

    companion object {
        val SOURHOGS = listOf(SOURHOG, QUEST_SOURHOG)

        const val BLIND_QUEUE = "queue.porcine_sourhog_blind"
        const val BLIND_CRY = "Argh! My eyes!"

        /** Sourhogs are 3x3, so "next to the player" is two tiles from the south-west corner. */
        const val MELEE_RANGE = 2

        /** One attack in this many is a spit even when the player is right up against it. */
        const val SPIT_ONE_IN = 3

        const val MELEE_HIT_DELAY = 1
        const val IMPACT_HEIGHT = 92
        const val SOUND_RADIUS = 10

        const val BLIND_MIN_DAMAGE = 20
        const val BLIND_MAX_DAMAGE = 30
        const val BLIND_DRAIN_PERCENT = 90

        const val MELEE_SEQ = "seq.sourhog_attack_melee"
        const val MELEE_SOUND = "synth.sourhog_attack_melee"
        const val SPIT_SEQ = "seq.sourhog_attack_ranged"
        const val SPIT_SOUND = "synth.sourhog_attack_ranged"
        const val SPIT_HIT_SOUND = "synth.sourhog_hit"
        const val SPIT_TRAVEL = "spotanim.sourhog_spit_travel"
        const val SPIT_IMPACT = "spotanim.sourhog_spit_impact"
        const val SPIT_PROJANIM = "projanim.magic_spell"

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}

/**
 * Leaves a carcass where the quest's sourhog fell so the player can cut a foot from it, and moves
 * the quest on. The carcass is a `varbit.porcine_footcut` multiloc, so each player sees it with or
 * without its foot according to their own progress.
 *
 * The kill lands mid-combat, when protected access is busy, so the stage change is retried on the
 * world queue until the player is free.
 */
class SourhogKillHook
@Inject
constructor(
    private val porcine: PorcineOfInterestQuest,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {
    private val sourhogIds = SourhogCombat.SOURHOGS.map { it.asRSCM(RSCMType.NPC) }.toSet()

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in sourhogIds) {
            return
        }
        val hero = context.hero
        if (hero.righthand?.isType(KATANA) == true) {
            hero.say("Nothing personal, pig.")
        }
        if (porcine.stage(hero) != STAGE_GOGGLES) {
            return
        }
        hero.porcineFootCut = CORPSE_WITH_FOOT
        locRepo.add(
            context.npc.coords,
            CARCASS,
            CARCASS_LIFETIME,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        launchWhenFree(hero.uid, LAUNCH_ATTEMPTS) {
            if (porcine.stage(player) != STAGE_GOGGLES) {
                return@launchWhenFree
            }
            porcine.advanceTo(this, STAGE_SLAIN)
            startDialogue {
                chatPlayer(
                    happy,
                    "That's the end of that. Now for a foot to show Sarah.",
                )
            }
        }
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
        const val CARCASS = "loc.porcine_dead_sourhog"
        const val KATANA = "obj.katana"

        /** Long enough to come back for the foot after a trip to the bank. */
        const val CARCASS_LIFETIME = 3000
        const val LAUNCH_ATTEMPTS = 20
    }
}
