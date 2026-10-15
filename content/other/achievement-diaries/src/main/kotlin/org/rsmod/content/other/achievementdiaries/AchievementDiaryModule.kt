package org.rsmod.content.other.achievementdiaries

import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.content.skills.runecrafting.essence.EssenceMineArrivalHook
import org.rsmod.plugin.module.PluginModule

class AchievementDiaryModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(DiaryKillHook::class.java)
        addSetBinding<EssenceMineArrivalHook>(DiaryEssenceMineHook::class.java)
    }
}
