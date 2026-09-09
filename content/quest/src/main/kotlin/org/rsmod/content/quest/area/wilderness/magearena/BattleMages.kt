package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The battle mages inside the arena. Each casts his god's spell (max hit 20, with its usual
 * side effect) rather than the melee swing the generic combat would give him, so the arena
 * fights the way players expect when they are training the spells.
 */
class BattleMages @Inject constructor(private val deps: BossDeps) : PluginScript() {

    private val godByMage: Map<Int, God> =
        mapOf(
            "npc.saradomin_mage".asRSCM(RSCMType.NPC) to God.SARADOMIN,
            "npc.guthix_mage".asRSCM(RSCMType.NPC) to God.GUTHIX,
            "npc.zamorak_mage".asRSCM(RSCMType.NPC) to God.ZAMORAK,
        )

    private val spec =
        boss("npc.saradomin_mage", "npc.guthix_mage", "npc.zamorak_mage") {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)
            val cast = ability("battlemage_cast", external(CAST_HANDLER))
            phase("arena") { weightedSelectorRandom { +random(cast, weight = 1) } }
        }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(CAST_HANDLER) { access, npc, target, _ -> cast(access, npc, target) }
        BossCombat.register(this, spec, deps)
    }

    private fun cast(access: StandardNpcAccess, npc: Npc, target: Player) {
        val god = godByMage[npc.id] ?: return
        access.anim(CAST_ANIM)
        deps.worldRepo.soundArea(npc, god.castSound, radius = SOUND_RADIUS)
        val hit = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
        val damage = if (hit) deps.random.of(0..MAX_HIT) else 0
        target.spotanim(god.impact, delay = IMPACT_CLIENT_DELAY, height = 0)
        target.finishNpcHit(npc, HIT_DELAY, HitType.Magic, damage, deps.playerHitModifier)
        if (damage > 0) {
            when (god) {
                God.SARADOMIN -> target.statSub("stat.prayer", constant = 1, percent = 0)
                God.GUTHIX -> target.statSub("stat.defence", constant = 0, percent = DRAIN_PERCENT)
                God.ZAMORAK -> target.statSub("stat.magic", constant = 0, percent = DRAIN_PERCENT)
            }
        }
    }

    private companion object {
        const val CAST_HANDLER = "battlemage.cast"
        const val CAST_ANIM = "seq.human_caststrike_staff"
        const val ATTACK_RATE = 4
        const val AGGRESSION_RADIUS = 8
        const val MAX_HIT = 20
        const val DRAIN_PERCENT = 5
        const val SOUND_RADIUS = 8
        const val IMPACT_CLIENT_DELAY = 30
        const val HIT_DELAY = 2
    }
}
