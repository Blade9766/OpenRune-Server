package org.rsmod.content.quest.manager

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.wilderness.magearena.FollowerSpawns
import org.rsmod.content.quest.area.wilderness.magearena.GodFollowerKillHook
import org.rsmod.content.quest.area.wilderness.magearena.KolodionAttackHook
import org.rsmod.content.quest.area.wilderness.magearena.KolodionFights
import org.rsmod.content.quest.area.wilderness.magearena.MageArena2Quest
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaTeleBlockHook
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<MageArenaQuest>()
        bindInstance<MageArena2Quest>()
        bindInstance<KolodionFights>()
        bindInstance<FollowerSpawns>()

        addSetBinding<NpcAttackValidateHook>(KolodionAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(GodFollowerKillHook::class.java)
        addSetBinding<PlayerTeleportValidateHook>(MageArenaTeleBlockHook::class.java)
    }
}
