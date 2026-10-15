package org.rsmod.content.other.achievementdiaries.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object AchievementDiaryTasksTable {
    const val TIER = 0
    const val DESCRIPTION = 1
    const val VARP = 2
    const val BIT = 3

    fun tasks() =
        dbTable("dbtable.achievement_diary_tasks", serverOnly = true) {
            column("tier", TIER, VarType.DBROW)
            column("description", DESCRIPTION, VarType.STRING)
            column("varp", VARP, VarType.STRING)
            column("bit", BIT, VarType.INT)

            fun task(row: String, tier: String, varp: String, bit: Int, description: String) {
                row(row) {
                    columnRSCM(TIER, tier)
                    column(DESCRIPTION, description)
                    column(VARP, varp)
                    column(BIT, bit)
                }
            }

            fun lumbridgeEasy(row: String, bit: Int, description: String) =
                task(row, "dbrow.diary_lumbridge_easy", "varp.lumb_dray_achievement_diary", bit, description)

            lumbridgeEasy("dbrow.diary_lumbridge_easy_draynor_rooftop", 1, "Complete a lap of the Draynor Village Rooftop Course.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_cave_bug", 2, "Slay a Cave bug in the Lumbridge Swamp Caves.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_sedridor", 3, "Have Archmage Sedridor teleport you to the Rune essence mine.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_water_runes", 4, "Craft some water runes.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_hans", 5, "Learn your age from Hans in Lumbridge.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_pickpocket", 6, "Pickpocket a man or woman in Lumbridge.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_oak_logs", 7, "Chop and burn some oak logs in Lumbridge.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_sewer_zombie", 8, "Kill a zombie in the Draynor Sewers.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_anchovies", 9, "Catch some anchovies in Al Kharid.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_bread", 10, "Bake some bread on the Lumbridge castle kitchen range.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_iron_ore", 11, "Mine some iron ore at the Al Kharid mine.")
            lumbridgeEasy("dbrow.diary_lumbridge_easy_ham_hideout", 12, "Enter the H.A.M. Hideout.")
        }
}
