package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RECEIVED_DAGGER
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How Nezikchened fights. Beside his target he claws with his melee attack; from further off he
 * casts his own flamestrike, a fire spell that hits up to 18. In the Viyeldi caves, while the
 * player still carries Echned Zekin's dagger errand, he may once in the fight take out a dark
 * dagger and hurl it, which the player dodges one time in three.
 *
 * He only ever attacks the player who drew him out.
 */
class NezikchenedCombat
@Inject
constructor(
    private val legends: LegendsQuest,
    private val nezikchened: Nezikchened,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val world: WorldRepository,
    private val hitModifier: PlayerHitModifier,
) : PluginScript() {

    private val threwDagger = HashSet<Npc>()

    override fun ScriptContext.startup() {
        val type = ServerCacheManager.getNpc(Nezikchened.DEMON.asRSCM(RSCMType.NPC)) ?: error("Missing npc: ${Nezikchened.DEMON}")
        onAiOpPlayer2(type) { attack(it.target) }
        onAiApPlayer2(type) { attack(it.target) }
        onEvent<NpcStateEvents.Delete> { threwDagger.remove(npc) }
    }

    private fun StandardNpcAccess.attack(target: Player) {
        val owner = nezikchened.ownerOf(npc)
        if (!target.isValidTarget() || (owner != null && owner != target.uid)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        actionDelay = mapClock + npc.attackRate()
        setAttackVars(target)
        if (throwDagger(target)) {
            return
        }
        if (isBeside(target)) {
            claw(target)
        } else {
            flamestrike(target)
        }
    }

    /** Next to the target on one of the four sides of his 3x3 body. */
    private fun StandardNpcAccess.isBeside(target: Player): Boolean {
        val size = npc.size
        val dx = maxOf(npc.coords.x - target.coords.x, 0, target.coords.x - (npc.coords.x + size - 1))
        val dz = maxOf(npc.coords.z - target.coords.z, 0, target.coords.z - (npc.coords.z + size - 1))
        return dx + dz == 1
    }

    private fun StandardNpcAccess.claw(target: Player) {
        val attackAnim = npc.visType.paramOrNull(params.attack_anim)
        anim(attackAnim?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) } ?: CLAW_SEQ)
        world.soundArea(npc, DEMON_ATTACK_SOUND, radius = SOUND_RADIUS)
        val landed = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Slash, random)
        val damage = if (landed) random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Slash)) else 0
        target.finishNpcHit(npc, MELEE_DELAY, HitType.Melee, damage, hitModifier)
    }

    private fun StandardNpcAccess.flamestrike(target: Player) {
        anim(CAST_SEQ)
        spotanim(FLAMESTRIKE_CASTING, height = CAST_HEIGHT)
        world.soundArea(npc, CAST_SOUND, radius = SOUND_RADIUS)
        val projType = ServerCacheManager.getProjectile(PROJANIM.asRSCM(RSCMType.PROJANIM)) ?: error("Missing projanim: $PROJANIM")
        val proj = ProjAnim.fromNpcToPlayer(npc, target, FLAMESTRIKE_TRAVEL.asRSCM(RSCMType.SPOTANIM), projType)
        world.projAnim(proj)
        if (!accuracy.rollMagicAccuracy(npc, target, random)) {
            target.spotanim(SPLASH_SPOTANIM, delay = proj.clientCycles, height = IMPACT_HEIGHT)
            world.soundArea(target, SPLASH_SOUND, delay = proj.clientCycles, radius = SOUND_RADIUS)
            target.queueCombatRetaliate(npc, delay = proj.serverCycles)
            return
        }
        val damage = random.of(0..FLAMESTRIKE_MAX_HIT)
        target.spotanim(FLAMESTRIKE_IMPACT, delay = proj.clientCycles, height = IMPACT_HEIGHT)
        world.soundArea(target, HIT_SOUND, delay = proj.clientCycles, radius = SOUND_RADIUS)
        target.finishNpcHit(npc, proj.serverCycles, HitType.Magic, damage, hitModifier, proj.clientCycles)
    }

    /**
     * The dark dagger, thrown at most once a fight and only at the source, where the player is
     * meant to be carrying the twin of it for Echned Zekin.
     */
    private fun StandardNpcAccess.throwDagger(target: Player): Boolean {
        if (nezikchened.fightOf(npc) != Nezikchened.Fight.Water || npc in threwDagger) {
            return false
        }
        if (!LegendsCoords.inViyeldiCaves(target.coords) || legends.stage(target) != STAGE_RECEIVED_DAGGER) {
            return false
        }
        if (random.of(DAGGER_ONE_IN) != 0) {
            return false
        }
        threwDagger += npc
        anim(THROW_SEQ)
        target.mes("The demon takes out a dark dagger and throws it at you.")
        val projType = ServerCacheManager.getProjectile(THROWN_PROJANIM.asRSCM(RSCMType.PROJANIM)) ?: return true
        val proj = ProjAnim.fromNpcToPlayer(npc, target, DAGGER_TRAVEL.asRSCM(RSCMType.SPOTANIM), projType)
        world.projAnim(proj)
        if (random.of(DODGE_ONE_IN) == 0) {
            target.mes("But you neatly manage to dodge the attack.")
            target.queueCombatRetaliate(npc, delay = proj.serverCycles)
            return true
        }
        target.mes("The dagger hits you with an agonising blow.")
        npc.say("Ha, ha, ha... feel my power!")
        world.soundArea(target, DAGGER_HIT_SOUND, delay = proj.clientCycles, radius = SOUND_RADIUS)
        target.finishNpcHit(npc, proj.serverCycles, HitType.Typeless, random.of(0..DAGGER_MAX_HIT), hitModifier, proj.clientCycles)
        return true
    }

    private fun StandardNpcAccess.setAttackVars(target: Player) {
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid
    }

    private companion object {
        const val FLAMESTRIKE_MAX_HIT = 18
        const val DAGGER_MAX_HIT = 18
        const val DAGGER_ONE_IN = 10
        const val DODGE_ONE_IN = 3
        const val MELEE_DELAY = 1
        const val CAST_HEIGHT = 92
        const val IMPACT_HEIGHT = 124
        const val SOUND_RADIUS = 10

        const val CLAW_SEQ = "seq.demon_attack"
        const val CAST_SEQ = "seq.demon_casting"
        const val THROW_SEQ = "seq.demon_casting"
        const val FLAMESTRIKE_CASTING = "spotanim.nezik_flamestrike_casting"
        const val FLAMESTRIKE_TRAVEL = "spotanim.nezik_flamestrike_travel"
        const val FLAMESTRIKE_IMPACT = "spotanim.nezik_flamestrike"
        const val DAGGER_TRAVEL = "spotanim.black_tknife_travel"
        const val SPLASH_SPOTANIM = "spotanim.failedspell_impact"
        const val PROJANIM = "projanim.magic_spell"
        const val THROWN_PROJANIM = "projanim.thrown"

        const val DEMON_ATTACK_SOUND = "synth.demon_attack"
        const val CAST_SOUND = "synth.firestrike_cast_and_fire"
        const val HIT_SOUND = "synth.firestrike_hit"
        const val SPLASH_SOUND = "synth.spellfail"
        const val DAGGER_HIT_SOUND = "synth.demon_attack"

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}
