package org.rsmod.content.quest.area.varrock.dragonslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The quest's reward is the right to wear rune platebodies (and the other bodies Oziach guards:
 * green dragonhide and dragon platebodies). Until it is done they cannot be worn.
 */
class DragonSlayerWearHook @Inject constructor() : PlayerRestrictionHook {
    private val restricted = RESTRICTED_BODIES.map { it.asRSCM(RSCMType.OBJ) }.toSet()

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id !in restricted) {
            return null
        }
        if (QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            return null
        }
        return "You need to have completed the Dragon Slayer quest to wear this."
    }

    private companion object {
        const val QUEST_KEY = "quest_dragonslayer1"

        val RESTRICTED_BODIES =
            listOf(
                "obj.rune_platebody",
                "obj.rune_platebody_gold",
                "obj.rune_platebody_trim",
                "obj.rune_platebody_zamorak",
                "obj.rune_platebody_saradomin",
                "obj.rune_platebody_guthix",
                "obj.rune_platebody_goldplate",
                "obj.rune_platebody_ancient",
                "obj.rune_platebody_armadyl",
                "obj.rune_platebody_bandos",
                "obj.rune_platebody_h1",
                "obj.rune_platebody_h2",
                "obj.rune_platebody_h3",
                "obj.rune_platebody_h4",
                "obj.rune_platebody_h5",
                "obj.dragonhide_body",
                "obj.dragonhide_body_trim",
                "obj.dragonhide_body_trim_gold",
                "obj.dragon_platebody",
                "obj.dragon_platebody_gold",
            )
    }
}

/**
 * Wormbrain only carries one map piece. Once the player has it (or has handed it to Ned) there
 * is nothing to gain from beating him up.
 */
class WormbrainAttackHook @Inject constructor(private val dragonSlayer: DragonSlayerQuest) : NpcAttackValidateHook {
    private val wormbrainId = "npc.wormbrain".asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != wormbrainId) {
            return NpcAttackValidateResult.Pass
        }
        val onQuest = dragonSlayer.stage(player) >= DragonSlayerQuest.STAGE_BRIEFED
        if (onQuest && dragonSlayer.hasMapPiece(player, DragonSlayerQuest.MAP_PART_LOZAR)) {
            return NpcAttackValidateResult.Deny(
                "You have already taken Wormbrain's map piece. There is no use in beating him up further.",
            )
        }
        return NpcAttackValidateResult.Pass
    }
}
