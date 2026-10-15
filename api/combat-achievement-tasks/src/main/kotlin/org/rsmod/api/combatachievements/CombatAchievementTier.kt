package org.rsmod.api.combatachievements

public enum class CombatAchievementTier(
    public val level: Int,
    public val label: String,
    public val points: Int,
    public val threshold: Int,
    internal val tasksEnum: Int,
    public val statusVarbit: String,
    internal val completedVarbit: String,
    internal val thresholdVarbit: String,
    public val lampClaimedVarbit: String,
    public val lamp: String,
    public val hilt: String,
    public val lampXp: Int,
    public val lampMinLevel: Int,
) {
    Easy(
        level = 1,
        label = "Easy",
        points = 1,
        threshold = 41,
        tasksEnum = 3981,
        statusVarbit = "varbit.ca_tier_status_easy",
        completedVarbit = "varbit.ca_total_tasks_completed_easy",
        thresholdVarbit = "varbit.ca_threshold_easy",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_easy",
        lamp = "obj.ca_lamp_easy",
        hilt = "obj.ca_offhand_easy",
        lampXp = 5_000,
        lampMinLevel = 20,
    ),
    Medium(
        level = 2,
        label = "Medium",
        points = 2,
        threshold = 169,
        tasksEnum = 3982,
        statusVarbit = "varbit.ca_tier_status_medium",
        completedVarbit = "varbit.ca_total_tasks_completed_medium",
        thresholdVarbit = "varbit.ca_threshold_medium",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_medium",
        lamp = "obj.ca_lamp_medium",
        hilt = "obj.ca_offhand_medium",
        lampXp = 10_000,
        lampMinLevel = 30,
    ),
    Hard(
        level = 3,
        label = "Hard",
        points = 3,
        threshold = 436,
        tasksEnum = 3983,
        statusVarbit = "varbit.ca_tier_status_hard",
        completedVarbit = "varbit.ca_total_tasks_completed_hard",
        thresholdVarbit = "varbit.ca_threshold_hard",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_hard",
        lamp = "obj.ca_lamp_hard",
        hilt = "obj.ca_offhand_hard",
        lampXp = 15_000,
        lampMinLevel = 40,
    ),
    Elite(
        level = 4,
        label = "Elite",
        points = 4,
        threshold = 1100,
        tasksEnum = 3984,
        statusVarbit = "varbit.ca_tier_status_elite",
        completedVarbit = "varbit.ca_total_tasks_completed_elite",
        thresholdVarbit = "varbit.ca_threshold_elite",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_elite",
        lamp = "obj.ca_lamp_elite",
        hilt = "obj.ca_offhand_elite",
        lampXp = 25_000,
        lampMinLevel = 50,
    ),
    Master(
        level = 5,
        label = "Master",
        points = 5,
        threshold = 1965,
        tasksEnum = 3985,
        statusVarbit = "varbit.ca_tier_status_master",
        completedVarbit = "varbit.ca_total_tasks_completed_master",
        thresholdVarbit = "varbit.ca_threshold_master",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_master",
        lamp = "obj.ca_lamp_master",
        hilt = "obj.ca_offhand_master",
        lampXp = 35_000,
        lampMinLevel = 60,
    ),
    Grandmaster(
        level = 6,
        label = "Grandmaster",
        points = 6,
        threshold = 2697,
        tasksEnum = 3986,
        statusVarbit = "varbit.ca_tier_status_grandmaster",
        completedVarbit = "varbit.ca_total_tasks_completed_grandmaster",
        thresholdVarbit = "varbit.ca_threshold_grandmaster",
        lampClaimedVarbit = "varbit.ca_lamp_claimed_grandmaster",
        lamp = "obj.ca_lamp_grandmaster",
        hilt = "obj.ca_offhand_grandmaster",
        lampXp = 50_000,
        lampMinLevel = 70,
    );

    /** How the task-completed chat message names the tier: "an easy", "a medium", ... */
    public val article: String
        get() = if (this == Easy || this == Elite) "an" else "a"

    public companion object {
        public const val STATUS_LOCKED: Int = 0
        public const val STATUS_UNCLAIMED: Int = 1
        public const val STATUS_CLAIMED: Int = 2

        public fun forLevel(level: Int): CombatAchievementTier? = entries.firstOrNull { it.level == level }
    }
}
