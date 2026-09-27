package org.rsmod.api.combatachievements

import jakarta.inject.Singleton
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Completion state lives entirely in the client's own vars: one bit per task id across the
 * `ca_task_completed_*` varps, with the per-tier and per-boss totals, the point total and the tier
 * statuses derived from those bits by [sync] so the interfaces always agree with them.
 */
@Singleton
public class CombatAchievements {

    public fun isComplete(player: Player, task: CombatAchievementTask): Boolean =
        isComplete(player, task.id)

    public fun isComplete(player: Player, id: Int): Boolean =
        player.vars[completedVarp(id)] and (1 shl (id % 32)) != 0

    public fun complete(player: Player, name: String): Boolean =
        complete(player, CombatAchievementTasks.named(name))

    public fun complete(player: Player, task: CombatAchievementTask): Boolean {
        if (isComplete(player, task)) {
            return false
        }
        val varp = completedVarp(task.id)
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] or (1 shl (task.id % 32)))
        announce(player, task)
        val unlocked = sync(player)
        for (tier in unlocked) {
            player.mes(
                "<col=ef1020>Congratulations! You have unlocked the ${tier.label} tier " +
                    "Combat Achievement rewards. Speak to Ghommal to claim them.</col>"
            )
        }
        return true
    }

    public fun points(player: Player): Int = player.vars["varbit.ca_points"]

    public fun isUnlocked(player: Player, tier: CombatAchievementTier): Boolean =
        player.vars[tier.statusVarbit] != CombatAchievementTier.STATUS_LOCKED

    public fun isClaimed(player: Player, tier: CombatAchievementTier): Boolean =
        player.vars[tier.statusVarbit] == CombatAchievementTier.STATUS_CLAIMED

    public fun markClaimed(player: Player, tier: CombatAchievementTier) {
        VarPlayerIntMapSetter.set(player, tier.statusVarbit, CombatAchievementTier.STATUS_CLAIMED)
    }

    /** Completes every kill count task whose boss kill count the player has already reached. */
    public fun checkKillcounts(player: Player) {
        for (task in CombatAchievementBosses.killcountTasks) {
            if (!isComplete(player, task.id) && player.vars[task.varp] >= task.count) {
                CombatAchievementTasks[task.id]?.let { complete(player, it) }
            }
        }
    }

    public fun onNamedKill(player: Player, npcName: String) {
        val name = npcName.lowercase()
        for ((id, taskName) in CombatAchievementBosses.namedKillTasks) {
            if (taskName == name && !isComplete(player, id)) {
                CombatAchievementTasks[id]?.let { complete(player, it) }
            }
        }
    }

    /**
     * Rebuilds the totals, point count, thresholds and tier statuses from the task bits.
     *
     * @return the tiers whose rewards became claimable during this call.
     */
    public fun sync(player: Player): List<CombatAchievementTier> {
        val tierCounts = IntArray(CombatAchievementTier.entries.size)
        val bossCounts = HashMap<Int, Int>()
        var points = 0
        for (task in CombatAchievementTasks.all) {
            if (!isComplete(player, task)) {
                continue
            }
            tierCounts[task.tier.ordinal]++
            points += task.tier.points
            if (task.boss != 0) {
                bossCounts.merge(task.boss, 1, Int::plus)
            }
        }
        setIfChanged(player, "varbit.ca_points", points)
        for ((boss, varbit) in CombatAchievementBosses.taskCountVarbits) {
            setIfChanged(player, varbit, bossCounts[boss] ?: 0)
        }
        val unlocked = mutableListOf<CombatAchievementTier>()
        for (tier in CombatAchievementTier.entries) {
            setIfChanged(player, tier.completedVarbit, tierCounts[tier.ordinal])
            setIfChanged(player, tier.thresholdVarbit, tier.threshold)
            val status = player.vars[tier.statusVarbit]
            if (status == CombatAchievementTier.STATUS_CLAIMED) {
                continue
            }
            val reached = points >= tier.threshold
            if (reached && status == CombatAchievementTier.STATUS_LOCKED) {
                unlocked += tier
            }
            val newStatus =
                if (reached) CombatAchievementTier.STATUS_UNCLAIMED
                else CombatAchievementTier.STATUS_LOCKED
            setIfChanged(player, tier.statusVarbit, newStatus)
        }
        return unlocked
    }

    private fun announce(player: Player, task: CombatAchievementTask) {
        val tier = task.tier
        val pointText = if (tier.points == 1) "1 point" else "${tier.points} points"
        player.mes(
            "Congratulations, you've completed ${tier.article} ${tier.label.lowercase()} combat " +
                "task: <col=06600c>${task.name}</col> ($pointText)."
        )
        player.midiJingle(COMPLETION_JINGLE)
        if (player.vars["varbit.ca_task_popup"] != 0) {
            ClientScripts.notificationDisplay(
                player,
                "Combat Task Completed!",
                "Task Completed: <col=ffffff>${task.name}</col><br><br>" +
                    "Tier: <col=ffffff>${tier.label}</col>",
            )
        }
    }

    private fun setIfChanged(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }

    private fun completedVarp(id: Int): String = "varp.ca_task_completed_${id / 32}"

    private companion object {
        const val COMPLETION_JINGLE = "jingle.grumpy"
    }
}
