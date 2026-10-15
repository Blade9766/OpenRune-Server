package org.rsmod.content.other.achievementdiaries.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object AchievementDiaryTiersTable {
    const val REGION = 0
    const val TIER = 1
    const val NAME = 2
    const val TASKMASTER = 3
    const val TASK_COUNT = 4
    const val COUNT_VARBIT = 5
    const val COMPLETE_VARBIT = 6
    const val REWARD_VARBIT = 7
    const val STARTED_VARBIT = 8
    const val REWARD = 9
    const val LAMP = 10
    const val LAMP_XP = 11
    const val LAMP_LEVEL = 12

    private const val LUMBRIDGE = 6

    private const val EASY = 0
    private const val MEDIUM = 1
    private const val HARD = 2
    private const val ELITE = 3

    fun tiers() =
        dbTable("dbtable.achievement_diary_tiers", serverOnly = true) {
            column("region", REGION, VarType.INT)
            column("tier", TIER, VarType.INT)
            column("name", NAME, VarType.STRING)
            column("taskmaster", TASKMASTER, VarType.STRING)
            column("task_count", TASK_COUNT, VarType.INT)
            column("count_varbit", COUNT_VARBIT, VarType.STRING)
            column("complete_varbit", COMPLETE_VARBIT, VarType.STRING)
            column("reward_varbit", REWARD_VARBIT, VarType.STRING)
            column("started_varbit", STARTED_VARBIT, VarType.STRING)
            column("reward", REWARD, VarType.OBJ)
            column("lamp", LAMP, VarType.OBJ)
            column("lamp_xp", LAMP_XP, VarType.INT)
            column("lamp_level", LAMP_LEVEL, VarType.INT)

            fun tier(
                row: String,
                region: Int,
                tier: Int,
                name: String,
                taskmaster: String,
                taskCount: Int,
                prefix: String,
                reward: String,
            ) {
                val (countKey, rewardKey, completeKey) = TIER_KEYS[tier]
                row(row) {
                    column(REGION, region)
                    column(TIER, tier)
                    column(NAME, name)
                    column(TASKMASTER, taskmaster)
                    column(TASK_COUNT, taskCount)
                    column(COUNT_VARBIT, "varbit.${prefix}_${countKey}_count")
                    column(COMPLETE_VARBIT, "varbit.${prefix}_diary_${completeKey}_complete")
                    column(REWARD_VARBIT, "varbit.${prefix}_${rewardKey}_reward")
                    column(STARTED_VARBIT, "varbit.started_${prefix}_diary")
                    columnRSCM(REWARD, reward)
                    columnRSCM(LAMP, LAMPS[tier])
                    column(LAMP_XP, LAMP_XPS[tier])
                    column(LAMP_LEVEL, LAMP_LEVELS[tier])
                }
            }

            val lumbridge = "Lumbridge & Draynor"
            val hatius = "Hatius Cosaintus"
            tier("dbrow.diary_lumbridge_easy", LUMBRIDGE, EASY, lumbridge, hatius, 12, "lumbridge", "obj.lumbridge_ring_easy")
            tier("dbrow.diary_lumbridge_medium", LUMBRIDGE, MEDIUM, lumbridge, hatius, 12, "lumbridge", "obj.lumbridge_ring_medium")
            tier("dbrow.diary_lumbridge_hard", LUMBRIDGE, HARD, lumbridge, hatius, 11, "lumbridge", "obj.lumbridge_ring_hard")
            tier("dbrow.diary_lumbridge_elite", LUMBRIDGE, ELITE, lumbridge, hatius, 6, "lumbridge", "obj.lumbridge_ring_elite")
        }

    private val TIER_KEYS =
        listOf(
            Triple("easy", "easy", "easy"),
            Triple("med", "medium", "medium"),
            Triple("hard", "hard", "hard"),
            Triple("elite", "elite", "elite"),
        )

    private val LAMPS =
        listOf("obj.diary_easy_lamp", "obj.diary_med_lamp", "obj.diary_hard_lamp", "obj.diary_elite_lamp")

    private val LAMP_XPS = listOf(2_500, 7_500, 15_000, 50_000)

    private val LAMP_LEVELS = listOf(30, 40, 50, 70)
}
