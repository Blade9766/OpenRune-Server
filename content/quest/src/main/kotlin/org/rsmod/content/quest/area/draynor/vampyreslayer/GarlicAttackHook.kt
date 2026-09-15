package org.rsmod.content.quest.area.draynor.vampyreslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.output.mes
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.GARLIC
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Carrying garlic weakens Count Draynor. The check runs on every attack attempt, and each stat is
 * only drained while it is back at full, so the effect returns as he recovers: -10 Attack (the
 * only drain that is announced), -10 Strength, -40 Defence and -10 Hitpoints.
 */
class GarlicAttackHook @Inject constructor() : NpcAttackValidateHook {
    private val countId = CountDraynor.COUNT.asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != countId || player.inv.count(GARLIC) == 0 || npc.hitpoints <= 0) {
            return NpcAttackValidateResult.Pass
        }
        if (npc.attackLvl >= npc.baseAttackLvl) {
            npc.attackLvl = (npc.attackLvl - ATTACK_DRAIN).coerceAtLeast(0)
            player.mes("The vampyre seems to be weakened by the garlic you're carrying.")
        }
        if (npc.strengthLvl >= npc.baseStrengthLvl) {
            npc.strengthLvl = (npc.strengthLvl - STRENGTH_DRAIN).coerceAtLeast(0)
        }
        if (npc.defenceLvl >= npc.baseDefenceLvl) {
            npc.defenceLvl = (npc.defenceLvl - DEFENCE_DRAIN).coerceAtLeast(0)
        }
        if (npc.hitpoints >= npc.baseHitpointsLvl) {
            npc.hitpoints = (npc.hitpoints - HITPOINTS_DRAIN).coerceAtLeast(1)
        }
        return NpcAttackValidateResult.Pass
    }

    private companion object {
        const val ATTACK_DRAIN = 10
        const val STRENGTH_DRAIN = 10
        const val DEFENCE_DRAIN = 40
        const val HITPOINTS_DRAIN = 10
    }
}
