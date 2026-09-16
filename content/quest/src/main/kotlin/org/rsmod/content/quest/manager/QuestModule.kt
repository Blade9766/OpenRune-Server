package org.rsmod.content.quest.manager

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.draynor.vampyreslayer.CountDraynor
import org.rsmod.content.quest.area.draynor.vampyreslayer.GarlicAttackHook
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest
import org.rsmod.content.quest.area.gnomestronghold.gliders.GnomeGliders
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ApeAtollAggression
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ChapterCards
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.GreegreeAttackHook
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.GreegreeWearHook
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Hangar
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.JungleDemonFight
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Marim
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyBackpackTeleportHook
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs.NarnodeMonkeyMadness
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.content.quest.area.karamja.shilovillage.Nazastarool
import org.rsmod.content.quest.area.karamja.shilovillage.NazastaroolAttackHook
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloUndead
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageKillHook
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest
import org.rsmod.content.quest.area.mortmyre.naturespirit.GhastAttackHook
import org.rsmod.content.quest.area.mortmyre.naturespirit.GhastKillHook
import org.rsmod.content.quest.area.mortmyre.naturespirit.Ghasts
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritDrezel
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest
import org.rsmod.content.quest.area.mortmyre.naturespirit.SpiritSpawns
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilKillHook
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest
import org.rsmod.content.quest.area.paterdomus.priestinperil.TempleGuardianAttackHook
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.SilverlightAttackHook
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircle
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerWearHook
import org.rsmod.content.quest.area.varrock.dragonslayer.Voyage
import org.rsmod.content.quest.area.varrock.dragonslayer.WormbrainAttackHook
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.wilderness.magearena.FollowerSpawns
import org.rsmod.content.quest.area.wilderness.magearena.GodFollowerKillHook
import org.rsmod.content.quest.area.wilderness.magearena.KolodionAttackHook
import org.rsmod.content.quest.area.wilderness.magearena.KolodionFights
import org.rsmod.content.quest.area.wilderness.magearena.MageArena2Quest
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaTeleBlockHook
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.SecateursEnchantment
import org.rsmod.content.quest.area.zanaris.fairytale1.TanglefootAttackHook
import org.rsmod.content.quest.area.zanaris.fairytale1.TanglefootKillHook
import org.rsmod.content.quest.area.zanaris.fairytale1.TanglefootLair
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<SpadeDigging>()
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
        bindInstance<DragonSlayerQuest>()
        bindInstance<QuestInstances>()
        bindInstance<DoorPassage>()
        bindInstance<Voyage>()
        bindInstance<MonkeyMadnessQuest>()
        bindInstance<NarnodeMonkeyMadness>()
        bindInstance<ChapterCards>()
        bindInstance<Greegree>()
        bindInstance<Hangar>()
        bindInstance<Marim>()
        bindInstance<JungleDemonFight>()
        bindInstance<ApeAtollAggression>()
        bindInstance<GnomeGliders>()
        bindInstance<VampyreSlayerQuest>()
        bindInstance<CountDraynor>()
        bindInstance<PriestInPerilQuest>()
        bindInstance<PaterdomusDoors>()
        bindInstance<NatureSpiritQuest>()
        bindInstance<NatureSpiritDrezel>()
        bindInstance<SpiritSpawns>()
        bindInstance<Ghasts>()
        bindInstance<ShiloVillageQuest>()
        bindInstance<DruidicRitualQuest>()
        bindInstance<ShiloUndead>()
        bindInstance<Nazastarool>()
        bindInstance<Fairytale1Quest>()
        bindInstance<SecateursEnchantment>()
        bindInstance<TanglefootLair>()
        addSetBinding<NpcAttackValidateHook>(SilverlightAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(KolodionAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(WormbrainAttackHook::class.java)
        addSetBinding<PlayerRestrictionHook>(DragonSlayerWearHook::class.java)
        addSetBinding<NpcDeathKillHook>(GodFollowerKillHook::class.java)
        addSetBinding<PlayerTeleportValidateHook>(MageArenaTeleBlockHook::class.java)
        addSetBinding<NpcAttackValidateHook>(GreegreeAttackHook::class.java)
        addSetBinding<PlayerRestrictionHook>(GreegreeWearHook::class.java)
        addSetBinding<PlayerTeleportValidateHook>(MonkeyBackpackTeleportHook::class.java)
        addSetBinding<NpcAttackValidateHook>(GarlicAttackHook::class.java)
        addSetBinding<NpcAttackValidateHook>(TempleGuardianAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(PriestInPerilKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(GhastAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(GhastKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(NazastaroolAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(ShiloVillageKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(TanglefootAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(TanglefootKillHook::class.java)
    }
}
