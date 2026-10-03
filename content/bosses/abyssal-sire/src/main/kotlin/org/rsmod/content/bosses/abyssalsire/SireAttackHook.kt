package org.rsmod.content.bosses.abyssalsire

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.LUNG
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SIRE_FORMS
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SIRE_SLEEPING
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

/**
 * The Sire and its respiratory systems need 85 Slayer and an abyssal demon (or Abyssal Sire boss)
 * task from any master but Krystilia, and the Sire fights one player at a time.
 */
class SireAttackHook @Inject constructor(private val fights: SireFights, private val deps: BossDeps) :
    NpcAttackValidateHook {

    private val sireIds by lazy { SIRE_FORMS.map { it.asRSCM(RSCMType.NPC) }.toSet() }
    private val lungId by lazy { LUNG.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val sire = npc.type.id in sireIds
        if (!sire && npc.type.id != lungId) return NpcAttackValidateResult.Pass
        if (player.statBase(SLAYER) < SLAYER_LEVEL) return NpcAttackValidateResult.Deny(SLAYER_LEVEL_MESSAGE)
        if (!onTask(player)) return NpcAttackValidateResult.Deny(OFF_TASK_MESSAGE)
        if (sire) {
            val fight = fights.fightOf(npc)
            if (fight != null && fight.hero !== player && heroStillFighting(fight)) {
                return NpcAttackValidateResult.Deny(BUSY_MESSAGE)
            }
        }
        return NpcAttackValidateResult.Pass
    }

    private fun heroStillFighting(fight: SireFight): Boolean =
        fight.hero.isValidTarget() &&
            fight.chamber.contains(fight.hero.coords) &&
            deps.mapClock.cycle - fight.heroLastHitSire < CLAIM_TICKS

    companion object {
        const val SLAYER = "stat.slayer"
        const val SLAYER_LEVEL = 85
        const val CLAIM_TICKS = 33
        const val SLAYER_LEVEL_MESSAGE = "You need a higher Slayer level to know how to wound this monster."
        const val OFF_TASK_MESSAGE = "You can only fight the Abyssal Sire while on an abyssal demon or Abyssal Sire task."
        const val BUSY_MESSAGE = "Someone else is already fighting the Abyssal Sire."

        private val demonTask: Int? by lazy {
            dev.openrune.ServerCacheManager.getNpc(SIRE_SLEEPING.asRSCM(RSCMType.NPC))?.paramOrNull(BaseParams.slayer_task_id)
        }

        fun onTask(player: Player): Boolean {
            if (SlayerTaskManager.isOnWildernessSlayerTask(player)) return false
            val target = player.vars["varp.slayer_target"]
            if (target == 0) return false
            if (target == demonTask) return true
            val task = SlayerTaskRow.all().firstOrNull { it.id == target } ?: return false
            return "abyssal sire" in task.nameLowercase
        }
    }
}

class AbyssalSireModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(SireAttackHook::class.java)
    }
}
