package org.rsmod.api.mechanics.toxins.impl

import kotlin.math.min
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

/**
 * Poison on npcs, mirroring the player mechanic in `api/mechanics/toxins`: a severity that falls
 * by one every [TICK_INTERVAL] cycles, dealing [PlayerPoison.damageForSeverity] each time until it
 * runs out. The severity lives in `varn.npc_poison_severity`; the ticking is `timer.npc_poison`.
 */
public object NpcPoison {
    public const val TIMER: String = "timer.npc_poison"
    public const val TICK_INTERVAL: Int = PlayerPoison.TICK_INTERVAL

    private const val SEVERITY_VARN = "varn.npc_poison_severity"

    /** Poison hits carry no attacker and must not be modified by combat hooks. */
    private val noModifier = NpcHitModifier {}

    public fun isPoisoned(npc: Npc): Boolean = npc.vars[SEVERITY_VARN] > 0

    public fun isImmune(npc: Npc): Boolean =
        (npc.visType.paramOrNull(params.poison_immunity) ?: 0) > 0

    /**
     * Poisons [npc] with a poison whose first hit deals [initialDamage]. A stronger poison already
     * running is left alone, as it is for players; a weaker one is replaced.
     */
    public fun tryPoison(npc: Npc, initialDamage: Int): Boolean {
        if (initialDamage <= 0 || npc.hitpoints <= 0 || isImmune(npc)) {
            return false
        }
        val severity = PlayerPoison.severityForInitialDamage(initialDamage)
        if (npc.vars[SEVERITY_VARN] >= severity) {
            return false
        }
        queuePoisonHit(npc, initialDamage)
        npc.vars[SEVERITY_VARN] = severity - 1
        npc.timer(TIMER, TICK_INTERVAL)
        return true
    }

    /**
     * Poisons [npc] at [severity], for sources defined by severity rather than first-hit damage
     * (the Ancient smoke spells). Replaces a running poison only if it hits at least as hard.
     */
    public fun tryPoisonSeverity(npc: Npc, severity: Int): Boolean {
        if (severity <= 0 || npc.hitpoints <= 0 || isImmune(npc)) {
            return false
        }
        val current = npc.vars[SEVERITY_VARN]
        if (current > 0) {
            val currentDamage = PlayerPoison.damageForSeverity(current)
            val incomingDamage = PlayerPoison.damageForSeverity(severity)
            if (incomingDamage < currentDamage) return false
            if (incomingDamage == currentDamage && severity <= current) return false
        }
        queuePoisonHit(npc, PlayerPoison.damageForSeverity(severity))
        if (severity - 1 <= 0) {
            clear(npc)
            return true
        }
        npc.vars[SEVERITY_VARN] = severity - 1
        npc.timer(TIMER, TICK_INTERVAL)
        return true
    }

    public fun onTimerTick(npc: Npc) {
        var severity = npc.vars[SEVERITY_VARN]
        if (severity <= 0 || npc.hitpoints <= 0) {
            clear(npc)
            return
        }
        queuePoisonHit(npc, PlayerPoison.damageForSeverity(severity))
        severity--
        if (severity <= 0) {
            clear(npc)
            return
        }
        npc.vars[SEVERITY_VARN] = severity
        npc.timer(TIMER, TICK_INTERVAL)
    }

    public fun clear(npc: Npc) {
        npc.vars[SEVERITY_VARN] = 0
        npc.clearTimer(TIMER)
    }

    private fun queuePoisonHit(npc: Npc, damage: Int) {
        val capped = min(damage, npc.hitpoints)
        if (capped <= 0) {
            return
        }
        npc.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = capped,
            modifier = noModifier,
            hitmark = hitmark_groups.poison_damage,
        )
    }
}
