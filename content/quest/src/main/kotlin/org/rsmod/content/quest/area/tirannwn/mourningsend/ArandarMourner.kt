package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The level-11 mourner who crosses the Arandar pass. His first move against each opponent is to
 * throw a vial instead of swinging: when it lands, every combat stat of that one player above
 * [DRAINED_LEVEL] (Attack, Strength, Defence, Ranged, Magic, Hitpoints and Prayer) falls to it.
 * Stats already lower stay where they are and base levels never change, so ordinary regeneration,
 * food and restore potions bring them back as they would after any drain. A player who kills him
 * before his first turn (one hit, or a special attack that hits several times) is never drained.
 *
 * After the vial he fights as a plain crush melee npc. Which opponents have had their vial is kept
 * per mourner and forgotten when he despawns, so the next mourner throws again.
 */
class ArandarMourner
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    private val vialed = HashMap<NpcUid, MutableSet<PlayerUid>>()
    private val vialSpotanim by lazy { SpotanimType(VIAL_TRAVEL.asRSCM(RSCMType.SPOTANIM)) }

    override fun ScriptContext.startup() {
        val type = ServerCacheManager.getNpc(MOURNER.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $MOURNER")
        onAiOpPlayer2(type) { attack(it.target) }
        onAiApPlayer2(type) { attack(it.target) }
        onEvent<NpcStateEvents.Delete> { vialed.remove(npc.uid) }
    }

    fun hasThrownAt(npc: Npc, player: Player): Boolean = vialed[npc.uid]?.contains(player.uid) == true

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

        if (vialed.getOrPut(npc.uid) { HashSet() }.add(target.uid)) {
            throwVial(target)
            return
        }
        anim(RSCM.getReverseMapping(RSCMType.SEQ, npc.visType.param(params.attack_anim).id))
        val hit = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)
        val damage = if (hit) random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush)) else 0
        target.finishNpcHit(npc, MELEE_HIT_DELAY, HitType.Melee, damage, hitModifier)
    }

    private fun StandardNpcAccess.throwVial(target: Player) {
        npc.say(OVERHEAD)
        anim(THROW_SEQ)
        worldRepo.projAnim(npc, target, vialSpotanim, VIAL_PROJANIM)
        drain(target)
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
        const val MOURNER = "npc.mourning_overpass_mourner"
        const val OVERHEAD = "This will cut down you to size!"
        const val THROW_SEQ = "seq.human_throw"
        const val VIAL_TRAVEL = "spotanim.vial_travel"
        const val VIAL_PROJANIM = "projanim.thrown"
        const val MELEE_HIT_DELAY = 1
        const val DRAINED_LEVEL = 20

        val DRAINED_STATS =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.defence",
                "stat.ranged",
                "stat.magic",
                "stat.hitpoints",
                "stat.prayer",
            )

        /** Lowers [player]'s combat stats to [DRAINED_LEVEL]; never raises one or touches a base level. */
        fun drain(player: Player) {
            for (stat in DRAINED_STATS) {
                val excess = player.stat(stat) - DRAINED_LEVEL
                if (excess > 0) {
                    player.statSub(stat, excess, 0)
                }
            }
            player.mes("The Mourner throws a vial at you. You feel weakened.")
        }

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}
