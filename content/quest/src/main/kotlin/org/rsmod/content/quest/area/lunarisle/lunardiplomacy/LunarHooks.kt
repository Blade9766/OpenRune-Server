package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.LUNAR_STAFF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ENTER_DREAM
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_EQUIPMENT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.DreamChallenges
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.DreamWorld
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.MeFight
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The rules the Moon Clan's magic imposes. Their ceremonial clothing can't be worn until the
 * Oneiromancer hands it over for the dream (the staff once she has taken the first one), and
 * inside the dream only that clothing may be worn, no potions work and no teleport can wake the
 * player. Me is the dreamer's own, and dying in the dream ends it.
 */
class LunarHooks
@Inject
constructor(
    private val lunar: Provider<LunarDiplomacyQuest>,
    private val dream: Provider<DreamWorld>,
    private val fight: Provider<MeFight>,
    private val challenges: Provider<DreamChallenges>,
) : PlayerRestrictionHook, PlayerTeleportValidateHook, NpcAttackValidateHook, PlayerDeathCleanupHook {
    private val clothing by lazy { LunarPiece.entries.map { it.obj.asRSCM(RSCMType.OBJ) }.toSet() }
    private val staff by lazy { LUNAR_STAFF.asRSCM(RSCMType.OBJ) }
    private val reflections by lazy { listOf(MeFight.ME_MALE, MeFight.ME_FEMALE).map { it.asRSCM(RSCMType.NPC) }.toSet() }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        val dreaming = dream.get().isDreaming(player)
        return when (action) {
            is RestrictedAction.Equip -> equip(player, action.obj.id, action.obj.name, dreaming)
            RestrictedAction.Drink -> if (dreaming) "Potions have no effect in a dream." else null
            else -> null
        }
    }

    private fun equip(player: Player, obj: Int, name: String, dreaming: Boolean): String? {
        val stage = lunar.get().stage(player)
        val complete = lunar.get().isComplete(player)
        if (obj == staff && !complete && stage < STAGE_EQUIPMENT) {
            return "The Lunar staff won't answer to you until the Oneiromancer has seen it."
        }
        if (obj in clothing && !complete && stage < STAGE_ENTER_DREAM) {
            return "You need the Oneiromancer's blessing before you can wear the Moon Clan's clothing."
        }
        if (dreaming && obj != staff && obj !in clothing && !name.contains(BOOK, ignoreCase = true)) {
            return "You can't use that in a dream."
        }
        return null
    }

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (type == TeleportType.Exempt || !dream.get().isDreaming(player)) {
            return null
        }
        return "You can't teleport now, you need to wake up first!"
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in reflections || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("That isn't your reflection.")
    }

    override fun cleanup(player: Player) {
        fight.get().cleanup(player)
        challenges.get().clearScratch(player)
        dream.get().forget(player)
    }

    private companion object {
        const val BOOK = "book"
    }
}
