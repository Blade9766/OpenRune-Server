package org.rsmod.content.quest.manager

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.SilverlightAttackHook
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircle
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
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
        bindInstance<DemonSlayerQuest>()
        bindInstance<GertrudesCatQuest>()
        bindInstance<RestlessGhostQuest>()
        bindInstance<WitchsPotionQuest>()
        bindInstance<SheepShearerQuest>()
        bindInstance<LostCityQuest>()
        bindInstance<GoblinDiplomacyQuest>()
        bindInstance<PlagueCityQuest>()
        bindInstance<BiohazardQuest>()
        bindInstance<QuestDoors>()
        bindInstance<StoneCircle>()
        bindInstance<WallyVision>()
        bindInstance<MageArenaQuest>()
        bindInstance<MageArena2Quest>()
        bindInstance<KolodionFights>()
        bindInstance<FollowerSpawns>()
        addSetBinding<NpcAttackValidateHook>(SilverlightAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(KolodionAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(GodFollowerKillHook::class.java)
        addSetBinding<PlayerTeleportValidateHook>(MageArenaTeleBlockHook::class.java)
    }
}
