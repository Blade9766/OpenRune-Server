package org.rsmod.api.combatachievements

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.enums.enum

public data class CombatAchievementTask(
    public val id: Int,
    public val name: String,
    public val description: String,
    public val tier: CombatAchievementTier,
    public val type: Int,
    public val boss: Int,
) {
    public companion object {
        public const val TYPE_STAMINA: Int = 1
        public const val TYPE_PERFECTION: Int = 2
        public const val TYPE_KILLCOUNT: Int = 3
        public const val TYPE_MECHANICAL: Int = 4
        public const val TYPE_RESTRICTION: Int = 5
        public const val TYPE_SPEED: Int = 6
    }
}

/**
 * The task list exactly as the client draws it: one enum of task structs per tier, each struct
 * carrying the task id that indexes the `ca_task_completed_*` bitfield varps.
 */
public object CombatAchievementTasks {
    public val all: List<CombatAchievementTask> by lazy { load() }

    private val byId: Map<Int, CombatAchievementTask> by lazy { all.associateBy { it.id } }

    private val byName: Map<String, CombatAchievementTask> by lazy {
        all.associateBy { it.name.lowercase() }
    }

    public operator fun get(id: Int): CombatAchievementTask? = byId[id]

    public fun named(name: String): CombatAchievementTask =
        byName[name.lowercase()] ?: error("Unknown combat achievement task: $name")

    public fun forBoss(boss: Int): List<CombatAchievementTask> = all.filter { it.boss == boss }

    private fun load(): List<CombatAchievementTask> {
        val idParam = "param.ca_task_id".asRSCM()
        val nameParam = "param.ca_task_name".asRSCM()
        val descParam = "param.ca_task_desc".asRSCM()
        val typeParam = "param.ca_task_type".asRSCM()
        val bossParam = "param.ca_task_boss_id".asRSCM()
        val tasks = mutableListOf<CombatAchievementTask>()
        for (tier in CombatAchievementTier.entries) {
            val structs = enum<Int, Int>(tier.tasksEnum).backing.toSortedMap().values
            for (structId in structs.filterNotNull()) {
                val params = ServerCacheManager.getStruct(structId)?.params ?: continue
                tasks +=
                    CombatAchievementTask(
                        id = params[idParam] as Int,
                        name = params[nameParam] as String,
                        description = params[descParam] as String,
                        tier = tier,
                        type = params[typeParam] as? Int ?: 0,
                        boss = params[bossParam] as? Int ?: 0,
                    )
            }
        }
        return tasks
    }
}
