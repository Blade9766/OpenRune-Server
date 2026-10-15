package org.rsmod.content.other.achievementdiaries

import jakarta.inject.Inject
import org.rsmod.api.achievementdiaries.AchievementDiaries
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.runecrafting.essence.EssenceMineArrivalHook
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter

class DiaryEssenceMineHook @Inject constructor(private val diaries: AchievementDiaries) :
    EssenceMineArrivalHook {
    override fun onArrival(access: ProtectedAccess, teleporter: EssenceMineTeleporter) {
        if (teleporter == EssenceMineTeleporter.Sedridor) {
            diaries.complete(access.player, "dbrow.diary_lumbridge_easy_sedridor")
        }
    }
}
