package org.rsmod.content.other.barrows

import jakarta.inject.Inject
import org.rsmod.api.npc.heal
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The brothers' own set effects on the player they fight. Effects that the wiki says protection
 * prayers do not stop (Ahrim, Karil, Torag) trigger on a hit that landed before prayer reduces
 * it; Guthan only heals from damage that actually lands.
 */
class BarrowsSetEffectsScript
@Inject
constructor(
    private val spawns: BarrowsSpawns,
    private val npcList: NpcList,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerHitEvents.Modify> { beforePrayer(player, hit) }
        onEvent<PlayerHitEvents.Impact> {
            if (hit.isFromNpc && hit.damage > 0) {
                val npc = hit.resolveNpcSource(npcList) ?: return@onEvent
                if (spawns.brotherTypes[npc.id] == Brother.Guthan && random.of(4) == 0) {
                    player.spotanim(GUTHAN_EFFECT)
                    npc.heal(hit.damage)
                }
            }
        }
    }

    private fun beforePrayer(player: Player, hit: HitBuilder) {
        if (!hit.isFromNpc) {
            return
        }
        val npc = hit.sourceUid?.let { NpcUid(it).resolve(npcList) } ?: return
        when (spawns.brotherTypes[npc.id]) {
            Brother.Dharok -> wretchedStrength(npc, hit)
            Brother.Verac -> defiler(player, hit)
            Brother.Ahrim ->
                if (hit.damage > 0 && random.of(5) == 0) {
                    player.spotanim(AHRIM_EFFECT)
                    player.statSub(STRENGTH, AHRIM_STRENGTH_DRAIN, 0)
                }
            Brother.Karil ->
                if (hit.damage > 0 && random.of(4) == 0) {
                    player.spotanim(KARIL_EFFECT)
                    player.statSub(AGILITY, 0, KARIL_AGILITY_DRAIN_PERCENT)
                }
            Brother.Torag ->
                if (hit.damage > 0 && random.of(4) == 0) {
                    player.spotanim(TORAG_EFFECT)
                    player.runEnergy -= player.runEnergy * TORAG_ENERGY_DRAIN_PERCENT / 100
                    UpdateRun.energy(player, player.runEnergy)
                }
            Brother.Guthan,
            null -> Unit
        }
    }

    /** One percent more damage for every hitpoint Dharok is missing. */
    private fun wretchedStrength(npc: Npc, hit: HitBuilder) {
        val missing = (npc.baseHitpointsLvl - npc.hitpoints).coerceAtLeast(0)
        hit.damage = hit.damage * (100 + missing) / 100
    }

    /** A quarter of Verac's swings ignore armour and protection prayers alike. */
    private fun defiler(player: Player, hit: HitBuilder) {
        if (random.of(4) != 0) {
            return
        }
        val protecting = player.vars[PROTECT_FROM_MELEE] == 1
        hit.damage = random.of(0, if (protecting) VERAC_PRAYED_MAX_HIT else VERAC_MAX_HIT)
        hit.penetration = 100
        player.spotanim(VERAC_EFFECT)
    }

    private companion object {
        const val STRENGTH = "stat.strength"
        const val AGILITY = "stat.agility"
        const val PROTECT_FROM_MELEE = "varbit.prayer_protectfrommelee"

        const val AHRIM_STRENGTH_DRAIN = 5
        const val KARIL_AGILITY_DRAIN_PERCENT = 20
        const val TORAG_ENERGY_DRAIN_PERCENT = 20
        const val VERAC_MAX_HIT = 23
        const val VERAC_PRAYED_MAX_HIT = 15

        const val AHRIM_EFFECT = "spotanim.barrows_ahirm_blighted_aura"
        const val GUTHAN_EFFECT = "spotanim.barrows_guthan_effect"
        const val KARIL_EFFECT = "spotanim.barrows_karil_tainted_shot"
        const val TORAG_EFFECT = "spotanim.barrows_torag_effect"
        const val VERAC_EFFECT = "spotanim.barrows_verac_desolation"
    }
}
