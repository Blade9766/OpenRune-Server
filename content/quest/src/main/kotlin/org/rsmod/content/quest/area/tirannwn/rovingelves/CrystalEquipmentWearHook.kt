package org.rsmod.content.quest.area.tirannwn.rovingelves

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

/**
 * The crystal bow and shield answer only to those who have done Roving Elves. Their Ranged or
 * Defence and Agility levels are item params, checked by the equipment rules as for any item;
 * receiving one (as the quest reward, say) is never gated on them.
 */
class CrystalEquipmentWearHook @Inject constructor() : PlayerRestrictionHook {
    private val crystalItems by lazy { CRYSTAL_ITEMS.map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id !in crystalItems) {
            return null
        }
        if (QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            return null
        }
        return "You need to have completed the Roving Elves quest to wield this."
    }

    companion object {
        val CRYSTAL_ITEMS =
            listOf(
                "obj.crystal_bow",
                "obj.crystal_bow_2500",
                "obj.crystal_bow_inactive",
                "obj.crystal_shield",
                "obj.crystal_shield_2500",
                "obj.crystal_shield_inactive",
            )
    }
}
