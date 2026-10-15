package org.rsmod.api.achievementdiaries

import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.AchievementDiaryTasksRow
import org.rsmod.api.table.AchievementDiaryTiersRow
import org.rsmod.game.entity.Player

/**
 * Task state lives in Jagex's own vars: one bit per task in the region's diary varps, plus the
 * per-tier count, completion and reward varbits the client's journal list reads. The task and
 * tier rows in the cache say which var holds what, so a region goes live by adding its rows.
 */
@Singleton
public class AchievementDiaries {
    private val tiersByRegion by lazy {
        AchievementDiaryTiersRow.all().groupBy { it.region }.mapValues { (_, tiers) ->
            tiers.sortedBy { it.tier }
        }
    }

    private val tasksByTier by lazy {
        AchievementDiaryTasksRow.all().groupBy { it.tier.rowId }.mapValues { (_, tasks) ->
            tasks.sortedBy { it.bit }
        }
    }

    public fun tiers(region: Int): List<AchievementDiaryTiersRow> =
        tiersByRegion[region].orEmpty()

    public fun tasks(tier: AchievementDiaryTiersRow): List<AchievementDiaryTasksRow> =
        tasksByTier[tier.rowId].orEmpty()

    public fun isComplete(player: Player, task: String): Boolean =
        isComplete(player, AchievementDiaryTasksRow.getRow(task))

    public fun isComplete(player: Player, task: AchievementDiaryTasksRow): Boolean =
        player.vars[task.varp] and (1 shl task.bit) != 0

    public fun isTierComplete(player: Player, tier: AchievementDiaryTiersRow): Boolean =
        player.vars[tier.completeVarbit] != 0

    public fun isClaimed(player: Player, tier: AchievementDiaryTiersRow): Boolean =
        player.vars[tier.rewardVarbit] != 0

    public fun complete(player: Player, task: String): Boolean =
        complete(player, AchievementDiaryTasksRow.getRow(task))

    public fun complete(player: Player, task: AchievementDiaryTasksRow): Boolean {
        if (isComplete(player, task)) {
            return false
        }
        val tier = task.tier
        VarPlayerIntMapSetter.set(player, task.varp, player.vars[task.varp] or (1 shl task.bit))
        VarPlayerIntMapSetter.set(player, tier.startedVarbit, 1)
        val count = tasks(tier).count { isComplete(player, it) }
        VarPlayerIntMapSetter.set(player, tier.countVarbit, count)
        player.mes(
            "<col=$MESSAGE_COLOUR>Well done! You have completed ${article(tier)} " +
                "${tierLabel(tier).lowercase()} task in the ${tier.name} area. " +
                "Your Achievement Diary has been updated.</col>"
        )
        if (count >= tier.taskCount && !isTierComplete(player, tier)) {
            VarPlayerIntMapSetter.set(player, tier.completeVarbit, 1)
            player.mes(
                "<col=$MESSAGE_COLOUR>Congratulations! You have completed all of the " +
                    "${tierLabel(tier).lowercase()} tasks in the ${tier.name} area. " +
                    "Speak to ${tier.taskmaster} to claim your reward.</col>"
            )
        }
        return true
    }

    /** The tiers of [region] that are complete but not yet claimed, in tier order. */
    public fun unclaimed(player: Player, region: Int): List<AchievementDiaryTiersRow> =
        tiers(region).filter { isTierComplete(player, it) && !isClaimed(player, it) }

    /**
     * Hands out the reward and lamp of the lowest unclaimed tier of [region]. Both objs are
     * unstackable, so the free-space check up front guarantees both adds land.
     */
    public fun claimNext(access: ProtectedAccess, region: Int): DiaryClaim {
        val tier = unclaimed(access.player, region).firstOrNull() ?: return DiaryClaim.Nothing
        if (access.inv.freeSpace() < CLAIM_SLOTS) {
            return DiaryClaim.NeedSpace(CLAIM_SLOTS)
        }
        VarPlayerIntMapSetter.set(access.player, tier.rewardVarbit, 1)
        access.invAdd(access.inv, tier.reward.internalName)
        access.invAdd(access.inv, tier.lamp.internalName)
        return DiaryClaim.Claimed(tier)
    }

    /** The claimed tiers of [region] whose reward the player no longer has anywhere. */
    public fun lostRewards(access: ProtectedAccess, region: Int): List<AchievementDiaryTiersRow> =
        tiers(region).filter { tier ->
            isClaimed(access.player, tier) &&
                tier.reward !in access.inv &&
                tier.reward !in access.worn &&
                tier.reward !in access.bank
        }

    public fun tierLabel(tier: AchievementDiaryTiersRow): String = TIER_LABELS[tier.tier]

    private fun article(tier: AchievementDiaryTiersRow): String =
        if (tier.tier == TIER_EASY || tier.tier == TIER_ELITE) "an" else "a"

    private companion object {
        const val TIER_EASY = 0
        const val TIER_ELITE = 3
        const val CLAIM_SLOTS = 2
        const val MESSAGE_COLOUR = "dc143c"
        val TIER_LABELS = listOf("Easy", "Medium", "Hard", "Elite")
    }
}

public sealed interface DiaryClaim {
    public data object Nothing : DiaryClaim

    public data class NeedSpace(val slots: Int) : DiaryClaim

    public data class Claimed(val tier: AchievementDiaryTiersRow) : DiaryClaim
}
