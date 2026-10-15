package org.rsmod.content.other.achievementdiaries.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class AchievementDiaryPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(AchievementDiaryTiersTable.tiers(), AchievementDiaryTasksTable.tasks())
}
