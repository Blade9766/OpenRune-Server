package org.rsmod.content.other.achievementdiaries

import jakarta.inject.Inject
import org.rsmod.api.achievementdiaries.AchievementDiaries
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.map.CoordGrid

class DiaryKillHook @Inject constructor(private val diaries: AchievementDiaries) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        when {
            npc.type.isType("npc.swamp_cave_bug") ->
                diaries.complete(context.hero, "dbrow.diary_lumbridge_easy_cave_bug")
            SEWER_ZOMBIES.any { npc.type.isType(it) } && npc.coords.inDraynorSewers() ->
                diaries.complete(context.hero, "dbrow.diary_lumbridge_easy_sewer_zombie")
        }
    }

    private fun CoordGrid.inDraynorSewers(): Boolean =
        level == 0 && x in DRAYNOR_SEWERS_X && z in DRAYNOR_SEWERS_Z

    private companion object {
        val DRAYNOR_SEWERS_X = 3080..3130
        val DRAYNOR_SEWERS_Z = 9640..9700

        val SEWER_ZOMBIES =
            listOf(
                "npc.zombie_armed_sewer1",
                "npc.zombie_armed_sewer2",
                "npc.zombie_armed_sewer3",
                "npc.zombie_armed_sewer4",
                "npc.zombie_unarmed_sewer1",
                "npc.zombie_unarmed_sewer2",
                "npc.zombie_unarmed_sewer3",
            )
    }
}
