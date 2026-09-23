package org.rsmod.content.quest.manager

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.undergroundpass.DollOfIban
import org.rsmod.content.quest.area.ardougne.undergroundpass.DwarvenCamp
import org.rsmod.content.quest.area.ardougne.undergroundpass.IbanTemple
import org.rsmod.content.quest.area.ardougne.undergroundpass.IbansIngredients
import org.rsmod.content.quest.area.ardougne.undergroundpass.IbansLair
import org.rsmod.content.quest.area.ardougne.undergroundpass.KalragKillHook
import org.rsmod.content.quest.area.ardougne.undergroundpass.KardiaTheWitch
import org.rsmod.content.quest.area.ardougne.undergroundpass.Koftik
import org.rsmod.content.quest.area.ardougne.undergroundpass.OrbsOfLight
import org.rsmod.content.quest.area.ardougne.undergroundpass.PaladinKillHook
import org.rsmod.content.quest.area.ardougne.undergroundpass.Paladins
import org.rsmod.content.quest.area.ardougne.undergroundpass.PassEntrance
import org.rsmod.content.quest.area.ardougne.undergroundpass.PassObstacles
import org.rsmod.content.quest.area.ardougne.undergroundpass.PrisonCells
import org.rsmod.content.quest.area.ardougne.undergroundpass.TheGrid
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest
import org.rsmod.content.quest.area.ardougne.undergroundpass.UnicornCave
import org.rsmod.content.quest.area.ardougne.undergroundpass.WellOfDoors
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HotFeatherTakeHook
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.AchiettiesDialogue
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.GripAttackHook
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.GripKillHook
import org.rsmod.content.quest.area.burthorpe.trollstronghold.DadAttackHook
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest
import org.rsmod.content.quest.area.desert.icthlarin.Ceremony
import org.rsmod.content.quest.area.desert.icthlarin.FlashbackTeleportHook
import org.rsmod.content.quest.area.desert.icthlarin.Flashbacks
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinCats
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.icthlarin.PyramidFightHooks
import org.rsmod.content.quest.area.desert.icthlarin.PyramidFights
import org.rsmod.content.quest.area.desert.icthlarin.TilePuzzle
import org.rsmod.content.quest.area.desert.shadowofthestorm.AgrithNaarFight
import org.rsmod.content.quest.area.desert.shadowofthestorm.AgrithNaarKillHook
import org.rsmod.content.quest.area.desert.shadowofthestorm.DemonThroneRoom
import org.rsmod.content.quest.area.desert.shadowofthestorm.Ritual
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.SotsItems
import org.rsmod.content.quest.area.desert.shadowofthestorm.TheChase
import org.rsmod.content.quest.area.desert.shadowofthestorm.UzerKilns
import org.rsmod.content.quest.area.desert.shadowofthestorm.npcs.CultMembers
import org.rsmod.content.quest.area.desert.shadowofthestorm.npcs.EvilDave
import org.rsmod.content.quest.area.desert.shadowofthestorm.npcs.FatherBadden
import org.rsmod.content.quest.area.desert.shadowofthestorm.npcs.FatherReen
import org.rsmod.content.quest.area.desert.shadowofthestorm.npcs.SotsGolem
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.npcs.MercenaryCaptainAttackHook
import org.rsmod.content.quest.area.desert.touristtrap.npcs.MercenaryCaptainKillHook
import org.rsmod.content.quest.area.draynor.porcineofinterest.NoticeBoard
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest
import org.rsmod.content.quest.area.draynor.porcineofinterest.SourhogCave
import org.rsmod.content.quest.area.draynor.porcineofinterest.SourhogCombat
import org.rsmod.content.quest.area.draynor.porcineofinterest.SourhogKillHook
import org.rsmod.content.quest.area.draynor.porcineofinterest.StrangeHole
import org.rsmod.content.quest.area.draynor.porcineofinterest.TrackingTrail
import org.rsmod.content.quest.area.draynor.porcineofinterest.npcs.Rosie
import org.rsmod.content.quest.area.draynor.porcineofinterest.npcs.Sarah
import org.rsmod.content.quest.area.draynor.porcineofinterest.npcs.Spria
import org.rsmod.content.quest.area.draynor.vampyreslayer.CountDraynor
import org.rsmod.content.quest.area.draynor.vampyreslayer.GarlicAttackHook
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyAttackHook
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyBirds
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyKillHook
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
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest
import org.rsmod.content.quest.area.karamja.junglepotion.TrufitusJunglePotion
import org.rsmod.content.quest.area.karamja.legendsquest.CarvedRockGemTakeHook
import org.rsmod.content.quest.area.karamja.legendsquest.HolyWaterWeapon
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsSupport
import org.rsmod.content.quest.area.karamja.legendsquest.Nezikchened
import org.rsmod.content.quest.area.karamja.legendsquest.NezikchenedAttackHook
import org.rsmod.content.quest.area.karamja.legendsquest.NezikchenedKillHook
import org.rsmod.content.quest.area.karamja.legendsquest.npcs.Gujuo
import org.rsmod.content.quest.area.karamja.legendsquest.npcs.Ungadulu
import org.rsmod.content.quest.area.karamja.shilovillage.Nazastarool
import org.rsmod.content.quest.area.karamja.shilovillage.NazastaroolAttackHook
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloUndead
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageKillHook
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarHooks
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.DreamChallenges
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.DreamWorld
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.MeFight
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
import org.rsmod.content.quest.area.rellekka.fremenniktrials.DraugenAttackHook
import org.rsmod.content.quest.area.rellekka.fremenniktrials.DraugenHunts
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.content.quest.area.rellekka.fremenniktrials.KoscheiFights
import org.rsmod.content.quest.area.rellekka.fremenniktrials.LonghallKegTakeHook
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantTrial
import org.rsmod.content.quest.area.rellekka.fremenniktrials.NavigatorsTrial
import org.rsmod.content.quest.area.rellekka.fremenniktrials.WarriorsTrialHooks
import org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs.RellekkaShops
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsExperimentHooks
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsExperiments
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.SilverlightAttackHook
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircle
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerWearHook
import org.rsmod.content.quest.area.varrock.dragonslayer.Voyage
import org.rsmod.content.quest.area.varrock.dragonslayer.WormbrainAttackHook
import org.rsmod.content.quest.area.varrock.familycrest.Chronozon
import org.rsmod.content.quest.area.varrock.familycrest.ChronozonKillHook
import org.rsmod.content.quest.area.varrock.familycrest.CrestParts
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest
import org.rsmod.content.quest.area.varrock.familycrest.GauntletEnchanting
import org.rsmod.content.quest.area.varrock.familycrest.PerfectGold
import org.rsmod.content.quest.area.varrock.familycrest.WitchavenDungeon
import org.rsmod.content.quest.area.varrock.familycrest.npcs.Avan
import org.rsmod.content.quest.area.varrock.familycrest.npcs.Caleb
import org.rsmod.content.quest.area.varrock.familycrest.npcs.Dimintheis
import org.rsmod.content.quest.area.varrock.familycrest.npcs.Johnathon
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.CryptScene
import org.rsmod.content.quest.area.varrock.romeojuliet.JulietScene
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietScenes
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.npcs.JonnyAttackHook
import org.rsmod.content.quest.area.varrock.shieldofarrav.npcs.WeaponsmasterKillHook
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
        bindInstance<JunglePotionQuest>()
        bindInstance<TrufitusJunglePotion>()
        bindInstance<ShiloVillageQuest>()
        bindInstance<DruidicRitualQuest>()
        bindInstance<ShiloUndead>()
        bindInstance<Nazastarool>()
        bindInstance<Fairytale1Quest>()
        bindInstance<SecateursEnchantment>()
        bindInstance<TanglefootLair>()
        bindInstance<PorcineOfInterestQuest>()
        bindInstance<NoticeBoard>()
        bindInstance<TrackingTrail>()
        bindInstance<StrangeHole>()
        bindInstance<SourhogCave>()
        bindInstance<SourhogCombat>()
        bindInstance<Sarah>()
        bindInstance<Rosie>()
        bindInstance<Spria>()
        bindInstance<TouristTrapQuest>()
        bindInstance<MiningCampSecurity>()
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
        addSetBinding<NpcDeathKillHook>(SourhogKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(MercenaryCaptainAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(MercenaryCaptainKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(DadAttackHook::class.java)
        bindInstance<FremennikTrialsQuest>()
        bindInstance<MerchantTrial>()
        bindInstance<RellekkaShops>()
        bindInstance<DraugenHunts>()
        addSetBinding<NpcAttackValidateHook>(DraugenAttackHook::class.java)
        bindInstance<KoscheiFights>()
        bindInstance<NavigatorsTrial>()
        addSetBinding<NpcAttackValidateHook>(WarriorsTrialHooks::class.java)
        addSetBinding<PlayerDeathHook>(WarriorsTrialHooks::class.java)
        addSetBinding<PlayerRespawnHook>(WarriorsTrialHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(WarriorsTrialHooks::class.java)
        addSetBinding<PlayerRestrictionHook>(WarriorsTrialHooks::class.java)
        addSetBinding<PlayerObjTakeValidateHook>(LonghallKegTakeHook::class.java)
        bindInstance<IcthlarinsLittleHelperQuest>()
        bindInstance<IcthlarinCats>()
        bindInstance<Flashbacks>()
        bindInstance<PyramidFights>()
        bindInstance<TilePuzzle>()
        bindInstance<Ceremony>()
        addSetBinding<NpcAttackValidateHook>(PyramidFightHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(PyramidFightHooks::class.java)
        addSetBinding<PlayerTeleportValidateHook>(FlashbackTeleportHook::class.java)
        bindInstance<WitchsHouseQuest>()
        bindInstance<WitchsExperiments>()
        addSetBinding<NpcAttackValidateHook>(WitchsExperimentHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(WitchsExperimentHooks::class.java)
        bindInstance<TheGolemQuest>()
        bindInstance<ShadowOfTheStormQuest>()
        bindInstance<DemonThroneRoom>()
        bindInstance<SotsItems>()
        bindInstance<UzerKilns>()
        bindInstance<Ritual>()
        bindInstance<TheChase>()
        bindInstance<AgrithNaarFight>()
        bindInstance<FatherReen>()
        bindInstance<FatherBadden>()
        bindInstance<EvilDave>()
        bindInstance<CultMembers>()
        bindInstance<SotsGolem>()
        addSetBinding<NpcDeathKillHook>(AgrithNaarKillHook::class.java)
        bindInstance<LunarDiplomacyQuest>()
        bindInstance<LunarTravel>()
        bindInstance<DreamWorld>()
        bindInstance<DreamChallenges>()
        bindInstance<MeFight>()
        addSetBinding<PlayerRestrictionHook>(LunarHooks::class.java)
        addSetBinding<PlayerTeleportValidateHook>(LunarHooks::class.java)
        addSetBinding<NpcAttackValidateHook>(LunarHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(LunarHooks::class.java)
        bindInstance<FamilyCrestQuest>()
        bindInstance<Dimintheis>()
        bindInstance<Caleb>()
        bindInstance<Avan>()
        bindInstance<Johnathon>()
        bindInstance<CrestParts>()
        bindInstance<GauntletEnchanting>()
        bindInstance<WitchavenDungeon>()
        bindInstance<PerfectGold>()
        bindInstance<Chronozon>()
        addSetBinding<NpcDeathKillHook>(ChronozonKillHook::class.java)
        bindInstance<BigChompyBirdHuntingQuest>()
        bindInstance<ChompyHunt>()
        bindInstance<ChompyBirds>()
        addSetBinding<NpcAttackValidateHook>(ChompyAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(ChompyKillHook::class.java)
        bindInstance<UndergroundPassQuest>()
        bindInstance<PassEntrance>()
        bindInstance<PassObstacles>()
        bindInstance<TheGrid>()
        bindInstance<OrbsOfLight>()
        bindInstance<PrisonCells>()
        bindInstance<UnicornCave>()
        bindInstance<Paladins>()
        bindInstance<WellOfDoors>()
        bindInstance<IbansLair>()
        bindInstance<DwarvenCamp>()
        bindInstance<KardiaTheWitch>()
        bindInstance<DollOfIban>()
        bindInstance<IbansIngredients>()
        bindInstance<IbanTemple>()
        bindInstance<Koftik>()
        addSetBinding<NpcDeathKillHook>(PaladinKillHook::class.java)
        addSetBinding<NpcDeathKillHook>(KalragKillHook::class.java)
        bindInstance<MerlinsCrystalQuest>()
        bindInstance<ShieldOfArravQuest>()
        addSetBinding<NpcAttackValidateHook>(JonnyAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(WeaponsmasterKillHook::class.java)
        bindInstance<RomeoJulietQuest>()
        bindInstance<RomeoJulietScenes>()
        bindInstance<JulietScene>()
        bindInstance<CryptScene>()
        bindInstance<HeroesQuest>()
        bindInstance<AchiettiesDialogue>()
        addSetBinding<NpcAttackValidateHook>(GripAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(GripKillHook::class.java)
        addSetBinding<PlayerObjTakeValidateHook>(HotFeatherTakeHook::class.java)
        bindInstance<LegendsQuest>()
        bindInstance<LegendsSupport>()
        bindInstance<Nezikchened>()
        bindInstance<Gujuo>()
        bindInstance<Ungadulu>()
        addSetBinding<NpcAttackValidateHook>(NezikchenedAttackHook::class.java)
        addSetBinding<NpcDeathKillHook>(NezikchenedKillHook::class.java)
        addSetBinding<PlayerObjTakeValidateHook>(CarvedRockGemTakeHook::class.java)
        addSetBinding<WeaponMap>(HolyWaterWeapon::class.java)
    }
}
