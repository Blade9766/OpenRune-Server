package org.rsmod.content.quest.area.tirannwn.rovingelves

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.refs.params
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
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.MOSS_GUARDIAN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_GET_SEED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Moss Guardian's "magical melee": each swing first rolls crush accuracy against the player's
 * melee defence for an ordinary melee hit, which Protect from Melee blocks. If that roll fails it
 * rolls again against the player's magic defence, and a hit from that second roll is magic damage
 * that no protection prayer stops. Both use the guardian's melee max hit, and every swing throws
 * up its magical splash. The Nightmare Zone copies fight the same way.
 */
class MossGuardian
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in GUARDIANS) {
            val type = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onAiOpPlayer2(type) { attack(it.target) }
            onAiApPlayer2(type) { attack(it.target) }
        }
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

        anim(RSCM.getReverseMapping(RSCMType.SEQ, npc.visType.param(params.attack_anim).id))
        npc.visType.paramOrNull(params.attack_sound)?.let { worldRepo.soundArea(npc, it.id, radius = SOUND_RADIUS) }
        target.spotanim(SPLASH_SPOTANIM)

        val swing = roll(npc, target, random)
        target.finishNpcHit(npc, HIT_DELAY, swing.type, swing.damage, hitModifier, penetration = swing.penetration)
    }

    /** One swing: which roll landed, if any, and how hard. */
    data class Swing(val type: HitType, val damage: Int, val penetration: Int)

    fun roll(npc: Npc, target: Player, random: GameRandom): Swing {
        val maxHit = maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush)
        if (accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)) {
            return Swing(HitType.Melee, random.of(0..maxHit), penetration = 0)
        }
        if (accuracy.rollMagicalMeleeAccuracy(npc, target, random)) {
            return Swing(HitType.Magic, random.of(0..maxHit), penetration = PIERCES_PRAYER)
        }
        return Swing(HitType.Melee, 0, penetration = 0)
    }

    private fun StandardNpcAccess.canAttack(target: Player): Boolean {
        if (!target.isValidTarget()) {
            return false
        }
        if (!mapMultiway(areaChecker)) {
            if (target.isInPvpCombat()) {
                return false
            }
            val aggressor = target.aggressiveNpc
            if (target.isInPvnCombat() && aggressor != null && aggressor != npc.uid) {
                return false
            }
        }
        return true
    }

    companion object {
        val GUARDIANS =
            listOf(MOSS_GUARDIAN, "npc.nzone_roving_mossgiant_normal", "npc.nzone_roving_mossgiant_hard")

        const val SPLASH_SPOTANIM = "spotanim.roving_mossgiant_impact"
        const val HIT_DELAY = 1
        const val SOUND_RADIUS = 10

        /** Takes the whole of a protection prayer's reduction away. */
        const val PIERCES_PRAYER = 100

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}

/**
 * Credits the Moss Guardian kill to the player the death system names as its killer, and only
 * while Eluned has them looking for the seed. Ordinary moss giants and the Nightmare Zone copies
 * are other npc types and never count. The seed itself comes from the guardian's drop table,
 * which [RovingElvesQuest.needsSeedDrop] vetoes once the player holds a seed.
 */
class MossGuardianKillHook @Inject constructor(private val roving: RovingElvesQuest) : NpcDeathKillHook {
    private val guardian by lazy { MOSS_GUARDIAN.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != guardian || roving.stage(context.hero) != STAGE_GET_SEED) {
            return
        }
        RovingElvesQuest.setVarBit(context.hero, "varbit.roving_guardian_slain", 1)
    }
}
